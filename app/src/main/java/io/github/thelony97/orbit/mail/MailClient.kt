package io.github.thelony97.orbit.mail

import android.text.Html
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.SearchTerm

data class MailHeader(
    val accountId: String,
    val accountEmail: String,
    val folder: String,
    val uid: Long,
    val messageId: String?,
    val from: String,
    val fromAddress: String,
    val subject: String,
    val at: Long,
)

data class MailResult(val headers: List<MailHeader>, val errors: List<String>, val fetchedAt: Long)

/** Results per contact for a few minutes, only in memory. */
object MailCache {
    private const val FRESH_MS = 15 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, MailResult>()

    fun key(contactId: String, addresses: List<String>, accounts: List<MailAccount>) =
        contactId + "|" + addresses.map { it.lowercase() }.sorted().joinToString(",") + "|" + accounts.joinToString(",") { it.id }

    fun get(key: String): MailResult? = cache[key]
    fun isFresh(r: MailResult) = System.currentTimeMillis() - r.fetchedAt < FRESH_MS
    fun put(key: String, r: MailResult) {
        cache[key] = r
    }

    fun clear() = cache.clear()
}

/** Minimal IMAP client: who wrote to me, and what. Read-only. */
object MailClient {

    private fun session(host: String, port: Int): Session {
        val p = Properties()
        p["mail.store.protocol"] = "imaps"
        p["mail.imaps.host"] = host
        p["mail.imaps.port"] = port.toString()
        p["mail.imaps.ssl.enable"] = "true"
        p["mail.imaps.ssl.checkserveridentity"] = "true"
        p["mail.imaps.connectiontimeout"] = "15000"
        p["mail.imaps.timeout"] = "25000"
        return Session.getInstance(p)
    }

    private fun connect(host: String, port: Int, user: String, password: String): Store {
        val store = session(host, port).getStore("imaps")
        store.connect(host, port, user, password)
        return store
    }

