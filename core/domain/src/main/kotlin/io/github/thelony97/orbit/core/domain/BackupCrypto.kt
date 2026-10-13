package io.github.thelony97.orbit.core.domain

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup encryption (PLAN 4): PBKDF2-HMAC-SHA256 with salt -> AES-256-GCM.
 *
 * File layout: magic "ORBK" | version 1 | iterations (int) | salt (16) | iv (12) | ciphertext+tag.
 * The salt travels with the file, so restoring needs only the password. Automatic backups
 * reuse a derived [Key] (kept Keystore-protected by the app), never the password itself.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('O'.code.toByte(), 'R'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 310_000
    private const val SALT = 16
    private const val IV = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    class Key(val bytes: ByteArray, val salt: ByteArray, val iterations: Int)

    class WrongPassword : Exception("Passwort falsch oder Datei beschädigt")

    fun newSalt(): ByteArray = ByteArray(SALT).also(random::nextBytes)

    fun derive(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): Key {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Key(bytes, salt.copyOf(), iterations)
        } finally {
            spec.clearPassword()
        }
    }

    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, key: Key): ByteArray {
        val iv = ByteArray(IV).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header(key))
        val body = cipher.doFinal(plain)
        return ByteBuffer.allocate(headerSize() + IV + body.size)
            .put(header(key)).put(iv).put(body).array()
    }

    /** Reads salt and iterations from [data] and derives the key from [password]. */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        val (salt, iterations) = readHeader(data)
        return decrypt(data, derive(password, salt, iterations))
    }

    fun decrypt(data: ByteArray, key: Key): ByteArray {
        val (salt, iterations) = readHeader(data)
        if (!salt.contentEquals(key.salt) || iterations != key.iterations) throw WrongPassword()
        val hs = headerSize()
        val iv = data.copyOfRange(hs, hs + IV)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(data.copyOfRange(0, hs))
        return try {
            cipher.doFinal(data, hs + IV, data.size - hs - IV)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassword()
        }
    }

    private fun headerSize() = MAGIC.size + 1 + 4 + SALT

    private fun header(key: Key): ByteArray =
        ByteBuffer.allocate(headerSize()).put(MAGIC).put(VERSION).putInt(key.iterations).put(key.salt).array()

    private fun readHeader(data: ByteArray): Pair<ByteArray, Int> {
        if (!isEncrypted(data) || data.size < headerSize() + IV + TAG_BITS / 8) throw WrongPassword()
        val b = ByteBuffer.wrap(data)
        b.position(MAGIC.size)
        if (b.get() != VERSION) throw WrongPassword()
        val iterations = b.int
        val salt = ByteArray(SALT).also { b.get(it) }
        return salt to iterations
    }
}
