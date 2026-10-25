package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Blind Relay Repository for Moment.
 *
 * Uses the provisioned named Firestore database exclusively.
 * Transmits only public ECDH keys and 512B-padded AES-256-GCM ciphertexts.
 */
class BlindRelayRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing /rooms.")
    }

    // --- Accountless Zero-Knowledge Relay (/zk_relay) ---

    /**
     * Finds live waiting rooms that are actively heartbeating (fresh within [maxStaleMs])
     * and opportunistically purges abandoned/stale ghost rooms so users never join an empty dead room.
     */
    suspend fun findLiveWaitingRooms(
        ephemeralNodeId: String,
        excludedRoomIds: Set<String> = emptySet(),
        maxStaleMs: Long = 16_000L
    ): Result<List<BlindRoomDoc>> {
        val path = "zk_relay"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("status", "waiting")
                .limit(20)
                .get()
                .await()
            val nowMs = System.currentTimeMillis()
            val allDocs = snapshot.toObjects(
                BlindRoomDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

            val freshRooms = mutableListOf<BlindRoomDoc>()
            for (room in allDocs) {
                if (room.id.isBlank()) continue
                val lastActiveMs = room.updatedAt?.toDate()?.time
                    ?: room.createdAt?.toDate()?.time
                    ?: nowMs
                val ageMs = nowMs - lastActiveMs

                if (ageMs > maxStaleMs) {
                    // Purge abandoned ghost waiting room
                    if (room.hostUid != ephemeralNodeId) {
                        runCatching {
                            db.collection(path).document(room.id).delete().await()
                        }
                    }
                    continue
                }

                if (room.hostUid != ephemeralNodeId &&
                    room.guestUid.isEmpty() &&
                    room.hostPubKey.isNotBlank() &&
                    room.id !in excludedRoomIds
                ) {
                    freshRooms.add(room)
                }
            }
            // Sort deterministically by room ID so tie-breaking is consistent across peers
            Result.success(freshRooms.sortedBy { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun createLiveWaitingRoom(
        ephemeralNodeId: String,
        hostPubKey: String,
        ephemeralSalt: String,
        customRoomId: String = "room_${UUID.randomUUID().toString().replace("-", "")}"
    ): Result<BlindRoomDoc> {
        val docRef = db.collection("zk_relay").document(customRoomId)
        val payload = mapOf(
            "hostUid" to ephemeralNodeId,
            "guestUid" to "",
            "hostPubKey" to hostPubKey,
            "guestPubKey" to "",
            "ephemeralSalt" to ephemeralSalt,
            "status" to "waiting",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(
                BlindRoomDoc(
                    id = customRoomId,
                    hostUid = ephemeralNodeId,
                    guestUid = "",
                    hostPubKey = hostPubKey,
                    guestPubKey = "",
                    ephemeralSalt = ephemeralSalt,
                    status = "waiting"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Updates the waiting room timestamp while the host is actively waiting in SEARCHING.
     */
    suspend fun heartbeatLiveWaitingRoom(roomId: String): Result<Unit> {
        val docRef = db.collection("zk_relay").document(roomId)
        return try {
            docRef.update("updatedAt", FieldValue.serverTimestamp()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atomically claims a waiting room using a Firestore Transaction so two guests cannot collide
     * and a guest never joins an already-shredded or already-claimed room.
     */
    suspend fun joinLiveWaitingRoom(
        roomId: String,
        guestNodeId: String,
        guestPubKey: String
    ): Result<Unit> {
        val docRef = db.collection("zk_relay").document(roomId)
        return try {
            db.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (!snap.exists()) {
                    throw IllegalStateException("Waiting room no longer exists")
                }
                val status = snap.getString("status") ?: ""
                val existingGuest = snap.getString("guestUid") ?: ""
                val hostPub = snap.getString("hostPubKey") ?: ""
                if (status != "waiting" || existingGuest.isNotEmpty() || hostPub.isBlank()) {
                    throw IllegalStateException("Waiting room is no longer available")
                }
                transaction.update(
                    docRef,
                    mapOf(
                        "guestUid" to guestNodeId,
                        "guestPubKey" to guestPubKey,
                        "status" to "active",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Scrubs the ephemeral ECDH public keys from the relay room document once both peers have
     * derived the shared secret in RAM, minimizing metadata exposure on the relay server.
     */
    suspend fun scrubActiveRoomPublicKeys(roomId: String): Result<Unit> {
        val docRef = db.collection("zk_relay").document(roomId)
        return try {
            docRef.update(
                mapOf(
                    "hostPubKey" to "",
                    "guestPubKey" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure(Exception("Key scrub skipped"))
        }
    }

    fun observeLiveRoom(roomId: String): Flow<BlindRoomDoc?> {
        val docRef = db.collection("zk_relay").document(roomId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    BlindRoomDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
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
        val docRef = db.collection("zk_relay").document(roomId)
            .collection("envelopes").document(envelopeId)
        val payload = mapOf(
            "roomId" to roomId,
            "senderUid" to senderNodeId,
            "participantUids" to participantIds.distinct().take(2).ifEmpty { listOf(senderNodeId) },
            "ratchetStep" to ratchetStep.toLong(),
            "iv" to ivBase64,
            "ciphertext" to ciphertextBase64,
            "ttlSeconds" to ttlSeconds.toLong(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(envelopeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeLiveRoomEnvelopes(roomId: String): Flow<List<BlindEnvelopeDoc>> {
        val colRef = db.collection("zk_relay").document(roomId).collection("envelopes")
        return colRef
            .whereEqualTo("roomId", roomId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    BlindEnvelopeDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.ratchetStep }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun deleteLiveEnvelope(roomId: String, envelopeId: String): Result<Unit> {
        val docRef = db.collection("zk_relay").document(roomId)
            .collection("envelopes").document(envelopeId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun shredLiveRoomAndEnvelopes(roomId: String): Result<Unit> {
        val roomRef = db.collection("zk_relay").document(roomId)
        return try {
            val envelopesCol = roomRef.collection("envelopes")
            val envSnap = envelopesCol
                .whereEqualTo("roomId", roomId)
                .get()
                .await()
            for (doc in envSnap.documents) {
                runCatching { doc.reference.delete().await() }
            }
            runCatching {
                roomRef.update(
                    mapOf(
                        "status" to "shredded",
                        "hostPubKey" to "",
                        "guestPubKey" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            runCatching { roomRef.delete().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, roomRef.path)
            Result.failure(e)
        }
    }

    // --- Authenticated Rules Test Collection (/rooms) ---

    suspend fun findWaitingRooms(currentUserId: String): Result<List<BlindRoomDoc>> {
        val path = "rooms"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("status", "waiting")
                .limit(10)
                .get()
                .await()
            val rooms = snapshot.toObjects(
                BlindRoomDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { it.hostUid != currentUserId && it.guestUid.isEmpty() }
            Result.success(rooms)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun createWaitingRoom(
        hostPubKey: String,
        ephemeralSalt: String,
        customRoomId: String = "room_${UUID.randomUUID().toString().replace("-", "")}"
    ): Result<BlindRoomDoc> {
        val uid = requireUserId()
        val docRef = db.collection("rooms").document(customRoomId)
        val payload = mapOf(
            "hostUid" to uid,
            "guestUid" to "",
            "hostPubKey" to hostPubKey,
            "guestPubKey" to "",
            "ephemeralSalt" to ephemeralSalt,
            "status" to "waiting",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(
                BlindRoomDoc(
                    id = customRoomId,
                    hostUid = uid,
                    guestUid = "",
                    hostPubKey = hostPubKey,
                    guestPubKey = "",
                    ephemeralSalt = ephemeralSalt,
                    status = "waiting"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinWaitingRoom(
        roomId: String,
        guestUid: String,
        guestPubKey: String
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("rooms").document(roomId)
        val updates = mapOf(
            "guestUid" to guestUid,
            "guestPubKey" to guestPubKey,
            "status" to "active",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getRoomById(roomId: String): Result<BlindRoomDoc?> {
        val docRef = db.collection("rooms").document(roomId)
        return try {
            val snap = docRef.get().await()
            val room = snap.toObject(
                BlindRoomDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(room)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeRoom(roomId: String): Flow<BlindRoomDoc?> {
        val docRef = db.collection("rooms").document(roomId)
        return docRef.snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    BlindRoomDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun sendEncryptedEnvelope(
        roomId: String,
        senderUid: String,
        participantUids: List<String>,
        ratchetStep: Int,
        ivBase64: String,
        ciphertextBase64: String,
        ttlSeconds: Int,
        envelopeId: String = "env_${UUID.randomUUID().toString().replace("-", "")}"
    ): Result<String> {
        requireUserId()
        val docRef = db.collection("rooms").document(roomId)
            .collection("envelopes").document(envelopeId)
        val payload = mapOf(
            "roomId" to roomId,
            "senderUid" to senderUid,
            "participantUids" to participantUids.distinct().take(2),
            "ratchetStep" to ratchetStep.toLong(),
            "iv" to ivBase64,
            "ciphertext" to ciphertextBase64,
            "ttlSeconds" to ttlSeconds.toLong(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(envelopeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeRoomEnvelopes(roomId: String, currentUserId: String): Flow<List<BlindEnvelopeDoc>> {
        val colRef = db.collection("rooms").document(roomId).collection("envelopes")
        return colRef
            .whereArrayContains("participantUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    BlindEnvelopeDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.ratchetStep }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun deleteEnvelope(roomId: String, envelopeId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("rooms").document(roomId)
            .collection("envelopes").document(envelopeId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun shredRoomAndEnvelopes(roomId: String, currentUserId: String): Result<Unit> {
        requireUserId()
        val roomRef = db.collection("rooms").document(roomId)
        return try {
            val envelopesCol = roomRef.collection("envelopes")
            val envSnap = envelopesCol
                .whereArrayContains("participantUids", currentUserId)
                .get()
                .await()
            for (doc in envSnap.documents) {
                runCatching { doc.reference.delete().await() }
            }
            runCatching {
                roomRef.update(
                    mapOf(
                        "status" to "shredded",
                        "hostPubKey" to "",
                        "guestPubKey" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            runCatching { roomRef.delete().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, roomRef.path)
            Result.failure(e)
        }
    }
}
