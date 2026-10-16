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
import io.github.thelony97.orbit.core.domain.Bounces
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Store
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
 * UID, so each run fetches only what is new; an address (or org domain) not searched yet in a
 * folder gets one search over the last 12 months. One connection per account and run, and never
 * two at once per account ([lockFor] is shared with loading a mail's text). Work is committed in
 * small steps – entries saved first, then the state – and a run stops after a time budget and
 * continues in the next one, so a big mailbox never starts over. The text is never stored. The
 * sync state is device state, not in backups.
 */
object MailSync {
    private const val PERIODIC = "orbit-mail"
    private const val NOW = "orbit-mail-now"
    private const val STATE = "mail-sync.json"
    private const val SOON_MS = 10 * 60 * 1000L
    private const val FOLDERS_MS = 24 * 60 * 60 * 1000L
    private const val BATCH = 10
    private const val CHUNK = 150
    private const val ACCOUNT_BUDGET_MS = 90_000L
    /** A bounce's report sits near the top; the returned original is not needed. */
    private const val BOUNCE_BYTES = 64 * 1024

    @Serializable
    data class FolderState(val uidValidity: Long, val lastUid: Long)

    @Serializable
    data class State(
        val folders: Map<String, FolderState> = emptyMap(),
        /** "<account>|<folder>|<address or @domain>" searched over the last 12 months. */
        val searchedIn: Set<String> = emptySet(),
        val folderNames: Map<String, List<String>> = emptyMap(),
        val foldersListedAt: Map<String, Long> = emptyMap(),
        /** The last run in which at least one account went through. */
        val lastRunAt: Long = 0,
    )

    enum class Outcome { DONE, MORE, RETRY }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val gate = Mutex()
    private val accountLocks = ConcurrentHashMap<String, Mutex>()
    @Volatile private var cached: State? = null
    private val _lastRun = MutableStateFlow(0L)
    /** For „zuletzt abgeglichen vor 2 Std.“. */
    val lastRun: StateFlow<Long> = _lastRun
    private val _running = MutableStateFlow(false)
    /** While a run fetches (the Wellen-Indikator on the Verlauf header). */
    val running: StateFlow<Boolean> = _running

    /** One connection per account at a time: sync and „Mail öffnen“ take turns. */
    fun lockFor(accountId: String): Mutex = accountLocks.getOrPut(accountId) { Mutex() }

    /** The last run that got through, also from an earlier process. */
    fun lastRunAt(ctx: Context): Long = maxOf(_lastRun.value, load(ctx).lastRunAt)

    private fun file(ctx: Context) = File(ctx.filesDir, STATE)

    private fun load(ctx: Context): State = cached
        ?: (runCatching { json.decodeFromString(State.serializer(), file(ctx).readText()) }.getOrNull() ?: State()).also { cached = it }

    private fun save(ctx: Context, s: State) {
        cached = s
        val tmp = File(ctx.filesDir, "$STATE.tmp")
        tmp.writeText(json.encodeToString(State.serializer(), s))
        if (!tmp.renameTo(file(ctx))) { tmp.copyTo(file(ctx), overwrite = true); tmp.delete() }
    }

    /** After a restore the Verlauf is older than the sync state: start over (duplicates are skipped). */
    fun reset(ctx: Context) {
        cached = State()
        file(ctx).delete()
    }

    fun schedulePeriodic(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<MailSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Without accounts nothing runs in the background at all. */
    fun cancelPeriodic(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(ctx).cancelUniqueWork(NOW)
    }

    /**
     * Opening a contact (PLAN 3.9): a run behind the page, unless one just happened – an address
     * of [addresses] not searched yet (new address, new contact) always gets its run, as does
     * [force] (a new account, a run that ran out of time).
     */
    fun requestSoon(ctx: Context, addresses: List<String> = emptyList(), force: Boolean = false) {
        val accounts = MailAccounts.accounts.value
        if (accounts.isEmpty()) return
        val state = load(ctx)
        val unsearched = addresses.map { key(it) }.filter { it.contains('@') }.any { a ->
            accounts.any { acc -> state.folderNames[acc.id].orEmpty().ifEmpty { listOf("INBOX") }.any { "${acc.id}|$it|$a" !in state.searchedIn } }
        }
        if (!force && !unsearched && System.currentTimeMillis() - maxOf(_lastRun.value, state.lastRunAt) < SOON_MS) return
        val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, if (force) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    /** Address or org domain as searched: lower case, „*.“ and a leading „@“ normalised. */
    private fun key(raw: String): String {
        val t = raw.trim().lowercase()
        return if (t.startsWith("@") || t.startsWith("*.") || !t.contains('@')) "@" + t.removePrefix("@").removePrefix("*.") else t
    }

    suspend fun run(ctx: Context): Outcome = gate.withLock {
        _running.value = true
        try { runLocked(ctx) } finally { _running.value = false }
    }

    private suspend fun runLocked(ctx: Context): Outcome = withContext(Dispatchers.IO) {
        val accounts = MailAccounts.accounts.value
        if (accounts.isEmpty()) return@withContext Outcome.DONE
        val data = OrbitRepository.snapshot()
        val iso = Phones.countryIso(ctx)
        // Mails of contacts in the trash still land with them (silently, like the archive): a
        // restore from the trash then lacks nothing.
        val matcher = Matcher(data.contacts.map { if (it.deletedAt != null) it.copy(deletedAt = null) else it }) { Phones.normalize(it, iso) }
        val match = { address: String -> matcher.matchMail(address)?.let { MailBundle.Owner(it.contactId, it.memberId) } }
        val own = accounts.flatMap { listOf(it.email, it.username) }.map { it.trim().lowercase() }.filter { it.contains('@') }.toSet()
        val keys = data.contacts.filter { it.deletedAt == null && !it.isMe }.flatMap { c ->
            c.emails.map { it.value } + c.members.flatMap { m -> m.emails.map { it.value } } + c.emailDomains.map { "@$it" }
        }.map(::key).filter { it.contains('@') && it.length > 3 && it !in own }.toSet()

        var anyOk = false
        var more = false
        var retry = false
        for (a in accounts) {
            val result = runCatching {
                lockFor(a.id).withLock { syncAccount(ctx, a, keys, own, match, System.currentTimeMillis() + ACCOUNT_BUDGET_MS) }
            }
            MailAccounts.setError(a.id, result.exceptionOrNull()?.let { MailClient.describe(it) })
            result.onSuccess { anyOk = true; if (it == Outcome.MORE) more = true }
            // A wrong password does not get better by retrying; the account shows the error.
            result.onFailure { if (!permanent(it)) retry = true }
        }
        if (anyOk) {
            val now = System.currentTimeMillis()
            save(ctx, load(ctx).copy(lastRunAt = now))
            _lastRun.value = now
        }
        when {
            more -> Outcome.MORE
            retry -> Outcome.RETRY
            else -> Outcome.DONE
        }
    }

    private fun permanent(e: Throwable): Boolean {
        var c: Throwable? = e
        while (c != null) {
            if (c is AuthenticationFailedException || c is IllegalStateException) return true
            c = c.cause
        }
        return false
    }

    /**
     * Entries first (saved to disk), then the sync state – never the other way round. New mails
     * ([fresh]) also keep the dead addresses current (PLAN 9 E14): [failed] from bounces marks an
     * address, a newer mail from it takes the mark away.
     */
    private suspend fun commit(
        ctx: Context,
        headers: List<MailBundle.Header>,
        own: Set<String>,
        match: (String) -> MailBundle.Owner?,
        fresh: Boolean = false,
        failed: Map<String, Long> = emptyMap(),
        change: (State) -> State,
    ) {
        val refs = headers.flatMap { MailBundle.owners(it, own, match) }.groupBy({ it.first.contactId }, { it.first.memberId to it.second })
        val heard = if (!fresh) emptyMap()
        else headers.filter { it.from.contains('@') && !Bounces.isBounce(it.from, it.subject) }
            .groupBy({ it.from.trim().lowercase() }, { it.at }).mapValues { (_, at) -> at.max() }.filterKeys { it !in own }
        // Only write when something changes: most new mails touch no dead address.
        val deadMoves = (failed.isNotEmpty() || heard.isNotEmpty()) &&
            OrbitRepository.snapshot().contacts.any { Bounces.apply(it, failed, heard) !== it }
        if (refs.isNotEmpty() || deadMoves) {
            OrbitRepository.update { d ->
                d.copy(contacts = d.contacts.map { c0 ->
                    val c = if (deadMoves) Bounces.apply(c0, failed, heard) else c0
                    val mine = refs[c.id] ?: return@map c
                    val changed = MailBundle.merge(c, mine, zone(), ::newId)
                    if (changed.isEmpty()) c
                    else {
                        val ids = changed.map { it.id }.toSet()
                        c.copy(entries = (c.entries.filterNot { it.id in ids } + changed).sortedByDescending { it.at })
                    }
                })
            }
            OrbitRepository.flush()
        }
        save(ctx, change(load(ctx)))
    }

    /**
     * Bounces among new mails (PLAN 9 E14, fixed rules in [Bounces]): failed address → time of the
     * bounce. Only the first [BOUNCE_BYTES] of a bounce are read; a mail that cannot be read counts as none.
     */
    private fun bouncesOf(folder: IMAPFolder, chunk: List<Message>, headers: List<MailBundle.Header>): Map<String, Long> {
        val candidates = headers.filter { Bounces.isBounce(it.from, it.subject) }
        if (candidates.isEmpty()) return emptyMap()
        val byUid = chunk.associateBy { folder.getUID(it) }
        val out = HashMap<String, Long>()
        for (h in candidates) {
            val m = byUid[h.uid] as? MimeMessage ?: continue
            // A broken connection throws on: the chunk is not committed and comes again next run.
            // A malformed bounce counts as none.
            val read = runCatching { m.rawInputStream.use { String(it.readNBytes(BOUNCE_BYTES), Charsets.UTF_8) } }
            val err = read.exceptionOrNull()
            if (err != null && !malformed(err)) throw err
            val text = read.getOrNull() ?: continue
            for (a in Bounces.failedRecipients(text)) out[a] = maxOf(out[a] ?: 0L, h.at)
        }
        return out
    }

    private suspend fun syncAccount(
        ctx: Context,
        a: MailAccount,
        keys: Set<String>,
        own: Set<String>,
        match: (String) -> MailBundle.Owner?,
        deadline: Long,
    ): Outcome {
        val password = MailAccounts.password(a) ?: throw IllegalStateException("Passwort nicht mehr lesbar – bitte das Konto neu verbinden.")
        val store = MailClient.open(a.host, a.port, a.username, password)
        try {
            val st0 = load(ctx)
            val names = st0.folderNames[a.id]?.takeIf { System.currentTimeMillis() - (st0.foldersListedAt[a.id] ?: 0) < FOLDERS_MS }
                ?: MailClient.syncFolders(store)?.also { listed ->
                    // Only a listing that worked is kept for a day; otherwise the inbox, and next time again.
                    save(ctx, load(ctx).let { s -> s.copy(folderNames = s.folderNames + (a.id to listed), foldersListedAt = s.foldersListedAt + (a.id to System.currentTimeMillis())) })
                }
                ?: listOf("INBOX")
            val since = Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000)
            for (name in names) {
                val folder = store.getFolder(name) as? IMAPFolder ?: continue
                if (runCatching { folder.open(Folder.READ_ONLY) }.isFailure) continue
                try {
                    val validity = folder.uidValidity
                    val fkey = "${a.id}|$name"
                    val st = load(ctx).folders[fkey]
                    if (st == null || st.uidValidity != validity) {
                        // First time, or the server renumbered: start at the end; the history
                        // comes from the searches below (again, for every key of this folder).
                        val start = FolderState(validity, (folder.uidNext - 1).coerceAtLeast(0))
                        commit(ctx, emptyList(), own, match) { s ->
                            s.copy(folders = s.folders + (fkey to start), searchedIn = s.searchedIn.filterNot { it.startsWith("$fkey|") }.toSet())
                        }
                    } else {
                        val fresh = folder.getMessagesByUID(st.lastUid + 1, UIDFolder.LASTUID)
                            .filter { folder.getUID(it) > st.lastUid }.sortedBy { folder.getUID(it) }
                        for (chunk in fresh.chunked(CHUNK)) {
                            val headers = headersOf(a, folder, validity, chunk.toTypedArray())
                            val top = chunk.maxOf { folder.getUID(it) }
                            val failed = bouncesOf(folder, chunk, headers)
                            commit(ctx, headers, own, match, fresh = true, failed = failed) { s ->
                                val cur = s.folders[fkey] ?: st
                                s.copy(folders = s.folders + (fkey to cur.copy(lastUid = maxOf(cur.lastUid, top))))
                            }
                            if (System.currentTimeMillis() > deadline) return Outcome.MORE
                        }
                    }
                    // Addresses not searched in this folder yet: once, over 12 months, in batches.
                    val pending = keys.filter { "$fkey|$it" !in load(ctx).searchedIn }.sorted()
                    for (batch in pending.chunked(BATCH)) {
                        if (System.currentTimeMillis() > deadline) return Outcome.MORE
                        val terms: List<SearchTerm> = batch.flatMap { k ->
                            listOf(FromStringTerm(k), RecipientStringTerm(Message.RecipientType.TO, k), RecipientStringTerm(Message.RecipientType.CC, k))
                        }
                        val found = folder.search(AndTerm(ReceivedDateTerm(ComparisonTerm.GE, since), OrTerm(terms.toTypedArray())))
                            .sortedByDescending { it.messageNumber }
                        for (chunk in found.chunked(CHUNK)) {
                            commit(ctx, headersOf(a, folder, validity, chunk.toTypedArray()), own, match) { it }
                            // Out of time mid-batch: the batch is not marked, it runs again next time.
                            if (System.currentTimeMillis() > deadline) return Outcome.MORE
                        }
                        commit(ctx, emptyList(), own, match) { s -> s.copy(searchedIn = s.searchedIn + batch.map { "$fkey|$it" }) }
                    }
                } finally {
                    runCatching { folder.close(false) }
                }
            }
            return Outcome.DONE
        } finally {
            runCatching { store.close() }
        }
    }

    private fun headersOf(a: MailAccount, folder: IMAPFolder, validity: Long, messages: Array<Message>): List<MailBundle.Header> {
        if (messages.isEmpty()) return emptyList()
        val fp = FetchProfile()
        fp.add(FetchProfile.Item.ENVELOPE)
        fp.add(FetchProfile.Item.FLAGS)
        fp.add(UIDFolder.FetchProfileItem.UID)
        fp.add("Message-ID")
        folder.fetch(messages, fp)
        return messages.mapNotNull { m ->
            runCatching {
                // Drafts (Gmail keeps every autosave in „Alle Nachrichten“) are no mails sent.
                if (m.isSet(Flags.Flag.DRAFT)) return@runCatching null
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

    /** A mail that cannot be parsed – not a lost connection or anything else. */
    private fun malformed(e: Throwable): Boolean =
        e is javax.mail.MessagingException && e !is javax.mail.FolderClosedException && e !is javax.mail.StoreClosedException

    /** The mail of a ref, for MailClient.body / images. */
    fun header(r: MailRef): MailHeader = MailHeader(r.account, "", r.folder, r.uid, r.messageId, r.address, r.address, r.subject, r.at, r.uidValidity)
}

class MailSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        OrbitRepository.init(applicationContext)
        MailAccounts.init(applicationContext)
        if (MailAccounts.accounts.value.isEmpty()) return Result.success()
        OrbitRepository.ensureLoaded()
        return when (MailSync.run(applicationContext)) {
            MailSync.Outcome.DONE -> Result.success()
            // Out of time with more to do: the next run follows right away.
            MailSync.Outcome.MORE -> { MailSync.requestSoon(applicationContext, force = true); Result.success() }
            MailSync.Outcome.RETRY -> Result.retry()
        }
    }
}
