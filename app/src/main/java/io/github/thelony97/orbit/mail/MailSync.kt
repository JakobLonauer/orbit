package io.github.thelony97.orbit.mail

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sun.mail.imap.IMAPFolder
import io.github.thelony97.orbit.core.domain.MailBundle
import io.github.thelony97.orbit.core.domain.Matcher
import io.github.thelony97.orbit.core.model.MailRef
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.domain.zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.RecipientStringTerm
import javax.mail.search.SearchTerm

/**
 * E-Mail v2 (PLAN 3.9): headers only, into the Verlauf. Per account and folder UIDVALIDITY + last
 * UID, so each run fetches only what is new; a contact address not searched yet gets one search
 * over the last 12 months. One connection per account and run, never two at once. The mail text
 * is never stored – [MailClient] loads it on tap. The sync state is device state, not in backups.
 */
object MailSync {
    private const val PERIODIC = "orbit-mail"
    private const val NOW = "orbit-mail-now"
    private const val STATE = "mail-sync.json"
    private const val SOON_MS = 10 * 60 * 1000L
    private const val FOLDERS_MS = 24 * 60 * 60 * 1000L
    private const val BATCH = 12
    private const val PER_SEARCH = 400

    @Serializable
    data class FolderState(val uidValidity: Long, val lastUid: Long)

