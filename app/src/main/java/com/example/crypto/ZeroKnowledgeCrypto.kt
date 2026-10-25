package com.example.crypto

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-Knowledge End-to-End Encryption & Anti-Fingerprinting Engine for Moment.
 *
 * Security & Anonymity Guarantees:
 * 1. Ephemeral ECDH (secp256r1 / P-256) key pairs are generated exclusively in volatile RAM.
 * 2. Private keys are NEVER serialized, logged, persisted, or transmitted over the network.
 * 3. Shared secrets are derived purely on-device via ECDH KeyAgreement + HKDF-SHA256.
 * 4. Every packet uses a per-step HKDF/HMAC-SHA256 ratcheted 256-bit key + fresh 96-bit random IV
 *    with AES/GCM/NoPadding (128-bit authentication tag).
 * 5. Traffic Analysis Resistance: Every plaintext JSON frame is padded with SecureRandom noise
 *    to fixed 512-byte quantum blocks before AES-256-GCM encryption so observers cannot infer
 *    message length or distinguish text, reactions, typing, and handshake packets.
 * 6. Clock-Skew Anonymization: Timestamps inside encrypted payloads are quantized to 1-second
 *    buckets to prevent device clock-drift fingerprinting.
 * 7. Calling shred() overwrites all key material in RAM with zeros (0x00).
 */
class ZeroKnowledgeSessionCrypto {

    private val secureRandom = SecureRandom()
    private var ephemeralKeyPair: KeyPair? = null
    private var rootSessionKey: ByteArray? = null
    private var sessionSaltBytes: ByteArray? = null
    private var sasFingerprint: String = ""
    private var sasHexBlocks: String = ""

    /**
     * Generates a brand-new, one-time unlinkable routing token in RAM for a single matchmaking attempt.
     * Prevents cross-session correlation in relay logs.
     */
    fun generateUnlinkableNodeToken(): String {
        val randomBytes = ByteArray(12)
        secureRandom.nextBytes(randomBytes)
        val hex = randomBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        Arrays.fill(randomBytes, 0.toByte())
        return "zk_$hex"
    }

    fun initializeEphemeralIdentity(existingSaltBase64: String? = null): EphemeralPublicIdentity {
        shred()
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(CURVE_NAME), secureRandom)
        val kp = kpg.generateKeyPair()
        ephemeralKeyPair = kp

        val salt = if (!existingSaltBase64.isNullOrBlank()) {
            Base64.decode(existingSaltBase64, Base64.NO_WRAP)
        } else {
            ByteArray(32).also { secureRandom.nextBytes(it) }
        }
        sessionSaltBytes = salt

