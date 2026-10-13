package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val plain = "Orbit-Backup mit Ümläuten 🎂".toByteArray()
    private val pw = "richtig-langes Passwort".toCharArray()

    // Few iterations keep the test fast; the format stores the count.
    private val key = BackupCrypto.derive(pw.copyOf(), BackupCrypto.newSalt(), iterations = 1_000)

    @Test fun roundTripWithKeyAndWithPassword() {
        val enc = BackupCrypto.encrypt(plain, key)
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, key))
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, pw.copyOf()))
    }

    @Test fun wrongPasswordFails() {
        val enc = BackupCrypto.encrypt(plain, key)
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(enc, "falsch".toCharArray()) }
    }

    @Test fun tamperingFails() {
        val enc = BackupCrypto.encrypt(plain, key)
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(enc, key) }
        val enc2 = BackupCrypto.encrypt(plain, key)
        enc2[8] = (enc2[8].toInt() xor 1).toByte() // iterations field is authenticated too
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(enc2, pw.copyOf()) }
    }

    @Test fun freshIvEveryTime() {
        assertFalse(BackupCrypto.encrypt(plain, key).contentEquals(BackupCrypto.encrypt(plain, key)))
    }

    @Test fun truncatedFails() {
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(byteArrayOf(1, 2, 3), key) }
    }
}
