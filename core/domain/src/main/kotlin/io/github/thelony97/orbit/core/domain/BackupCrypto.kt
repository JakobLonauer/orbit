package io.github.thelony97.orbit.core.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup encryption (PLAN 4): PBKDF2-HMAC-SHA256 with salt -> AES-256-GCM, streamed in chunks
 * so backups with many photos never need to fit into memory.
 *
 * Layout: header = "ORBK" | version 2 | iterations (int) | salt (16) | file id (8),
 * then chunks: plain length (int) | ciphertext+tag. Every file gets its own AES key
 * (HMAC-SHA256(key, file id)), chunk i uses nonce = 0000 | i and AAD = header | i | last-flag,
 * so nonces never repeat and reordering, truncating or editing any byte fails.
 * The salt travels with the file: restoring needs only the password. Automatic backups reuse
 * a derived [Key] (kept Keystore-protected by the app), never the password itself.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('O'.code.toByte(), 'R'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 2
    const val DEFAULT_ITERATIONS = 310_000
    private const val SALT = 16
    private const val PREFIX = 8
    private const val TAG_BITS = 128
    const val CHUNK = 1 shl 20
    private val HEADER = MAGIC.size + 1 + 4 + SALT + PREFIX
    private val random = SecureRandom()

    class Key(val bytes: ByteArray, val salt: ByteArray, val iterations: Int)

    class WrongPassword : Exception("Passwort falsch oder Datei beschädigt")

    fun newSalt(): ByteArray = ByteArray(SALT).also { random.nextBytes(it) }

    fun derive(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): Key {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Key(bytes, salt.copyOf(), iterations)
        } finally {
            spec.clearPassword()
        }
    }

    fun isEncrypted(head: ByteArray): Boolean =
        head.size >= MAGIC.size && head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** Streams [input] encrypted into [output]. Does not close either stream. */
    fun encrypt(input: InputStream, output: OutputStream, key: Key, chunk: Int = CHUNK) {
        val prefix = ByteArray(PREFIX).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).putInt(key.iterations)
            .put(key.salt).put(prefix).array()
        output.write(header)
        val out = DataOutputStream(output)
        val secret = fileKey(key, prefix)
        var buf = readFull(input, chunk)
        var index = 0L
        while (true) {
            val next = if (buf.size == chunk) readFull(input, chunk) else ByteArray(0)
            val last = next.isEmpty()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce(index)))
            cipher.updateAAD(aad(header, index, last))
            out.writeInt(buf.size)
            out.write(cipher.doFinal(buf))
            if (last) break
            buf = next
            index++
        }
        out.flush()
    }

    /** Reads salt and iterations from the stream and derives the key from [password]. */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val header = readHeader(input)
        val salt = header.copyOfRange(MAGIC.size + 5, MAGIC.size + 5 + SALT)
        val iterations = ByteBuffer.wrap(header, MAGIC.size + 1, 4).int
        decryptBody(input, output, header, derive(password, salt, iterations))
    }

    fun decrypt(input: InputStream, output: OutputStream, key: Key) {
        val header = readHeader(input)
        val salt = header.copyOfRange(MAGIC.size + 5, MAGIC.size + 5 + SALT)
        val iterations = ByteBuffer.wrap(header, MAGIC.size + 1, 4).int
        if (!salt.contentEquals(key.salt) || iterations != key.iterations) throw WrongPassword()
        decryptBody(input, output, header, key)
    }

    // Convenience for small payloads and tests.
    fun encrypt(plain: ByteArray, key: Key): ByteArray =
        ByteArrayOutputStream().also { encrypt(ByteArrayInputStream(plain), it, key) }.toByteArray()

    fun decrypt(data: ByteArray, password: CharArray): ByteArray =
        ByteArrayOutputStream().also { decrypt(ByteArrayInputStream(data), it, password) }.toByteArray()

    fun decrypt(data: ByteArray, key: Key): ByteArray =
        ByteArrayOutputStream().also { decrypt(ByteArrayInputStream(data), it, key) }.toByteArray()

    private fun decryptBody(input: InputStream, output: OutputStream, header: ByteArray, key: Key) {
        val prefix = header.copyOfRange(HEADER - PREFIX, HEADER)
        val secret = fileKey(key, prefix)
        val din = DataInputStream(input)
        var index = 0L
        var pending: ByteArray? = readChunk(din) ?: throw WrongPassword()
        while (pending != null) {
            val next = readChunk(din)
            val last = next == null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce(index)))
            cipher.updateAAD(aad(header, index, last))
            val plain = try {
                cipher.doFinal(pending)
            } catch (e: AEADBadTagException) {
                throw WrongPassword()
            }
            output.write(plain)
            pending = next
            index++
        }
        output.flush()
    }

    /** One stored chunk (ciphertext+tag) or null at a clean end of stream. */
    private fun readChunk(din: DataInputStream): ByteArray? {
        val len = try {
            din.readInt()
        } catch (e: EOFException) {
            return null
        }
        if (len < 0 || len > CHUNK * 4) throw WrongPassword()
        val ct = ByteArray(len + TAG_BITS / 8)
        try {
            din.readFully(ct)
        } catch (e: EOFException) {
            throw WrongPassword()
        }
        return ct
    }

    private fun readHeader(input: InputStream): ByteArray {
        val header = readFull(input, HEADER)
        if (header.size < HEADER || !isEncrypted(header) || header[MAGIC.size] != VERSION) throw WrongPassword()
        return header
    }

    private fun readFull(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) buf else buf.copyOf(off)
    }

    private fun fileKey(key: Key, fileId: ByteArray): SecretKeySpec {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.bytes, "HmacSHA256"))
        return SecretKeySpec(mac.doFinal(fileId), "AES")
    }

    private fun nonce(index: Long): ByteArray = ByteBuffer.allocate(12).putInt(0).putLong(index).array()

    private fun aad(header: ByteArray, index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + 9).put(header).putLong(index).put(if (last) 1 else 0).array()
}
