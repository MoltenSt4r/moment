package com.example.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

data class RelayTimestamp(val time: Long = System.currentTimeMillis()) {
    fun toDate(): Date = Date(time)
}

data class BlindRoomDoc(
    val id: String = "",
    val hostUid: String = "",
    val guestUid: String = "",
    val hostPubKey: String = "",
    val guestPubKey: String = "",
    val ephemeralSalt: String = "",
    val status: String = "waiting",
    val createdAt: RelayTimestamp? = RelayTimestamp(),
    val updatedAt: RelayTimestamp? = RelayTimestamp()
)

data class BlindEnvelopeDoc(
    val id: String = "",
    val roomId: String = "",
    val senderUid: String = "",
    val participantUids: List<String> = emptyList(),
    val ratchetStep: Long = 0L,
    val iv: String = "",
    val ciphertext: String = "",
    val ttlSeconds: Long = 0L,
    val createdAt: RelayTimestamp? = RelayTimestamp()
)

/**
 * Decrypted chat message stored strictly in volatile RAM.
 */
data class MomentMessage(
    val id: String,
    val senderUid: String,
    val isMine: Boolean,
    val text: String,
    val replyToId: String? = null,
    val replyPreview: String? = null,
    val reactions: Map<String, Set<String>> = emptyMap(),
    val ttlSeconds: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val expiresAtMs: Long? = null,
    val ratchetStep: Int = 0,
    val isDisintegrating: Boolean = false
)
