package io.github.thelony97.orbit.mail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class MailAccount(
    val id: String = newId(),
    val email: String,
    val host: String,
    val port: Int = 993,
    val username: String,
    /** Password encrypted with a key that never leaves the Android Keystore. */
    val secret: String,
    val provider: String = "other",
    val lastError: String? = null,
)

data class MailProvider(
    val key: String,
    val label: String,
    val host: String,
    val port: Int = 993,
    val domains: List<String>,
    val hint: String,
    val supported: Boolean = true,
)

object MailProviders {
    val all = listOf(
        MailProvider(
            "gmail", "Gmail", "imap.gmail.com", domains = listOf("gmail.com", "googlemail.com"),
            hint = "Gmail braucht ein App-Passwort: Google-Konto → Sicherheit → 2-Faktor-Authentifizierung aktivieren → „App-Passwörter“. Dein normales Passwort funktioniert nicht.",
        ),
        MailProvider(
            "gmx", "GMX", "imap.gmx.net", domains = listOf("gmx.at", "gmx.de", "gmx.net", "gmx.ch", "gmx.com"),
            hint = "Bei GMX zuerst unter E-Mail → Einstellungen → POP3/IMAP Abruf den Zugriff erlauben. Dann dein GMX-Passwort (oder App-Passwort, falls 2FA aktiv) eingeben.",
        ),
        MailProvider(
            "webde", "web.de", "imap.web.de", domains = listOf("web.de"),
            hint = "Bei web.de zuerst unter E-Mail → Einstellungen → POP3/IMAP Abruf den Zugriff erlauben. Dann dein Passwort (oder App-Passwort, falls 2FA aktiv) eingeben.",
        ),
        MailProvider(
            "icloud", "iCloud", "imap.mail.me.com", domains = listOf("icloud.com", "me.com", "mac.com"),
            hint = "iCloud braucht ein app-spezifisches Passwort: account.apple.com → Anmeldung und Sicherheit → App-spezifische Passwörter.",
        ),
        MailProvider(
            "yahoo", "Yahoo", "imap.mail.yahoo.com", domains = listOf("yahoo.com", "yahoo.de", "ymail.com"),
            hint = "Yahoo braucht ein App-Passwort: Kontosicherheit → App-Passwort generieren.",
        ),
        MailProvider(
            "tonline", "T-Online", "secureimap.t-online.de", domains = listOf("t-online.de"),
            hint = "T-Online braucht ein eigenes E-Mail-Passwort (Kundencenter → E-Mail-Passwort), nicht das Login-Passwort.",
        ),
        MailProvider(
            "outlook", "Outlook / Hotmail", "outlook.office365.com", domains = listOf("outlook.com", "hotmail.com", "live.com", "outlook.de", "hotmail.de", "live.de", "msn.com"),
            hint = "Microsoft lässt seit September 2024 keine Anmeldung mit Passwort mehr zu (nur noch OAuth). Tipp: Mails in Outlook automatisch an ein Gmail-/GMX-Konto weiterleiten und das verbinden.",
            supported = false,
        ),
        MailProvider(
            "other", "Anderer Server", "", domains = emptyList(),
            hint = "IMAP-Server und Port deines Anbieters eintragen (fast immer Port 993 mit SSL).",
        ),
    )

    fun byKey(key: String) = all.firstOrNull { it.key == key } ?: all.last()

    fun forEmail(email: String): MailProvider {
        val domain = email.substringAfter('@', "").lowercase().trim()
        return all.firstOrNull { domain in it.domains } ?: all.last()
    }
}

/** Encrypts small secrets with an AES key that lives in the Android Keystore. */
object Secrets {
    private const val ALIAS = "orbit-mail-key"

    private fun key(): SecretKey {
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

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(value: String): String? = runCatching {
        val (iv, ct) = value.split(':', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)))
    }.getOrNull()
}

/**
 * Linked mail accounts. Stored outside orbit.json (in no-backup storage) so passwords
 * never end up in a backup ZIP.
 */
object MailAccounts {
    private const val FILE = "mail_accounts.json"
    private lateinit var file: File
    private val serializer = ListSerializer(MailAccount.serializer())
    private val lock = Mutex()
    private val _accounts = MutableStateFlow<List<MailAccount>>(emptyList())
    val accounts: StateFlow<List<MailAccount>> = _accounts.asStateFlow()

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.noBackupFilesDir, FILE)
        _accounts.value = runCatching { OrbitRepository.json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    /** Applies [change] to the current list and writes it; serialised so parallel updates don't clash. */
    private suspend fun mutate(change: (List<MailAccount>) -> List<MailAccount>) = lock.withLock {
        val list = change(_accounts.value)
        if (list != _accounts.value) persist(list)
    }

    private suspend fun persist(list: List<MailAccount>) = withContext(Dispatchers.IO) {
        _accounts.value = list
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(OrbitRepository.json.encodeToString(serializer, list))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    suspend fun upsert(account: MailAccount) = mutate { list ->
        if (list.any { it.id == account.id }) list.map { if (it.id == account.id) account else it } else list + account
    }

    suspend fun remove(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
        MailCache.clear()
    }

    suspend fun setError(id: String, error: String?) = mutate { list ->
        list.map { if (it.id == id && it.lastError != error) it.copy(lastError = error) else it }
    }

    fun password(a: MailAccount): String? = Secrets.decrypt(a.secret)
}
