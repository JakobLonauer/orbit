package io.github.thelony97.orbit.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import io.github.thelony97.orbit.store.CallerIndexFile
import io.github.thelony97.orbit.store.HistoryDb
import io.github.thelony97.orbit.store.Store
import java.io.File

/**
 * Single source of truth, held in memory as a [StateFlow]. Storage v2 (ADR 0003): the JSON
 * core is written atomically, Verlauf and Ablage go into SQLite as differences ([Store]).
 * The first start after 1.0.7 keeps a copy of the old file and converts it ([StoreUpgrade]).
 */
object OrbitRepository {

    private const val TAG = "OrbitRepository"
    private const val FILE = "orbit.json"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var appContext: Context
    private lateinit var store: Store
    lateinit var history: HistoryDb
        private set

    /** What the last save wrote; the next save stores only the difference. */
    private var lastSaved: OrbitData? = null

    /**
     * Set when stored data exists but could not be read. Saving and backups are then blocked so
     * the unreadable file (and the 1.0.7 copy) is never overwritten by an empty state; the UI
     * shows an error screen instead of onboarding.
     */
    private val _loadError = MutableStateFlow<Throwable?>(null)
    val loadError: StateFlow<Throwable?> = _loadError.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()
    private val writeMutex = Mutex()
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)

    private val _data = MutableStateFlow<OrbitData?>(null)
    /** `null` until the file has been loaded. */
    val data: StateFlow<OrbitData?> = _data.asStateFlow()

    /** Called after every successful write (used to refresh the widget). */
    var onSaved: (suspend (Context) -> Unit)? = null

    val photoDir: File get() = File(appContext.filesDir, "photos").apply { mkdirs() }

    fun photoFile(name: String): File = File(photoDir, name)

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        history = HistoryDb(appContext)
        store = Store(File(appContext.filesDir, FILE), history, ::newId)
        scope.launch {
            ensureLoaded()
            // Refresh the caller index once per start, so an update's new fields (zuletzt,
            // Themen) are there before the next save. Same lock as saving: one writer at a time,
            // always from the current state.
            writeMutex.withLock {
                val current = _data.value
                if (_loadError.value == null && current != null) {
                    runCatching { CallerIndexFile.write(appContext, current) }.onFailure { Log.w(TAG, "caller index", it) }
                }
            }
        }
        scope.launch {
            for (signal in saveSignal) {
                delay(250)
                runCatching { persist() }.onFailure { Log.e(TAG, "save failed", it) }
            }
        }
    }

    /** Tests only: forget the in-memory state so the next [init] starts like a fresh process. */
    @androidx.annotation.VisibleForTesting
    internal fun resetForTest(context: Context) {
        if (::history.isInitialized) runCatching { history.close() }
        _data.value = null
        _loadError.value = null
        lastSaved = null
        appContext = context.applicationContext
        history = HistoryDb(appContext)
        store = Store(File(appContext.filesDir, FILE), history, ::newId)
    }

    suspend fun ensureLoaded(): OrbitData = loadMutex.withLock {
        _data.value ?: withContext(Dispatchers.IO) { readFromDisk() }.also { _data.value = it }
    }

    /** Current state, loading it first if necessary. */
    suspend fun snapshot(): OrbitData = _data.value ?: ensureLoaded()

    suspend fun update(transform: (OrbitData) -> OrbitData) {
        ensureLoaded()
        _data.update { current -> migrate(transform(current ?: Defaults.fresh())) }
        saveSignal.trySend(Unit)
    }

    /** Fire-and-forget variant for UI callbacks. */
    fun edit(transform: (OrbitData) -> OrbitData) {
        scope.launch { update(transform) }
    }

    fun editContact(id: String, transform: (Contact) -> Contact) = edit { d ->
        d.copy(contacts = d.contacts.map { if (it.id == id) transform(it).copy(updatedAt = now()) else it })
    }

    fun editSettings(transform: (Settings) -> Settings) = edit { d -> d.copy(settings = transform(d.settings)) }

    /** Writes immediately. Call at the end of workers / receivers. */
    suspend fun flush() {
        persist()
    }

    /** Replaces everything, e.g. after restoring a backup. */
    suspend fun replaceAll(newData: OrbitData) {
        ensureLoaded()
        // Restoring a backup is an explicit decision and may replace an unreadable state.
        _loadError.value = null
        _data.value = newData
        persist()
    }

    private fun readFromDisk(): OrbitData {
        val file = store.file
        return try {
            val loaded = store.load()
            lastSaved = loaded
            loaded?.let(::migrate) ?: Defaults.fresh()
        } catch (e: Throwable) {
            Log.e(TAG, "could not read $FILE, keeping a copy", e)
            (appContext as? io.github.thelony97.orbit.OrbitApp)?.errorLog?.log("Daten laden", e)
            if (file.exists()) {
                _loadError.value = e
                runCatching { file.copyTo(File(appContext.filesDir, "orbit-broken-${System.currentTimeMillis()}.json"), overwrite = true) }
            }
            Defaults.fresh()
        }
    }

    fun migrate(d: OrbitData): OrbitData {
        var result = d
        // Make sure the built-in entry types always exist.
        val missing = Defaults.entryTypes.filter { def -> result.entryTypes.none { it.id == def.id } }
        if (missing.isNotEmpty()) result = result.copy(entryTypes = missing + result.entryTypes)
        // Exactly one "Ich" entry that is never in the trash.
        val mes = result.contacts.filter { it.isMe }
        result = when {
            mes.isEmpty() -> result.copy(contacts = listOf(Defaults.me()) + result.contacts)
            mes.size > 1 -> {
                val keep = mes.first().id
                result.copy(contacts = result.contacts.map { if (it.isMe && it.id != keep) it.copy(isMe = false) else it })
            }
            else -> result
        }
        if (result.contacts.any { it.isMe && it.deletedAt != null }) {
            result = result.copy(contacts = result.contacts.map { if (it.isMe) it.copy(deletedAt = null) else it })
        }
        // Sorting by due date no longer exists (PLAN 3.0); idempotent.
        if (result.settings.listSort == "due") {
            result = result.copy(settings = result.settings.copy(listSort = "name", listSortReversed = false))
        }
        return result
    }

    private suspend fun persist() {
        val written = writeMutex.withLock {
            val snapshot = _data.value ?: return@withLock false
            if (snapshot === lastSaved) return@withLock false
            if (_loadError.value != null) {
                Log.e(TAG, "not saving: stored data could not be read")
                return@withLock false
            }
            withContext(Dispatchers.IO) {
                store.save(lastSaved, snapshot)
                // Small side file for the call screen; never allowed to break a save.
                runCatching { CallerIndexFile.write(appContext, snapshot) }.onFailure { Log.w(TAG, "caller index", it) }
            }
            lastSaved = snapshot
            true
        }
        val callback = onSaved
        if (written && callback != null) {
            try {
                callback(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "onSaved failed", e)
            }
        }
    }
}

fun now(): Long = System.currentTimeMillis()
