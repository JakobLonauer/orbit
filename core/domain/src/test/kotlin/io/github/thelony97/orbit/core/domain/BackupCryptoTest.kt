package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

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

    @Test fun manyChunksStreamAndExactMultiples() {
        for (size in listOf(0, 1, 100, 300, 301, 1000)) {
            val data = Random(size).nextBytes(size)
            val enc = ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(data), it, key, chunk = 100) }.toByteArray()
            val dec = ByteArrayOutputStream().also { BackupCrypto.decrypt(ByteArrayInputStream(enc), it, key) }.toByteArray()
            assertArrayEquals("size $size", data, dec)
        }
    }

    @Test fun wrongPasswordFails() {
        val enc = BackupCrypto.encrypt(plain, key)
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(enc, "falsch".toCharArray()) }
    }

    @Test fun tamperingAndTruncationFail() {
        val data = Random(1).nextBytes(350)
        val enc = ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(data), it, key, chunk = 100) }.toByteArray()
        val flipped = enc.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(flipped, key) }
        // Dropping the last chunk must not look like a shorter valid backup.
        val truncated = enc.copyOf(enc.size - (4 + 50 + 16))
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(truncated, key) }
        val iterFlip = enc.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(iterFlip, pw.copyOf()) }
    }

    @Test fun freshNonceEveryTime() {
        assertFalse(BackupCrypto.encrypt(plain, key).contentEquals(BackupCrypto.encrypt(plain, key)))
    }

    @Test fun garbageFails() {
        assertThrows(BackupCrypto.WrongPassword::class.java) { BackupCrypto.decrypt(byteArrayOf(1, 2, 3), key) }
    }
}
