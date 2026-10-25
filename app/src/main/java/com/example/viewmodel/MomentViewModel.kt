package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.HandshakeVerification
import com.example.crypto.PayloadKind
import com.example.crypto.PlainEnvelopePayload
import com.example.crypto.SealedCipherPacket
import com.example.crypto.ZeroKnowledgeSessionCrypto
import com.example.data.BlindEnvelopeDoc
import com.example.data.BlindRelayRepository
import com.example.data.BlindRoomDoc
import com.example.data.MomentMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

enum class SessionPhase {
    IDLE,
    SEARCHING,
    HANDSHAKING,
    ACTIVE_CHAT
}

data class ReplyDraft(
    val messageId: String,
    val previewText: String,
    val isFromMe: Boolean
)

data class MomentUiState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val currentRoomId: String? = null,
    val isPeerConnected: Boolean = false,
    val handshakeVerification: HandshakeVerification? = null,
    val messages: List<MomentMessage> = emptyList(),
    val replyDraft: ReplyDraft? = null,
    val defaultTtlSeconds: Int = 30,
    val isPeerTyping: Boolean = false,
    val nowEpochMs: Long = System.currentTimeMillis(),
    val statusBannerText: String? = null,
    val shreddedCount: Int = 0
)

class MomentViewModel(
    private val repository: BlindRelayRepository,
    initialUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MomentUiState())
    val uiState: StateFlow<MomentUiState> = _uiState.asStateFlow()

    // Volatile RAM-only ECDH P-256 + 512B-padded AES-256-GCM engine
    private val localCrypto = ZeroKnowledgeSessionCrypto()
    private val jitterRandom = SecureRandom()

    // Rotated on every single matchmaking search so no two chats ever share an identifier
    private var activeSessionNodeToken: String = initialUserId
    private var activePeerUid: String = ""

    private val ratchetCounter = AtomicInteger(0)
    private val processedEnvelopeIds = mutableSetOf<String>()
    private val envelopeDocIdByMessageId = mutableMapOf<String, String>()
    private val disintegratingMessageIds = mutableSetOf<String>()
    private val deadRoomIds = mutableSetOf<String>()

    private var matchmakingJob: Job? = null
    private var roomObserverJob: Job? = null
    private var envelopesObserverJob: Job? = null
    private var ttlTickerJob: Job? = null
    private var typingResetJob: Job? = null
    private var lastTypingSentAtMs: Long = 0L

    init {
        startDisappearingMessagesTicker()
    }

    fun setDefaultTtlSeconds(seconds: Int) {
        _uiState.update { it.copy(defaultTtlSeconds = seconds) }
    }

    fun setReplyDraft(message: MomentMessage?) {
        _uiState.update {
            it.copy(
                replyDraft = message?.let { msg ->
                    ReplyDraft(
                        messageId = msg.id,
                        previewText = msg.text.take(90),
                        isFromMe = msg.isMine
                    )
                }
            )
        }
    }

    /**
     * Real Zero-Knowledge E2EE matchmaking over Firestore /zk_relay.
     *
     * Never drops the user into an empty chat:
     * - Rotates the user's one-time routing token (unlinkable across sessions).
     * - Stays in SEARCHING / HANDSHAKING until a live peer completes the mutual ECDH handshake.
     * - Automatically purges dead/stale ghost rooms and resolves simultaneous dual-host searches.
     */
    fun startMatchmaking() {
        shredActiveSessionInternal(deleteRemote = true)

        // Generate a brand-new one-time unlinkable node token for this search
        activeSessionNodeToken = localCrypto.generateUnlinkableNodeToken()

        _uiState.update {
            it.copy(
                phase = SessionPhase.SEARCHING,
                currentRoomId = null,
                isPeerConnected = false,
                messages = emptyList(),
                replyDraft = null,
                handshakeVerification = null,
                isPeerTyping = false,
                statusBannerText = null
            )
        }

        matchmakingJob = viewModelScope.launch {
            while (isActive && _uiState.value.phase == SessionPhase.SEARCHING) {
                // 1. Look for an existing fresh waiting room from a live peer
                val waitingResult = repository.findLiveWaitingRooms(
                    ephemeralNodeId = activeSessionNodeToken,
                    excludedRoomIds = deadRoomIds
                )
                val candidateRoom = waitingResult.getOrNull()?.firstOrNull()

                if (candidateRoom != null) {
                    val connected = attemptGuestJoinAndVerifyLiveness(candidateRoom)
                    if (connected) {
                        return@launch
                    }
                    // Candidate room was claimed by someone else or its host went offline; retry
                    _uiState.update {
                        if (it.phase != SessionPhase.IDLE) {
                            it.copy(phase = SessionPhase.SEARCHING, currentRoomId = null)
                        } else it
                    }
                }

                if (!isActive || _uiState.value.phase != SessionPhase.SEARCHING) return@launch

                // 2. Host our own waiting room and wait for a real partner (with live heartbeats & tie-breaking)
                val connectedAsHost = hostWaitingRoomUntilMatchedOrPreempted()
                if (connectedAsHost) {
                    return@launch
                }
            }
        }
    }

    /**
     * Attempts to atomically join [candidateRoom] as Guest, derive the ECDH root key,
     * send an encrypted HANDSHAKE_SYN, and verify that the Host responds with an encrypted
     * packet within 4.5 seconds before entering ACTIVE_CHAT.
     */
    private suspend fun attemptGuestJoinAndVerifyLiveness(candidateRoom: BlindRoomDoc): Boolean {
        val roomId = candidateRoom.id
        val guestIdentity = localCrypto.initializeEphemeralIdentity(candidateRoom.ephemeralSalt)

        val joinRes = repository.joinLiveWaitingRoom(
            roomId = roomId,
            guestNodeId = activeSessionNodeToken,
            guestPubKey = guestIdentity.publicKeyBase64
        )
        if (joinRes.isFailure) {
            deadRoomIds.add(roomId)
            localCrypto.shred()
            return false
        }

        val verification = localCrypto.completeHandshake(
            peerPublicKeyBase64 = candidateRoom.hostPubKey,
            saltBase64 = candidateRoom.ephemeralSalt
        )
        activePeerUid = candidateRoom.hostUid

        _uiState.update {
            it.copy(
                phase = SessionPhase.HANDSHAKING,
                currentRoomId = roomId,
                handshakeVerification = verification
            )
        }

        val hostAckDeferred = CompletableDeferred<Boolean>()
        val handshakeCollectorJob = viewModelScope.launch {
            repository.observeLiveRoomEnvelopes(roomId)
                .catch { e -> Log.w(TAG, "Handshake envelope observer error", e) }
                .collect { envelopes ->
                    for (env in envelopes) {
                        if (env.senderUid == activeSessionNodeToken) continue
                        val decrypted = localCrypto.decryptPayload(
                            SealedCipherPacket(
                                ratchetStep = env.ratchetStep.toInt(),
                                ivBase64 = env.iv,
                                ciphertextBase64 = env.ciphertext
                            )
                        )
                        if (decrypted != null) {
                            if (decrypted.kind == PayloadKind.HANDSHAKE_ACK ||
                                decrypted.kind == PayloadKind.HANDSHAKE_SYN
                            ) {
                                processedEnvelopeIds.add(env.id)
                                viewModelScope.launch { repository.deleteLiveEnvelope(roomId, env.id) }
                            }
                            hostAckDeferred.complete(true)
                        }
                    }
                }
        }

        // Send encrypted 512B-padded HANDSHAKE_SYN to wake/confirm with Host
        sendControlEnvelope(roomId, PayloadKind.HANDSHAKE_SYN)

        val hostConfirmed = withTimeoutOrNull(4_500L) {
            hostAckDeferred.await()
        } ?: false

        handshakeCollectorJob.cancel()

        if (!hostConfirmed) {
            // Host room was a ghost room (host closed app right before join); shred it and keep searching
            deadRoomIds.add(roomId)
            localCrypto.shred()
            activePeerUid = ""
            viewModelScope.launch { repository.shredLiveRoomAndEnvelopes(roomId) }
            return false
        }

        // Scrub public keys from Firestore now that both sides hold the shared secret in RAM
        viewModelScope.launch { repository.scrubActiveRoomPublicKeys(roomId) }

        enterActiveChat(
            roomId = roomId,
            peerUid = candidateRoom.hostUid,
            verification = verification
        )
        observeActiveRoomLifecycle(roomId)
        return true
    }

    /**
     * Creates a waiting room on `/zk_relay` and waits in `SessionPhase.SEARCHING`.
     * - Immediately transitions to `ACTIVE_CHAT` when a Guest joins and completes ECDH.
     * - Sends a heartbeat every ~3.2s so other peers know this waiting room is live.
     * - Checks for simultaneous waiting rooms from other peers and breaks ties deterministically
     *   (`myRoom.id > otherRoom.id`) so two simultaneous searchers always pair up within seconds.
     */
    private suspend fun hostWaitingRoomUntilMatchedOrPreempted(): Boolean {
        val hostIdentity = localCrypto.initializeEphemeralIdentity()
        val createRes = repository.createLiveWaitingRoom(
            ephemeralNodeId = activeSessionNodeToken,
            hostPubKey = hostIdentity.publicKeyBase64,
            ephemeralSalt = hostIdentity.ephemeralSaltBase64
        )
        val createdRoom = createRes.getOrNull() ?: run {
            delay(1_500L)
            return false
        }

        val myRoomId = createdRoom.id
        _uiState.update { it.copy(currentRoomId = myRoomId) }

        val matchOutcome = CompletableDeferred<Boolean>()

        val hostRoomListenerJob = viewModelScope.launch {
            repository.observeLiveRoom(myRoomId)
                .catch { e -> Log.w(TAG, "Host waiting room observer error", e) }
                .collect { roomSnap ->
                    if (roomSnap == null || roomSnap.status == "shredded") {
                        if (!matchOutcome.isCompleted) matchOutcome.complete(false)
                        return@collect
                    }
                    if (roomSnap.status == "active" &&
                        roomSnap.guestUid.isNotBlank() &&
                        roomSnap.guestPubKey.isNotBlank()
                    ) {
                        activePeerUid = roomSnap.guestUid
                        val verification = localCrypto.completeHandshake(
                            peerPublicKeyBase64 = roomSnap.guestPubKey,
                            saltBase64 = roomSnap.ephemeralSalt
                        )
                        _uiState.update {
                            it.copy(
                                phase = SessionPhase.HANDSHAKING,
                                handshakeVerification = verification
                            )
                        }
                        // Confirm liveness to the Guest with an encrypted 512B-padded HANDSHAKE_ACK
                        sendControlEnvelope(myRoomId, PayloadKind.HANDSHAKE_ACK)

                        enterActiveChat(
                            roomId = myRoomId,
                            peerUid = roomSnap.guestUid,
                            verification = verification
                        )
                        observeActiveRoomLifecycle(myRoomId)
                        if (!matchOutcome.isCompleted) matchOutcome.complete(true)
                    }
                }
        }

        val heartbeatAndTieBreakerJob = viewModelScope.launch {
            while (isActive && !matchOutcome.isCompleted) {
                val jitterMs = 2_800L + (jitterRandom.nextInt(900)).toLong()
                delay(jitterMs)
                if (matchOutcome.isCompleted || _uiState.value.phase != SessionPhase.SEARCHING) break

                // 1. Refresh our waiting room timestamp so peers know we are online right now
                repository.heartbeatLiveWaitingRoom(myRoomId)

                // 2. Check if another live user also created a waiting room simultaneously
                val otherWaiting = repository.findLiveWaitingRooms(
                    ephemeralNodeId = activeSessionNodeToken,
                    excludedRoomIds = deadRoomIds + myRoomId
                ).getOrNull().orEmpty()

                val betterCandidate = otherWaiting.firstOrNull { other ->
                    // Deterministic tie-breaker: the room with the larger ID yields and joins the smaller ID
                    myRoomId > other.id
                }
                if (betterCandidate != null && !matchOutcome.isCompleted) {
                    // Shred our waiting room and switch to joining the other peer's waiting room
                    repository.shredLiveRoomAndEnvelopes(myRoomId)
                    matchOutcome.complete(false)
                    break
                }
            }
        }

        val matched = try {
            matchOutcome.await()
        } finally {
            hostRoomListenerJob.cancel()
            heartbeatAndTieBreakerJob.cancel()
        }
        return matched
    }

    private suspend fun sendControlEnvelope(roomId: String, kind: PayloadKind) {
        if (!localCrypto.isHandshakeReady()) return
        val step = ratchetCounter.incrementAndGet()
        val payload = PlainEnvelopePayload(
            kind = kind,
            messageId = "ctrl_${UUID.randomUUID()}"
        )
        val sealed = localCrypto.encryptPayload(payload, step)
        val participants = listOf(activeSessionNodeToken, activePeerUid).filter { it.isNotBlank() }
        val envId = "env_${UUID.randomUUID().toString().replace("-", "")}"
        processedEnvelopeIds.add(envId)
        repository.sendLiveEncryptedEnvelope(
            roomId = roomId,
            senderNodeId = activeSessionNodeToken,
            participantIds = participants,
            ratchetStep = sealed.ratchetStep,
            ivBase64 = sealed.ivBase64,
            ciphertextBase64 = sealed.ciphertextBase64,
            ttlSeconds = 15,
            envelopeId = envId
        )
    }

    private fun observeActiveRoomLifecycle(roomId: String) {
        roomObserverJob?.cancel()
        roomObserverJob = viewModelScope.launch {
            repository.observeLiveRoom(roomId)
                .catch { e -> Log.w(TAG, "Active room observer error", e) }
                .collect { roomSnap ->
                    if (roomSnap == null || roomSnap.status == "shredded") {
                        if (_uiState.value.phase == SessionPhase.ACTIVE_CHAT) {
                            shredActiveSessionInternal(deleteRemote = false)
                            _uiState.update {
                                it.copy(
                                    phase = SessionPhase.IDLE,
                                    currentRoomId = null,
                                    isPeerConnected = false,
                                    messages = emptyList(),
                                    statusBannerText = "Собеседник покинул чат • Ключи уничтожены"
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun enterActiveChat(
        roomId: String,
        peerUid: String,
        verification: HandshakeVerification
    ) {
        activePeerUid = peerUid
        _uiState.update {
            it.copy(
                phase = SessionPhase.ACTIVE_CHAT,
                currentRoomId = roomId,
                isPeerConnected = true,
                handshakeVerification = verification,
                statusBannerText = null
            )
        }

        envelopesObserverJob?.cancel()
        envelopesObserverJob = viewModelScope.launch {
            repository.observeLiveRoomEnvelopes(roomId)
                .catch { e -> Log.w(TAG, "Envelope stream error", e) }
                .collect { envelopes ->
                    processIncomingEnvelopes(envelopes)
                }
        }
    }

    fun onUserTypingChanged(currentText: String) {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        if (state.phase != SessionPhase.ACTIVE_CHAT || !state.isPeerConnected || !localCrypto.isHandshakeReady()) return

        val now = System.currentTimeMillis()
        val isTypingNow = currentText.isNotBlank()
        if (isTypingNow && now - lastTypingSentAtMs < 2_500L) return
        lastTypingSentAtMs = now

        val step = ratchetCounter.incrementAndGet()
        val payload = PlainEnvelopePayload(
            kind = PayloadKind.TYPING,
            messageId = "typ_${UUID.randomUUID()}",
            isTyping = isTypingNow
        )
        viewModelScope.launch {
            val sealed = localCrypto.encryptPayload(payload, step)
            val participants = listOf(activeSessionNodeToken, activePeerUid).filter { it.isNotBlank() }
            val envId = "env_${UUID.randomUUID().toString().replace("-", "")}"
            processedEnvelopeIds.add(envId)
            repository.sendLiveEncryptedEnvelope(
                roomId = roomId,
                senderNodeId = activeSessionNodeToken,
                participantIds = participants,
                ratchetStep = sealed.ratchetStep,
                ivBase64 = sealed.ivBase64,
                ciphertextBase64 = sealed.ciphertextBase64,
                ttlSeconds = 15,
                envelopeId = envId
            )
            // Delete transient typing envelope shortly after so relay stays clean
            delay(2_000L)
            repository.deleteLiveEnvelope(roomId, envId)
        }
    }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        if (text.isEmpty() ||
            state.phase != SessionPhase.ACTIVE_CHAT ||
            !state.isPeerConnected ||
            !localCrypto.isHandshakeReady()
        ) return

        val replyDraft = state.replyDraft
        val ttl = state.defaultTtlSeconds
        val msgId = "msg_${UUID.randomUUID()}"
        val step = ratchetCounter.incrementAndGet()
        val now = System.currentTimeMillis()
        val expiresMs = if (ttl > 0) now + (ttl * 1000L) else null

        val plainPayload = PlainEnvelopePayload(
            kind = PayloadKind.MESSAGE,
            messageId = msgId,
            text = text,
            replyToId = replyDraft?.messageId,
            replyPreview = replyDraft?.previewText,
            sentAtMs = now
        )

        val localMsg = MomentMessage(
            id = msgId,
            senderUid = activeSessionNodeToken,
            isMine = true,
            text = text,
            replyToId = replyDraft?.messageId,
            replyPreview = replyDraft?.previewText,
            ttlSeconds = ttl,
            createdAtMs = now,
            expiresAtMs = expiresMs,
            ratchetStep = step
        )
        _uiState.update { st ->
            st.copy(
                replyDraft = null,
                messages = (st.messages + localMsg).sortedBy { it.createdAtMs }
            )
        }

        viewModelScope.launch {
            val sealed = localCrypto.encryptPayload(plainPayload, step)
            val participants = listOf(activeSessionNodeToken, activePeerUid).filter { it.isNotBlank() }
            val envId = "env_${UUID.randomUUID().toString().replace("-", "")}"
            processedEnvelopeIds.add(envId)
            envelopeDocIdByMessageId[msgId] = envId
            repository.sendLiveEncryptedEnvelope(
                roomId = roomId,
                senderNodeId = activeSessionNodeToken,
                participantIds = participants,
                ratchetStep = sealed.ratchetStep,
                ivBase64 = sealed.ivBase64,
                ciphertextBase64 = sealed.ciphertextBase64,
                ttlSeconds = ttl,
                envelopeId = envId
            )
        }
    }

    fun toggleReaction(targetMessageId: String, emoji: String) {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        if (state.phase != SessionPhase.ACTIVE_CHAT ||
            !state.isPeerConnected ||
            !localCrypto.isHandshakeReady()
        ) return

        applyReactionToRam(targetMessageId, emoji, activeSessionNodeToken)

        val step = ratchetCounter.incrementAndGet()
        val payload = PlainEnvelopePayload(
            kind = PayloadKind.REACTION,
            messageId = "react_${UUID.randomUUID()}",
            reactionEmoji = emoji,
            targetMessageId = targetMessageId
        )

        viewModelScope.launch {
            val sealed = localCrypto.encryptPayload(payload, step)
            val participants = listOf(activeSessionNodeToken, activePeerUid).filter { it.isNotBlank() }
            val envId = "env_${UUID.randomUUID().toString().replace("-", "")}"
            processedEnvelopeIds.add(envId)
            repository.sendLiveEncryptedEnvelope(
                roomId = roomId,
                senderNodeId = activeSessionNodeToken,
                participantIds = participants,
                ratchetStep = sealed.ratchetStep,
                ivBase64 = sealed.ivBase64,
                ciphertextBase64 = sealed.ciphertextBase64,
                ttlSeconds = 0,
                envelopeId = envId
            )
        }
    }

    private fun applyReactionToRam(targetId: String, emoji: String, senderUid: String) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id != targetId) {
                    msg
                } else {
                    val currentReactors = msg.reactions[emoji].orEmpty()
                    val nextReactors = if (currentReactors.contains(senderUid)) {
                        currentReactors - senderUid
                    } else {
                        currentReactors + senderUid
                    }
                    val nextMap = msg.reactions.toMutableMap()
                    if (nextReactors.isEmpty()) {
                        nextMap.remove(emoji)
                    } else {
                        nextMap[emoji] = nextReactors
                    }
                    msg.copy(reactions = nextMap)
                }
            }
            state.copy(messages = updated)
        }
    }

    private fun processIncomingEnvelopes(envelopes: List<BlindEnvelopeDoc>) {
        if (!localCrypto.isHandshakeReady()) return
        val now = System.currentTimeMillis()
        val roomId = _uiState.value.currentRoomId

        for (env in envelopes) {
            if (env.id.isBlank() || processedEnvelopeIds.contains(env.id)) continue
            processedEnvelopeIds.add(env.id)

            val step = env.ratchetStep.toInt()
            if (step > ratchetCounter.get()) {
                ratchetCounter.set(step)
            }

            val decrypted = localCrypto.decryptPayload(
                SealedCipherPacket(
                    ratchetStep = step,
                    ivBase64 = env.iv,
                    ciphertextBase64 = env.ciphertext
                )
            ) ?: continue

            when (decrypted.kind) {
                PayloadKind.HANDSHAKE_SYN, PayloadKind.HANDSHAKE_ACK -> {
                    if (!roomId.isNullOrBlank()) {
                        viewModelScope.launch { repository.deleteLiveEnvelope(roomId, env.id) }
                    }
                }

                PayloadKind.MESSAGE -> {
                    val ttlSec = env.ttlSeconds.toInt()
                    val createdMs = env.createdAt?.toDate()?.time ?: decrypted.sentAtMs.takeIf { it > 0 } ?: now
                    val expiresMs = if (ttlSec > 0) createdMs + (ttlSec * 1000L) else null
                    if (expiresMs != null && expiresMs <= now) {
                        if (!roomId.isNullOrBlank()) {
                            viewModelScope.launch { repository.deleteLiveEnvelope(roomId, env.id) }
                        }
                        continue
                    }

                    envelopeDocIdByMessageId[decrypted.messageId] = env.id
                    val newMsg = MomentMessage(
                        id = decrypted.messageId,
                        senderUid = env.senderUid,
                        isMine = env.senderUid == activeSessionNodeToken,
                        text = decrypted.text,
                        replyToId = decrypted.replyToId,
                        replyPreview = decrypted.replyPreview,
                        ttlSeconds = ttlSec,
                        createdAtMs = createdMs,
                        expiresAtMs = expiresMs,
                        ratchetStep = step
                    )
                    _uiState.update { state ->
                        if (state.messages.any { it.id == newMsg.id }) state
                        else state.copy(
                            isPeerTyping = false,
                            messages = (state.messages + newMsg).sortedBy { it.createdAtMs }
                        )
                    }
                }

                PayloadKind.REACTION -> {
                    val targetId = decrypted.targetMessageId ?: continue
                    val emoji = decrypted.reactionEmoji ?: continue
                    applyReactionToRam(targetId, emoji, env.senderUid)
                }

                PayloadKind.TYPING -> {
                    if (env.senderUid != activeSessionNodeToken) {
                        _uiState.update { it.copy(isPeerTyping = decrypted.isTyping) }
                        typingResetJob?.cancel()
                        if (decrypted.isTyping) {
                            typingResetJob = viewModelScope.launch {
                                delay(3_500L)
                                _uiState.update { it.copy(isPeerTyping = false) }
                            }
                        }
                    }
                    if (!roomId.isNullOrBlank()) {
                        viewModelScope.launch { repository.deleteLiveEnvelope(roomId, env.id) }
                    }
                }
            }
        }
    }

    fun switchToNewPartner() {
        startMatchmaking()
    }

    fun exitToMainMenu() {
        shredActiveSessionInternal(deleteRemote = true)
        activeSessionNodeToken = localCrypto.generateUnlinkableNodeToken()
        _uiState.update {
            it.copy(
                phase = SessionPhase.IDLE,
                currentRoomId = null,
                isPeerConnected = false,
                messages = emptyList(),
                replyDraft = null,
                handshakeVerification = null,
                isPeerTyping = false,
                statusBannerText = null,
                shreddedCount = it.shreddedCount + 1
            )
        }
    }

    private fun shredActiveSessionInternal(deleteRemote: Boolean) {
        matchmakingJob?.cancel()
        roomObserverJob?.cancel()
        envelopesObserverJob?.cancel()
        typingResetJob?.cancel()

        val roomToShred = _uiState.value.currentRoomId
        localCrypto.shred()
        activePeerUid = ""
        ratchetCounter.set(0)
        processedEnvelopeIds.clear()
        envelopeDocIdByMessageId.clear()
        disintegratingMessageIds.clear()

        if (deleteRemote && !roomToShred.isNullOrBlank() && !roomToShred.startsWith("local_")) {
            viewModelScope.launch {
                repository.shredLiveRoomAndEnvelopes(roomToShred)
            }
        }
    }

    private fun startDisappearingMessagesTicker() {
        ttlTickerJob?.cancel()
        ttlTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(200L)
                val now = System.currentTimeMillis()
                val currentState = _uiState.value
                if (currentState.phase != SessionPhase.ACTIVE_CHAT) continue

                val newlyExpired = currentState.messages.filter { msg ->
                    msg.expiresAtMs != null &&
                        msg.expiresAtMs <= now &&
                        !msg.isDisintegrating &&
                        !disintegratingMessageIds.contains(msg.id)
                }

                if (newlyExpired.isNotEmpty()) {
                    val triggerIds = newlyExpired.map { it.id }.toSet()
                    disintegratingMessageIds.addAll(triggerIds)

                    _uiState.update { state ->
                        state.copy(
                            nowEpochMs = now,
                            messages = state.messages.map { msg ->
                                if (msg.id in triggerIds) msg.copy(isDisintegrating = true) else msg
                            }
                        )
                    }

                    val roomId = currentState.currentRoomId
                    launch {
                        delay(750L)
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.filterNot { it.id in triggerIds },
                                shreddedCount = state.shreddedCount + triggerIds.size
                            )
                        }
                        disintegratingMessageIds.removeAll(triggerIds)
                        if (!roomId.isNullOrBlank() && !roomId.startsWith("local_")) {
                            for (expId in triggerIds) {
                                val envDocId = envelopeDocIdByMessageId.remove(expId)
                                if (!envDocId.isNullOrBlank()) {
                                    launch { repository.deleteLiveEnvelope(roomId, envDocId) }
                                }
                            }
                        }
                    }
                } else if (currentState.messages.any { it.expiresAtMs != null }) {
                    _uiState.update { it.copy(nowEpochMs = now) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        shredActiveSessionInternal(deleteRemote = true)
        ttlTickerJob?.cancel()
    }

    private companion object {
        const val TAG = "MomentViewModel"
    }
}
