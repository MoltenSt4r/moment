package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.PayloadKind
import com.example.crypto.PlainEnvelopePayload
import com.example.crypto.ZeroKnowledgeSessionCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and verify zero knowledge 512B padded encryption`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Moment", appName)

        val alice = ZeroKnowledgeSessionCrypto()
        val bob = ZeroKnowledgeSessionCrypto()

        val token1 = alice.generateUnlinkableNodeToken()
        val token2 = alice.generateUnlinkableNodeToken()
        assertNotEquals(token1, token2)

        val aliceId = alice.initializeEphemeralIdentity()
        val bobId = bob.initializeEphemeralIdentity(aliceId.ephemeralSaltBase64)

        val aliceSas = alice.completeHandshake(bobId.publicKeyBase64, aliceId.ephemeralSaltBase64)
        val bobSas = bob.completeHandshake(aliceId.publicKeyBase64, aliceId.ephemeralSaltBase64)
        assertEquals(aliceSas.sasHexCode, bobSas.sasHexCode)

        val shortMsg = PlainEnvelopePayload(kind = PayloadKind.MESSAGE, messageId = "m1", text = "Привет")
        val typingMsg = PlainEnvelopePayload(kind = PayloadKind.TYPING, messageId = "t1", isTyping = true)

        val packet1 = alice.encryptPayload(shortMsg, 1)
        val packet2 = alice.encryptPayload(typingMsg, 2)

        // Both short message and typing event are padded to the exact same 512-byte quantum block
        assertEquals(packet1.ciphertextBase64.length, packet2.ciphertextBase64.length)

        val decrypted = bob.decryptPayload(packet1)
        assertNotNull(decrypted)
        assertEquals("Привет", decrypted?.text)
    }
}
