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
import java.io.File
import java.io.FileOutputStream

/**
 * Single source of truth. All data lives in one JSON document in app storage,
 * held in memory as a [StateFlow] and written back atomically after changes.
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
        scope.launch { ensureLoaded() }
        scope.launch {
            for (signal in saveSignal) {
                delay(250)
                runCatching { persist() }.onFailure { Log.e(TAG, "save failed", it) }
            }
        }
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
        _data.value = newData
        persist()
    }

    private fun readFromDisk(): OrbitData {
        val file = File(appContext.filesDir, FILE)
        if (!file.exists()) return Defaults.fresh()
        return try {
            migrate(json.decodeFromString(OrbitData.serializer(), file.readText()))
        } catch (e: Exception) {
            Log.e(TAG, "could not read $FILE, keeping a copy", e)
            file.copyTo(File(appContext.filesDir, "orbit-broken-${System.currentTimeMillis()}.json"), overwrite = true)
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
        return result
    }

    private suspend fun persist() {
        val written = writeMutex.withLock {
            val snapshot = _data.value ?: return@withLock false
            withContext(Dispatchers.IO) {
                val target = File(appContext.filesDir, FILE)
                val tmp = File(appContext.filesDir, "$FILE.tmp")
                FileOutputStream(tmp).use { out ->
                    out.write(json.encodeToString(OrbitData.serializer(), snapshot).toByteArray())
                    out.fd.sync()
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
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