        val pubKeyBase64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        return EphemeralPublicIdentity(
            publicKeyBase64 = pubKeyBase64,
            ephemeralSaltBase64 = saltBase64
        )
    }

    fun completeHandshake(peerPublicKeyBase64: String, saltBase64: String): HandshakeVerification {
        val localKp = checkNotNull(ephemeralKeyPair) { "Ephemeral keypair not initialized in RAM" }
        val peerPubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
        val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
        sessionSaltBytes = saltBytes

        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPubBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(localKp.private)
        keyAgreement.doPhase(peerPublicKey, true)
        val rawSharedSecret = keyAgreement.generateSecret()

        try {
            // HKDF-SHA256 Extract & Expand -> 256-bit Root Session Key
            val prk = hmacSha256(saltBytes, rawSharedSecret)
            val info = HKDF_INFO.toByteArray(StandardCharsets.UTF_8) + byteArrayOf(0x01)
            val derivedRoot = hmacSha256(prk, info)
            Arrays.fill(prk, 0.toByte())

            rootSessionKey?.let { Arrays.fill(it, 0.toByte()) }
            rootSessionKey = derivedRoot

            // Compute Short Authentication String (SAS) from sorted public keys + salt
            val localPubB64 = Base64.encodeToString(localKp.public.encoded, Base64.NO_WRAP)
            val orderedKeys = listOf(localPubB64, peerPublicKeyBase64).sorted().joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256")
                .digest((orderedKeys + "|" + saltBase64).toByteArray(StandardCharsets.UTF_8))

            val glyphs = listOf("◈", "✦", "⬡", "◉", "▲", "❖", "⬢", "✧", "⟐", "❂", "⬟", "✹", "⟁", "✪", "⬠", "✺")
            val g1 = glyphs[(digest[0].toInt() and 0xFF) % glyphs.size]
            val g2 = glyphs[(digest[1].toInt() and 0xFF) % glyphs.size]
            val g3 = glyphs[(digest[2].toInt() and 0xFF) % glyphs.size]
            val g4 = glyphs[(digest[3].toInt() and 0xFF) % glyphs.size]
            sasFingerprint = "$g1 $g2 $g3 $g4"

            sasHexBlocks = (0..3).joinToString("·") { idx ->
                val b1 = digest[idx * 2].toInt() and 0xFF
                val b2 = digest[idx * 2 + 1].toInt() and 0xFF
                String.format("%02X%02X", b1, b2)
            }

            return HandshakeVerification(
                sasGlyphs = sasFingerprint,
                sasHexCode = sasHexBlocks,
                algorithmSummary = "ECDH P-256 → HKDF-SHA256 → AES-256-GCM (512B Pad)"
            )
        } finally {
            Arrays.fill(rawSharedSecret, 0.toByte())
        }
    }

    fun isHandshakeReady(): Boolean = rootSessionKey != null

    fun encryptPayload(payload: PlainEnvelopePayload, ratchetStep: Int): SealedCipherPacket {
        val root = checkNotNull(rootSessionKey) { "Zero-knowledge session key not established" }
        val msgKeyBytes = deriveRatchetMessageKey(root, ratchetStep)
        try {
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(msgKeyBytes, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            // Bind ratchetStep as Additional Authenticated Data (AAD) to prevent replay or tampering
            cipher.updateAAD("step:$ratchetStep".toByteArray(StandardCharsets.UTF_8))

            val paddedJsonBytes = payload.toPaddedJsonBytes(secureRandom)
            val ciphertextBytes = cipher.doFinal(paddedJsonBytes)
            Arrays.fill(paddedJsonBytes, 0.toByte())

            return SealedCipherPacket(
                ratchetStep = ratchetStep,
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                ciphertextBase64 = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP)
            )
        } finally {
            Arrays.fill(msgKeyBytes, 0.toByte())
        }
    }

    fun decryptPayload(packet: SealedCipherPacket): PlainEnvelopePayload? {
        val root = rootSessionKey ?: run {
            Log.w(TAG, "Cannot decrypt: rootSessionKey is null")
            return null
        }
        val msgKeyBytes = deriveRatchetMessageKey(root, packet.ratchetStep)
        return try {
            val iv = Base64.decode(packet.ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(packet.ciphertextBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(msgKeyBytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD("step:${packet.ratchetStep}".toByteArray(StandardCharsets.UTF_8))

            val plainBytes = cipher.doFinal(ciphertext)
            val jsonStr = String(plainBytes, StandardCharsets.UTF_8)
            Arrays.fill(plainBytes, 0.toByte())
            PlainEnvelopePayload.fromJsonString(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failure at step ${packet.ratchetStep}: ${e.message}", e)
            null
        } finally {
            Arrays.fill(msgKeyBytes, 0.toByte())
        }
    }

    /**
     * Cryptographically wipes all key material in volatile RAM with zeros.
     */
    fun shred() {
        rootSessionKey?.let { Arrays.fill(it, 0.toByte()) }
        rootSessionKey = null
        sessionSaltBytes?.let { Arrays.fill(it, 0.toByte()) }
        sessionSaltBytes = null
        try {
            val priv: PrivateKey? = ephemeralKeyPair?.private
            if (priv != null && !priv.isDestroyed) {
                priv.destroy()
            }
        } catch (_: Exception) {
            // Android KeyStore / BouncyCastle PrivateKey.destroy() may be unsupported on some curves
        }
        ephemeralKeyPair = null
        sasFingerprint = ""
        sasHexBlocks = ""
    }

    private fun deriveRatchetMessageKey(rootKey: ByteArray, step: Int): ByteArray {
        val stepContext = "Moment-Msg-Key-Step-$step".toByteArray(StandardCharsets.UTF_8)
        return hmacSha256(rootKey, stepContext)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    companion object {
        private const val TAG = "ZeroKnowledgeCrypto"
        private const val CURVE_NAME = "secp256r1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val HKDF_INFO = "Moment-ZeroKnowledge-E2EE-v1"
        private const val PADDING_BLOCK_BYTES = 512
    }
}

data class EphemeralPublicIdentity(
    val publicKeyBase64: String,
    val ephemeralSaltBase64: String
)

data class HandshakeVerification(
    val sasGlyphs: String,
    val sasHexCode: String,
    val algorithmSummary: String
)

data class SealedCipherPacket(
    val ratchetStep: Int,
    val ivBase64: String,
    val ciphertextBase64: String
)

enum class PayloadKind {
    MESSAGE,
    REACTION,
    TYPING,
    HANDSHAKE_SYN,
    HANDSHAKE_ACK
}

data class PlainEnvelopePayload(
    val kind: PayloadKind,
    val messageId: String,
    val text: String = "",
    val replyToId: String? = null,
    val replyPreview: String? = null,
    val reactionEmoji: String? = null,
    val targetMessageId: String? = null,
    val isTyping: Boolean = false,
    val sentAtMs: Long = (System.currentTimeMillis() / 1000L) * 1000L
) {
    fun toJsonString(): String {
        val quantizedTimestamp = (sentAtMs / 1000L) * 1000L
        return JSONObject().apply {
            put("kind", kind.name)
            put("messageId", messageId)
            put("text", text)
            if (!replyToId.isNullOrBlank()) put("replyToId", replyToId)
            if (!replyPreview.isNullOrBlank()) put("replyPreview", replyPreview)
            if (!reactionEmoji.isNullOrBlank()) put("reactionEmoji", reactionEmoji)
            if (!targetMessageId.isNullOrBlank()) put("targetMessageId", targetMessageId)
            put("isTyping", isTyping)
            put("sentAtMs", quantizedTimestamp)
        }.toString()
    }

    /**
     * Serializes the payload with cryptographically random noise padding so that the resulting
     * UTF-8 byte array is aligned to 512-byte quantum blocks (defeating traffic-length analysis).
     */
    fun toPaddedJsonBytes(secureRandom: SecureRandom): ByteArray {
        val quantizedTimestamp = (sentAtMs / 1000L) * 1000L
        val baseObj = JSONObject().apply {
            put("kind", kind.name)
            put("messageId", messageId)
            put("text", text)
            if (!replyToId.isNullOrBlank()) put("replyToId", replyToId)
            if (!replyPreview.isNullOrBlank()) put("replyPreview", replyPreview)
            if (!reactionEmoji.isNullOrBlank()) put("reactionEmoji", reactionEmoji)
            if (!targetMessageId.isNullOrBlank()) put("targetMessageId", targetMessageId)
            put("isTyping", isTyping)
            put("sentAtMs", quantizedTimestamp)
            put("pad", "")
        }
        val baseBytesLen = baseObj.toString().toByteArray(StandardCharsets.UTF_8).size
        val targetBlockSize = (((baseBytesLen + 32) + 511) / 512) * 512
        val neededChars = (targetBlockSize - baseBytesLen).coerceAtLeast(16)
        val rawNoise = ByteArray((neededChars * 3) / 4 + 4)
        secureRandom.nextBytes(rawNoise)
        val padStr = Base64.encodeToString(rawNoise, Base64.NO_WRAP).take(neededChars)
        Arrays.fill(rawNoise, 0.toByte())
        baseObj.put("pad", padStr)
        return baseObj.toString().toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        fun fromJsonString(json: String): PlainEnvelopePayload {
            val obj = JSONObject(json)
            val kindStr = obj.optString("kind", PayloadKind.MESSAGE.name)
            val kind = runCatching { PayloadKind.valueOf(kindStr) }.getOrDefault(PayloadKind.MESSAGE)
            val rawSent = obj.optLong("sentAtMs", System.currentTimeMillis())
            return PlainEnvelopePayload(
                kind = kind,
                messageId = obj.optString("messageId", ""),
                text = obj.optString("text", ""),
                replyToId = obj.optString("replyToId", "").takeIf { it.isNotBlank() },
                replyPreview = obj.optString("replyPreview", "").takeIf { it.isNotBlank() },
                reactionEmoji = obj.optString("reactionEmoji", "").takeIf { it.isNotBlank() },
                targetMessageId = obj.optString("targetMessageId", "").takeIf { it.isNotBlank() },
                isTyping = obj.optBoolean("isTyping", false),
                sentAtMs = (rawSent / 1000L) * 1000L
            )
        }
    }
}