    @Serializable
    data class State(
        val folders: Map<String, FolderState> = emptyMap(),
        /** "<account>|<address or @domain>" searched over the last 12 months. */
        val searched: Set<String> = emptySet(),
        val folderNames: Map<String, List<String>> = emptyMap(),
        val foldersListedAt: Map<String, Long> = emptyMap(),
        val lastRunAt: Long = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val gate = Mutex()
    private val _lastRun = MutableStateFlow(0L)
    /** For „zuletzt abgeglichen vor 2 Std.“. */
    val lastRun: StateFlow<Long> = _lastRun
    private val _running = MutableStateFlow(false)
    /** While a run fetches (the Wellen-Indikator on the Verlauf header). */
    val running: StateFlow<Boolean> = _running

    /** The last finished run, also from an earlier process. */
    fun lastRunAt(ctx: Context): Long = maxOf(_lastRun.value, load(ctx).lastRunAt)

    private fun file(ctx: Context) = File(ctx.filesDir, STATE)

    private fun load(ctx: Context): State =
        runCatching { json.decodeFromString(State.serializer(), file(ctx).readText()) }.getOrNull() ?: State()

    private fun save(ctx: Context, s: State) {
        val tmp = File(ctx.filesDir, "$STATE.tmp")
        tmp.writeText(json.encodeToString(State.serializer(), s))
        if (!tmp.renameTo(file(ctx))) { tmp.copyTo(file(ctx), overwrite = true); tmp.delete() }
    }

    fun schedulePeriodic(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<MailSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Opening a contact (PLAN 3.9): a run behind the page, unless one just happened – an address
     * of [addresses] not searched yet (new address, new contact) always gets its run.
     */
    fun requestSoon(ctx: Context, addresses: List<String> = emptyList()) {
        val accounts = MailAccounts.accounts.value
        if (accounts.isEmpty()) return
        val state = load(ctx)
        val unsearched = addresses.map { it.trim().lowercase() }.filter { it.contains('@') }
            .any { a -> accounts.any { "${it.id}|$a" !in state.searched } }
        if (!unsearched && System.currentTimeMillis() - maxOf(_lastRun.value, state.lastRunAt) < SOON_MS) return
        val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    /** Returns false if an account failed (the worker then retries later with a growing pause). */
    suspend fun run(ctx: Context): Boolean = gate.withLock {
        _running.value = true
        try { runLocked(ctx) } finally { _running.value = false }
    }

    private suspend fun runLocked(ctx: Context): Boolean =
        withContext(Dispatchers.IO) {
            val accounts = MailAccounts.accounts.value
            if (accounts.isEmpty()) return@withContext true
            var state = load(ctx)
            val data = OrbitRepository.snapshot()
            val iso = Phones.countryIso(ctx)
            val matcher = Matcher(data.contacts) { Phones.normalize(it, iso) }
            val match = { address: String -> matcher.matchMail(address)?.let { MailBundle.Owner(it.contactId, it.memberId) } }
            val own = accounts.flatMap { listOf(it.email, it.username) }.map { it.trim().lowercase() }.filter { it.contains('@') }.toSet()
            // What to look for once: every address (and org domain) Orbit knows.
            val keys = data.contacts.filter { it.deletedAt == null && !it.isMe }.flatMap { c ->
                c.emails.map { it.value } + c.members.flatMap { m -> m.emails.map { it.value } } + c.emailDomains.map { "@" + it.trim().removePrefix("@") }
            }.map { it.trim().lowercase() }.filter { it.contains('@') && it.length > 3 }.toSet()

            val headers = ArrayList<MailBundle.Header>()
            var ok = true
            for (a in accounts) {
                val result = runCatching {
                    val (fetched, next) = syncAccount(a, state, keys)
                    headers += fetched
                    next
                }
                MailAccounts.setError(a.id, result.exceptionOrNull()?.let { MailClient.describe(it) })
                result.getOrNull()?.let { state = it } ?: run { ok = false }
            }

            // Into the Verlauf: per contact the new/changed bundled entries.
            val refs = headers.flatMap { MailBundle.owners(it, own, match) }.groupBy({ it.first.contactId }, { it.first.memberId to it.second })
            if (refs.isNotEmpty()) {
                OrbitRepository.update { d ->
                    d.copy(contacts = d.contacts.map { c ->
                        val mine = refs[c.id] ?: return@map c
                        val changed = MailBundle.merge(c, mine, zone(), ::newId)
                        if (changed.isEmpty()) c
                        else {
                            val ids = changed.map { it.id }.toSet()
                            c.copy(entries = (c.entries.filterNot { it.id in ids } + changed).sortedByDescending { it.at })
                        }
                    })
                }
            }
            val now = System.currentTimeMillis()
            state = state.copy(lastRunAt = now)
            save(ctx, state)
            _lastRun.value = now
            ok
        }

    private fun syncAccount(a: MailAccount, state0: State, keys: Set<String>): Pair<List<MailBundle.Header>, State> {
        var state = state0
        val password = MailAccounts.password(a) ?: error("Passwort nicht mehr lesbar – bitte das Konto neu verbinden.")
        val store = MailClient.open(a.host, a.port, a.username, password)
        val out = ArrayList<MailBundle.Header>()
        try {
            val names = state.folderNames[a.id]?.takeIf { System.currentTimeMillis() - (state.foldersListedAt[a.id] ?: 0) < FOLDERS_MS }
                ?: MailClient.syncFolders(store).also {
                    state = state.copy(folderNames = state.folderNames + (a.id to it), foldersListedAt = state.foldersListedAt + (a.id to System.currentTimeMillis()))
                }
            var resetSearch = false
            val since = Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000)
            val pending = keys.filter { "${a.id}|$it" !in state.searched }
            for (name in names) {
                val folder = store.getFolder(name) as? IMAPFolder ?: continue
                runCatching { folder.open(Folder.READ_ONLY) }.getOrElse { continue }
                try {
                    val validity = folder.uidValidity
                    val key = "${a.id}|$name"
                    val st = state.folders[key]
                    if (st == null || st.uidValidity != validity) {
                        // First time (or the server renumbered): start at the end, history comes from the searches.
                        state = state.copy(folders = state.folders + (key to FolderState(validity, (folder.uidNext - 1).coerceAtLeast(0))))
                        if (st != null) resetSearch = true
                    } else {
                        val fresh = folder.getMessagesByUID(st.lastUid + 1, UIDFolder.LASTUID).filter { folder.getUID(it) > st.lastUid }
                        out += headersOf(a, folder, validity, fresh.toTypedArray())
                        val top = fresh.maxOfOrNull { folder.getUID(it) } ?: st.lastUid
                        state = state.copy(folders = state.folders + (key to st.copy(lastUid = maxOf(st.lastUid, top))))
                    }
                    // New addresses: one search over 12 months, in batches.
                    pending.chunked(BATCH).forEach { batch ->
                        val terms: List<SearchTerm> = batch.flatMap { k ->
                            listOf(FromStringTerm(k), RecipientStringTerm(Message.RecipientType.TO, k), RecipientStringTerm(Message.RecipientType.CC, k))
                        }
                        val term = AndTerm(ReceivedDateTerm(ComparisonTerm.GE, since), OrTerm(terms.toTypedArray()))
                        val found = folder.search(term).sortedByDescending { it.messageNumber }.take(PER_SEARCH)
                        out += headersOf(a, folder, validity, found.toTypedArray())
                    }
                } finally {
                    runCatching { folder.close(false) }
                }
            }
            state = if (resetSearch) state.copy(searched = state.searched.filterNot { it.startsWith("${a.id}|") }.toSet())
            else state.copy(searched = state.searched + pending.map { "${a.id}|$it" })
        } finally {
            runCatching { store.close() }
        }
        return out to state
    }

    private fun headersOf(a: MailAccount, folder: IMAPFolder, validity: Long, messages: Array<Message>): List<MailBundle.Header> {
        if (messages.isEmpty()) return emptyList()
        val fp = FetchProfile()
        fp.add(FetchProfile.Item.ENVELOPE)
        fp.add(UIDFolder.FetchProfileItem.UID)
        folder.fetch(messages, fp)
        return messages.mapNotNull { m ->
            runCatching {
                val from = (m.from?.firstOrNull() as? InternetAddress)?.address.orEmpty()
                val to = listOfNotNull(m.getRecipients(Message.RecipientType.TO), m.getRecipients(Message.RecipientType.CC))
                    .flatMap { it.toList() }.mapNotNull { (it as? InternetAddress)?.address }
                MailBundle.Header(
                    account = a.id, folder = folder.fullName, uidValidity = validity, uid = folder.getUID(m),
                    messageId = (m as? MimeMessage)?.messageID, at = (m.receivedDate ?: m.sentDate)?.time ?: return@runCatching null,
                    from = from, to = to, subject = m.subject.orEmpty(),
                )
            }.getOrNull()
        }
    }

    /** The mail of a ref, for MailClient.body. */
    fun header(r: MailRef): MailHeader = MailHeader(r.account, "", r.folder, r.uid, r.messageId, r.address, r.address, r.subject, r.at)
}

class MailSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        OrbitRepository.init(applicationContext)
        OrbitRepository.ensureLoaded()
        MailAccounts.init(applicationContext)
        return if (MailSync.run(applicationContext)) Result.success() else Result.retry()
    }
}
