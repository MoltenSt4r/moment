package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance Zero-Knowledge WebSocket Relay Client for Moment.
 * Completely independent of Google Firebase and Play Services.
 */
class BlindRelayRepository(context: Context? = null) {

    companion object {
        private const val TAG = "BlindRelayWs"
        // Dedicated relay endpoint on user's hardened server
        const val RELAY_URL = "ws://77.73.135.243:8765"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)

    // Current room state flow (emits when matched or peer leaves)
    private val roomStateFlow = MutableStateFlow<BlindRoomDoc?>(null)

    // Envelopes received from peer
    private val envelopesFlow = MutableSharedFlow<BlindEnvelopeDoc>(replay = 50)
    private val envelopeHistory = mutableListOf<BlindEnvelopeDoc>()

    // Current active room ID and user credentials
    private var currentRoomId: String? = null
    private var myNodeId: String = ""
    private var myPubKey: String = ""
    private var mySalt: String = ""

    init {
        connectWebSocket()
    }

    private fun connectWebSocket() {
        if (isConnected.get()) return

        val request = Request.Builder().url(RELAY_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected to $RELAY_URL")
                isConnected.set(true)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                isConnected.set(false)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}. Reconnecting in 3s...")
                isConnected.set(false)
                scope.launch {
                    delay(3000)
                    connectWebSocket()
                }
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "matched" -> {
                    val roomId = json.getString("roomId")
                    val role = json.getString("role")
                    val peerNodeId = json.getString("peerNodeId")
                    val peerPubKey = json.getString("peerPubKey")
                    val salt = json.getString("salt")

                    currentRoomId = roomId

                    val roomDoc = if (role == "host") {
                        BlindRoomDoc(
                            id = roomId,
                            hostUid = myNodeId,
                            guestUid = peerNodeId,
                            hostPubKey = myPubKey,
                            guestPubKey = peerPubKey,
                            ephemeralSalt = salt,
                            status = "active",
                            createdAt = RelayTimestamp(),
                            updatedAt = RelayTimestamp()
                        )
                    } else {
                        BlindRoomDoc(
                            id = roomId,
                            hostUid = peerNodeId,
                            guestUid = myNodeId,
                            hostPubKey = peerPubKey,
                            guestPubKey = myPubKey,
                            ephemeralSalt = salt,
                            status = "active",
                            createdAt = RelayTimestamp(),
                            updatedAt = RelayTimestamp()
                        )
                    }
                    roomStateFlow.value = roomDoc
                }

                "envelope" -> {
                    val roomId = json.optString("roomId")
                    val envJson = json.optJSONObject("envelope") ?: return
                    val env = BlindEnvelopeDoc(
                        id = "env_${UUID.randomUUID().toString().replace("-", "")}",
                        roomId = roomId,
                        senderUid = envJson.optString("senderUid"),
                        ratchetStep = envJson.optLong("ratchetStep"),
                        iv = envJson.optString("iv"),
                        ciphertext = envJson.optString("ciphertext"),
                        ttlSeconds = envJson.optLong("ttlSeconds"),
                        createdAt = RelayTimestamp()
                    )
                    synchronized(envelopeHistory) {
                        envelopeHistory.add(env)
                    }
                    scope.launch {
                        envelopesFlow.emit(env)
                    }
                }

                "peer_left" -> {
                    val roomId = json.optString("roomId", currentRoomId ?: "")
                    roomStateFlow.value = BlindRoomDoc(
                        id = roomId,
                        status = "shredded",
                        createdAt = RelayTimestamp(),
                        updatedAt = RelayTimestamp()
                    )
                }

                "queue_joined" -> {
                    Log.i(TAG, "Joined matchmaking queue on server")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing incoming message: $text", e)
        }
    }

    private fun sendJson(json: JSONObject) {
        val payload = json.toString()
        val ws = webSocket
        if (ws != null && isConnected.get()) {
            ws.send(payload)
        } else {
            connectWebSocket()
            scope.launch {
                delay(500)
                webSocket?.send(payload)
            }
        }
    }

