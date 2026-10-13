package io.github.thelony97.orbit.store

import android.content.Context
import io.github.thelony97.orbit.core.domain.CallerIndex
import io.github.thelony97.orbit.core.domain.DocJson
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.domain.Phones
import java.io.File

/**
 * Anrufer-Kurzindex (PLAN 4): rewritten after every save, read when it rings, without loading
 * the whole document. Kept small: number -> name, picture, role, note start, customer number.
 */
object CallerIndexFile {
    private const val NAME = "caller-index.json"

    fun file(ctx: Context) = File(ctx.filesDir, NAME)

    fun write(ctx: Context, doc: OrbitDoc) {
        val iso = Phones.countryIso(ctx)
        val index = CallerIndex.build(doc) { Phones.normalize(it, iso) }
        StoreUpgrade.atomicWrite(file(ctx), DocJson.json.encodeToString(CallerIndex.serializer(), index))
    }

    fun read(ctx: Context): CallerIndex? = runCatching {
        DocJson.json.decodeFromString(CallerIndex.serializer(), file(ctx).readText())
    }.getOrNull()
}
