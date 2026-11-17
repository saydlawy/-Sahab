package com.saydlawy.sahab.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSyncEncryptionTest {

    @Test
    fun encryptV2_andDecrypt_succeedsWithCorrectPin_andRejectsWrongPin() {
        val secretPayload = """[{"server":"https://iptv.example.com","user":"ali","pass":"s3cr3t"}]"""
        val pin = "4829"
        val uid = "user_uid_123"

        val cipher1 = CloudAccountCrypto.encryptV2(secretPayload, pin)
        val cipher2 = CloudAccountCrypto.encryptV2(secretPayload, pin)

        assertTrue(cipher1.startsWith("enc_v2:"))
        // Random 16-byte salt & 12-byte IV guarantee distinct ciphertexts for identical plaintexts
        assertNotEquals(cipher1, cipher2)

        val okResult = CloudAccountCrypto.decrypt(cipher1, uid, pin)
        assertTrue(okResult is CloudDecryptResult.Ok)
        assertEquals(secretPayload, (okResult as CloudDecryptResult.Ok).plain)

        val wrongPinResult = CloudAccountCrypto.decrypt(cipher1, uid, "0000")
        assertTrue(wrongPinResult is CloudDecryptResult.WrongPin)

        val noPinResult = CloudAccountCrypto.decrypt(cipher1, uid, null)
        assertTrue(noPinResult is CloudDecryptResult.PinRequired)
    }

    @Test
    fun decryptLegacyV1_worksForMigrationToV2() {
        val uid = "legacy_uid_999"
        val oldSecret = "https://old-server.example.com"
        val v1Encrypted = CloudAccountCrypto.encryptLegacyV1ForTest(oldSecret, uid)

        val dec = CloudAccountCrypto.decrypt(v1Encrypted, uid, null)
        assertTrue(dec is CloudDecryptResult.Ok)
        assertEquals(oldSecret, (dec as CloudDecryptResult.Ok).plain)

        val migratedV2 = CloudAccountCrypto.encryptV2(oldSecret, "9988")
        assertTrue(migratedV2.startsWith("enc_v2:"))
        val v2Dec = CloudAccountCrypto.decrypt(migratedV2, uid, "9988")
        assertEquals(CloudDecryptResult.Ok(oldSecret), v2Dec)
    }
}