    suspend fun findLiveWaitingRooms(
        ephemeralNodeId: String,
        excludedRoomIds: Set<String> = emptySet(),
        maxStaleMs: Long = 16_000L
    ): Result<List<BlindRoomDoc>> {
        // WebSocket server handles matchmaking centrally in-memory
        return Result.success(emptyList())
    }

    suspend fun createLiveWaitingRoom(
        ephemeralNodeId: String,
        hostPubKey: String,
        ephemeralSalt: String,
        customRoomId: String = "room_${UUID.randomUUID().toString().replace("-", "")}"
    ): Result<BlindRoomDoc> {
        myNodeId = ephemeralNodeId
        myPubKey = hostPubKey
        mySalt = ephemeralSalt
        currentRoomId = customRoomId

        synchronized(envelopeHistory) {
            envelopeHistory.clear()
        }

        val initialRoom = BlindRoomDoc(
            id = customRoomId,
            hostUid = ephemeralNodeId,
            guestUid = "",
            hostPubKey = hostPubKey,
            guestPubKey = "",
            ephemeralSalt = ephemeralSalt,
            status = "waiting"
        )
        roomStateFlow.value = initialRoom

        val joinMsg = JSONObject().apply {
            put("action", "join_queue")
            put("nodeId", ephemeralNodeId)
            put("pubKey", hostPubKey)
            put("salt", ephemeralSalt)
        }
        sendJson(joinMsg)

        return Result.success(initialRoom)
    }

    suspend fun heartbeatLiveWaitingRoom(roomId: String): Result<Unit> {
        val pingMsg = JSONObject().apply {
            put("action", "ping")
        }
        sendJson(pingMsg)
        return Result.success(Unit)
    }

    suspend fun joinLiveWaitingRoom(
        roomId: String,
        guestNodeId: String,
        guestPubKey: String
    ): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun scrubActiveRoomPublicKeys(roomId: String): Result<Unit> {
        return Result.success(Unit)
    }

    fun observeLiveRoom(roomId: String): Flow<BlindRoomDoc?> {
        return roomStateFlow.asStateFlow()
    }

    suspend fun sendLiveEncryptedEnvelope(
        roomId: String,
        senderNodeId: String,
        participantIds: List<String>,
        ratchetStep: Int,
        ivBase64: String,
        ciphertextBase64: String,
        ttlSeconds: Int,
        envelopeId: String = "env_${UUID.randomUUID().toString().replace("-", "")}"
    ): Result<String> {
        val envObj = JSONObject().apply {
            put("roomId", roomId)
            put("senderUid", senderNodeId)
            put("ratchetStep", ratchetStep)
            put("iv", ivBase64)
            put("ciphertext", ciphertextBase64)
            put("ttlSeconds", ttlSeconds)
        }

        val msg = JSONObject().apply {
            put("action", "send_envelope")
            put("roomId", roomId)
            put("envelope", envObj)
        }
        sendJson(msg)
        return Result.success(envelopeId)
    }

    fun observeLiveRoomEnvelopes(roomId: String): Flow<List<BlindEnvelopeDoc>> {
        return envelopesFlow.asSharedFlow().map {
            synchronized(envelopeHistory) {
                envelopeHistory.toList()
            }
        }
    }

    suspend fun deleteLiveEnvelope(roomId: String, envelopeId: String): Result<Unit> {
        synchronized(envelopeHistory) {
            envelopeHistory.removeAll { it.id == envelopeId }
        }
        return Result.success(Unit)
    }

    suspend fun shredLiveRoomAndEnvelopes(roomId: String): Result<Unit> {
        val leaveMsg = JSONObject().apply {
            put("action", "leave_room")
            put("roomId", roomId)
        }
        sendJson(leaveMsg)

        synchronized(envelopeHistory) {
            envelopeHistory.clear()
        }
        roomStateFlow.value = BlindRoomDoc(id = roomId, status = "shredded")
        currentRoomId = null
        return Result.success(Unit)
    }
}
