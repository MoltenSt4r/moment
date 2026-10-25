package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.crypto.PayloadKind
import com.example.crypto.PlainEnvelopePayload
import com.example.crypto.SealedCipherPacket
import com.example.crypto.ZeroKnowledgeSessionCrypto
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class BlindRelayRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndJoinRoom_withZeroKnowledgeEcdhHandshake_succeedsAndEncryptsEnvelope() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = BlindRelayRepository(firestore)

        val aliceCrypto = ZeroKnowledgeSessionCrypto()
        val aliceIdentity = aliceCrypto.initializeEphemeralIdentity()
        val roomId = "room_test_${UUID.randomUUID().toString().replace("-", "")}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createWaitingRoom(
                hostPubKey = aliceIdentity.publicKeyBase64,
                ephemeralSalt = aliceIdentity.ephemeralSaltBase64,
                customRoomId = roomId
            )
        }
        assertTrue(createResult.isSuccess)

        // Bob joins waiting room with his own ephemeral ECDH keypair
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobCrypto = ZeroKnowledgeSessionCrypto()
        val bobIdentity = bobCrypto.initializeEphemeralIdentity(aliceIdentity.ephemeralSaltBase64)

        val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.joinWaitingRoom(
                roomId = roomId,
                guestUid = bobUid,
                guestPubKey = bobIdentity.publicKeyBase64
            )
        }
        assertTrue(joinResult.isSuccess)

        // Both complete ECDH + HKDF-SHA256 handshake locally without private keys ever leaving RAM
        val bobHandshake = bobCrypto.completeHandshake(
            peerPublicKeyBase64 = aliceIdentity.publicKeyBase64,
            saltBase64 = aliceIdentity.ephemeralSaltBase64
        )
        val aliceHandshake = aliceCrypto.completeHandshake(
            peerPublicKeyBase64 = bobIdentity.publicKeyBase64,
            saltBase64 = aliceIdentity.ephemeralSaltBase64
        )
        assertEquals(aliceHandshake.sasHexCode, bobHandshake.sasHexCode)

        // Bob encrypts a message using AES-256-GCM and sends it via BlindRelayRepository
        val plain = PlainEnvelopePayload(
            kind = PayloadKind.MESSAGE,
            messageId = "msg_1",
            text = "Привет из Zero-Knowledge канала!"
        )
        val sealed = bobCrypto.encryptPayload(plain, ratchetStep = 1)
        val envSendResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.sendEncryptedEnvelope(
                roomId = roomId,
                senderUid = bobUid,
                participantUids = listOf(aliceUid, bobUid),
                ratchetStep = sealed.ratchetStep,
                ivBase64 = sealed.ivBase64,
                ciphertextBase64 = sealed.ciphertextBase64,
                ttlSeconds = 30
            )
        }
        assertTrue(envSendResult.isSuccess)

        // Alice observes room envelopes and decrypts Bob's ciphertext
        signInTestUser(ALICE_EMAIL)
        val envelopes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeRoomEnvelopes(roomId, aliceUid).first { it.isNotEmpty() }
        }
        val received = envelopes.first()
        val decrypted = aliceCrypto.decryptPayload(
            SealedCipherPacket(
                ratchetStep = received.ratchetStep.toInt(),
                ivBase64 = received.iv,
                ciphertextBase64 = received.ciphertext
            )
        )
        assertNotNull(decrypted)
        assertEquals("Привет из Zero-Knowledge канала!", decrypted?.text)
    }

    @Test
    fun getRoomById_crossUserAccessToActiveRoom_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = BlindRelayRepository(firestore)
        val aliceCrypto = ZeroKnowledgeSessionCrypto()
        val aliceIdentity = aliceCrypto.initializeEphemeralIdentity()
        val roomId = "room_private_${UUID.randomUUID().toString().replace("-", "")}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createWaitingRoom(
                hostPubKey = aliceIdentity.publicKeyBase64,
                ephemeralSalt = aliceIdentity.ephemeralSaltBase64,
                customRoomId = roomId
            ).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobCrypto = ZeroKnowledgeSessionCrypto()
        val bobIdentity = bobCrypto.initializeEphemeralIdentity(aliceIdentity.ephemeralSaltBase64)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.joinWaitingRoom(roomId, bobUid, bobIdentity.publicKeyBase64).getOrThrow()
        }

        // Eve (not host or guest) tries to read the active room
        signInTestUser(EVE_EMAIL)
        val eveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getRoomById(roomId)
        }
        assertTrue(eveResult.isFailure)
        val firestoreEx = eveResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeRoomEnvelopes_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = BlindRelayRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeRoomEnvelopes("room_unauth", "anon_uid").first()
            }
            fail("Expected exception for unauthenticated caller")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@moment.test"
        const val BOB_EMAIL = "bob@moment.test"
        const val EVE_EMAIL = "eve@moment.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