    fun describe(e: Throwable): String {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is UnknownHostException) return "Server nicht erreichbar – Internetverbindung oder Servername prüfen."
            if (cause is AuthenticationFailedException) return "Anmeldung abgelehnt – E-Mail und (App-)Passwort prüfen. Bei manchen Anbietern muss IMAP erst aktiviert werden."
            cause = cause.cause
        }
        return when (e) {
            is MessagingException -> "Fehler beim Abrufen: ${e.message ?: e.javaClass.simpleName}"
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    /** Logs in once to check the credentials. */
    suspend fun test(host: String, port: Int, user: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val store = connect(host, port, user, password)
            try {
                store.getFolder("INBOX").exists()
            } finally {
                runCatching { store.close() }
            }
            Unit
        }
    }

    private fun foldersToSearch(store: Store): List<Folder> {
        val result = mutableListOf(store.getFolder("INBOX"))
        runCatching {
            store.defaultFolder.list("*").forEach { f ->
                val attrs = (f as? IMAPFolder)?.attributes ?: return@forEach
                if (attrs.any { it.equals("\\All", ignoreCase = true) || it.equals("\\Archive", ignoreCase = true) }) result += f
            }
        }
        return result
    }

    private fun searchAccount(a: MailAccount, addresses: List<String>, limit: Int): List<MailHeader> {
        val password = MailAccounts.password(a) ?: throw IllegalStateException("Passwort nicht mehr lesbar – bitte das Konto neu verbinden.")
        val store = connect(a.host, a.port, a.username, password)
        try {
            val terms: List<SearchTerm> = addresses.map { FromStringTerm(it) }
            val term: SearchTerm = if (terms.size == 1) terms[0] else OrTerm(terms.toTypedArray())
            val out = ArrayList<MailHeader>()
            foldersToSearch(store).forEach { folder ->
                runCatching {
                    folder.open(Folder.READ_ONLY)
                    try {
                        val found: Array<Message> = folder.search(term)
                        val newest = found.sortedByDescending { it.messageNumber }.take(limit).toTypedArray()
                        if (newest.isNotEmpty()) {
                            val fp = FetchProfile()
                            fp.add(FetchProfile.Item.ENVELOPE)
                            fp.add(UIDFolder.FetchProfileItem.UID)
                            folder.fetch(newest, fp)
                            newest.forEach { m ->
                                val sender = m.from?.firstOrNull() as? InternetAddress
                                out += MailHeader(
                                    accountId = a.id,
                                    accountEmail = a.email,
                                    folder = folder.fullName,
                                    uid = (folder as UIDFolder).getUID(m),
                                    messageId = (m as? MimeMessage)?.messageID,
                                    from = sender?.personal ?: sender?.address ?: "",
                                    fromAddress = sender?.address ?: "",
                                    subject = m.subject?.takeIf { it.isNotBlank() } ?: "(kein Betreff)",
                                    at = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
                                )
                            }
                        }
                    } finally {
                        runCatching { folder.close(false) }
                    }
                }
            }
            return out
        } finally {
            runCatching { store.close() }
        }
    }

    /** Newest mails sent from any of [addresses], across all linked accounts. */
    suspend fun fromAddresses(addresses: List<String>, accounts: List<MailAccount>, limit: Int = 25): MailResult = withContext(Dispatchers.IO) {
        val clean = addresses.map { it.trim() }.filter { it.contains('@') }.distinctBy { it.lowercase() }
        if (clean.isEmpty() || accounts.isEmpty()) return@withContext MailResult(emptyList(), emptyList(), System.currentTimeMillis())
        val perAccount = coroutineScope {
            accounts.map { a ->
                async {
                    val r = runCatching { searchAccount(a, clean, limit) }
                    MailAccounts.setError(a.id, r.exceptionOrNull()?.let { describe(it) })
                    a to r
                }
            }.awaitAll()
        }
        val headers = perAccount.flatMap { it.second.getOrNull().orEmpty() }
            .sortedByDescending { it.at }
            .distinctBy { it.messageId ?: "${it.accountId}/${it.folder}/${it.uid}" }
            .take(limit)
        val errors = perAccount.mapNotNull { (a, r) -> r.exceptionOrNull()?.let { "${a.email}: ${describe(it)}" } }
        MailResult(headers, errors, System.currentTimeMillis())
    }

    /** Plain text of one message (HTML is flattened). */
    suspend fun body(h: MailHeader): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val a = MailAccounts.accounts.value.firstOrNull { it.id == h.accountId } ?: error("Konto wurde entfernt.")
            val password = MailAccounts.password(a) ?: error("Passwort nicht mehr lesbar.")
            val store = connect(a.host, a.port, a.username, password)
            try {
                val folder = store.getFolder(h.folder)
                folder.open(Folder.READ_ONLY)
                try {
                    val m = (folder as UIDFolder).getMessageByUID(h.uid) ?: error("Die Nachricht gibt es nicht mehr.")
                    (text(m) ?: "(Kein Text – vermutlich nur Anhänge.)").trim().take(20_000)
                } finally {
                    runCatching { folder.close(false) }
                }
            } finally {
                runCatching { store.close() }
            }
        }
    }

    private fun text(p: Part): String? {
        return when {
            p.isMimeType("text/plain") -> p.content as? String
            p.isMimeType("text/html") -> (p.content as? String)?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString() }
            p.isMimeType("multipart/alternative") -> {
                val mp = p.content as? Multipart ?: return null
                var fallback: String? = null
                for (i in 0 until mp.count) {
                    val part = mp.getBodyPart(i)
                    if (part.isMimeType("text/plain")) return part.content as? String
                    if (fallback == null) fallback = text(part)
                }
                fallback
            }
            p.isMimeType("multipart/*") -> {
                val mp = p.content as? Multipart ?: return null
                for (i in 0 until mp.count) {
                    val part = mp.getBodyPart(i)
                    if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) continue
                    text(part)?.let { return it }
                }
                null
            }
            p.isMimeType("message/rfc822") -> (p.content as? Part)?.let { text(it) }
            else -> null
        }
    }
}
