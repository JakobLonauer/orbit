package io.github.thelony97.orbit.store

import android.content.Context
import io.github.thelony97.orbit.core.domain.ChangeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Änderungsprotokoll (PLAN 9 E23) on disk: the last 30 days of field changes, small and local. */
object ChangeLogFile {
    private const val FILE = "changelog.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ChangeLog.Change.serializer())
    private val _all = MutableStateFlow<List<ChangeLog.Change>>(emptyList())
    val all: StateFlow<List<ChangeLog.Change>> = _all.asStateFlow()
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun load(ctx: Context): List<ChangeLog.Change> {
        if (!loaded) {
            _all.value = runCatching { json.decodeFromString(serializer, file(ctx).readText()) }.getOrDefault(emptyList())
            loaded = true
        }
        return _all.value
    }

    @Synchronized
    fun append(ctx: Context, changes: List<ChangeLog.Change>) {
        if (changes.isEmpty()) return
        val next = ChangeLog.prune(load(ctx) + changes, System.currentTimeMillis())
        _all.value = next
        runCatching { file(ctx).writeText(json.encodeToString(serializer, next)) }
    }
}
