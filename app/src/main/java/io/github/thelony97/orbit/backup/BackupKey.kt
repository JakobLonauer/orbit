package io.github.thelony97.orbit.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.thelony97.orbit.core.domain.BackupCrypto
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.store.StoreUpgrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The backup key (PLAN 4): derived once from Jakob's password (PBKDF2), then kept wrapped by
 * the Android Keystore in no-backup storage so automatic backups can encrypt without asking.
 * The password itself is never stored; restoring always asks for it.
 */
object BackupKey {
    private const val ALIAS = "orbit-backup-key"
    private const val FILE = "backup_key.json"

    @Serializable
    private data class Stored(val salt: String, val iterations: Int, val iv: String, val wrapped: String)

    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, FILE)

    fun isSet(ctx: Context): Boolean = file(ctx).exists()

    /** Derives and stores a new key. Slow on purpose (PBKDF2); runs off the main thread. */
    suspend fun set(ctx: Context, password: CharArray) = withContext(Dispatchers.Default) {
        val key = BackupCrypto.derive(password, BackupCrypto.newSalt())
        password.fill(' ')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val wrapped = cipher.doFinal(key.bytes)
        val stored = Stored(b64(key.salt), key.iterations, b64(cipher.iv), b64(wrapped))
        StoreUpgrade.atomicWrite(file(ctx), OrbitRepository.json.encodeToString(Stored.serializer(), stored))
    }

    fun load(ctx: Context): BackupCrypto.Key? = runCatching {
        val s = OrbitRepository.json.decodeFromString(Stored.serializer(), file(ctx).readText())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, unb64(s.iv)))
        BackupCrypto.Key(cipher.doFinal(unb64(s.wrapped)), unb64(s.salt), s.iterations)
    }.getOrNull()

    fun clear(ctx: Context) {
        file(ctx).delete()
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
