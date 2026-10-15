package io.github.thelony97.orbit.actions

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.thelony97.orbit.MainActivity
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.core.domain.Shortcuts
import io.github.thelony97.orbit.core.domain.Visibility
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.domain.Logic

/**
 * App-Shortcuts (PLAN 3.14): Neuer Kontakt, Verlauf and the top favourites, updated whenever the
 * favourites change. Archived or deleted contacts drop out on the next update.
 */
object AppShortcuts {

    const val ACTION_NEW = "io.github.thelony97.orbit.action.NEW_CONTACT"
    const val ACTION_VERLAUF = "io.github.thelony97.orbit.action.VERLAUF"
    const val ACTION_LAST_CALL = "io.github.thelony97.orbit.action.LAST_CALL_NOTE"

    private const val FAVORITES = 2

    /** PLAN 9 A5: people straight in the system share sheet („Teilen“ → Anna). */
    const val SHARE_CATEGORY = "io.github.thelony97.orbit.category.SHARE_TARGET"
    private const val SHARE_PEOPLE = 4
    @Volatile private var shown: List<String>? = null
    @Volatile private var goneShown: Set<String>? = null

    /** The launcher learns which shortcut gets used (its own ranking). */
    fun used(ctx: Context, id: String) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(ctx, id) }
    }

    fun update(ctx: Context, data: OrbitData) {
        val gone = data.contacts.filter { it.deletedAt != null || it.archived }.map { it.id }.toSet()
        if (gone != goneShown) {
            goneShown = gone
            tidyPinned(ctx, data)
        }
        val favs = Shortcuts.topFavorites(data.contacts, now(), { Visibility.inLists(it, data.settings.showOrgs) }, { Logic.displayName(it, false) })
            .take(FAVORITES)
        // The people Jakob is most in touch with (last 90 days) – fixed rule, no guessing.
        val since = now() - 90L * 86_400_000L
        val sharePeople = data.contacts
            .filter { !it.isOrg && !it.isMe && it.deletedAt == null && !it.archived }
            .map { it to it.entries.count { e -> e.at >= since && e.memberId == null } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Contact, Int>> { it.second }.thenBy { Logic.displayName(it.first, false) })
            .take(SHARE_PEOPLE)
            .map { it.first }
        val key = (favs + sharePeople.map { it.copy(id = "share-" + it.id) }).map { it.id + "|" + Logic.displayName(it, false) + "|" + it.photo + "|" + it.groupIds.firstOrNull() }
        if (key == shown) return
        val main = { Intent(ctx, MainActivity::class.java) }
        val list = buildList {
            add(
                ShortcutInfoCompat.Builder(ctx, "new")
                    .setShortLabel("Neuer Kontakt")
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_shortcut_new))
                    .setIntent(main().setAction(ACTION_NEW))
                    .setRank(0)
                    .build(),
            )
            add(
                ShortcutInfoCompat.Builder(ctx, "verlauf")
                    .setShortLabel("Verlauf")
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_shortcut_verlauf))
                    .setIntent(main().setAction(ACTION_VERLAUF))
                    .setRank(1)
                    .build(),
            )
            favs.forEachIndexed { i, c ->
                add(
                    ShortcutInfoCompat.Builder(ctx, "fav-" + c.id)
                        .setShortLabel(Logic.shortName(c).take(24))
                        .setLongLabel(Logic.displayName(c, false).take(40))
                        .setIcon(IconCompat.createWithAdaptiveBitmap(icon(c, data)))
                        .setIntent(
                            main().setAction(Intent.ACTION_VIEW)
                                .setData(Uri.parse("orbit://contact/${c.id}"))
                                .putExtra(MainActivity.EXTRA_CONTACT, c.id),
                        )
                        .setRank(2 + i)
                        .build(),
                )
            }
            sharePeople.forEachIndexed { i, c ->
                val name = Logic.displayName(c, false)
                add(
                    ShortcutInfoCompat.Builder(ctx, "share-" + c.id)
                        .setShortLabel(Logic.shortName(c).take(24))
                        .setLongLabel(name.take(40))
                        .setIcon(IconCompat.createWithAdaptiveBitmap(icon(c, data)))
                        .setPerson(androidx.core.app.Person.Builder().setName(name).setKey(c.id).build())
                        .setLongLived(true)
                        .setCategories(setOf(SHARE_CATEGORY))
                        .setIntent(
                            main().setAction(Intent.ACTION_VIEW)
                                .setData(Uri.parse("orbit://contact/${c.id}"))
                                .putExtra(MainActivity.EXTRA_CONTACT, c.id),
                        )
                        // Behind the launcher's own shortcuts: these are for sharing.
                        .setRank(10 + i)
                        .build(),
                )
            }
        }
        // Never more than the launcher allows – its own shortcuts come first, sharing after.
        val max = runCatching { ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx) }.getOrDefault(4).coerceAtLeast(1)
        if (runCatching { ShortcutManagerCompat.setDynamicShortcuts(ctx, list.take(max)) }.getOrDefault(false)) shown = key
    }

    /** A favourite pinned to the home screen goes grey once the contact is archived or deleted. */
    private fun tidyPinned(ctx: Context, data: OrbitData) {
        val pinned = runCatching { ShortcutManagerCompat.getShortcuts(ctx, ShortcutManagerCompat.FLAG_MATCH_PINNED) }.getOrNull().orEmpty()
            .map { it.id }.filter { it.startsWith("fav-") }
        if (pinned.isEmpty()) return
        val byId = data.contacts.associateBy { it.id }
        val (alive, gone) = pinned.partition { id -> byId[id.removePrefix("fav-")]?.let { it.deletedAt == null && !it.archived } == true }
        runCatching {
            if (gone.isNotEmpty()) ShortcutManagerCompat.disableShortcuts(ctx, gone, "Nicht mehr in Orbit")
            if (alive.isNotEmpty()) ShortcutManagerCompat.enableShortcuts(ctx, alive.mapNotNull { id -> byId[id.removePrefix("fav-")]?.let { pinnedInfo(ctx, it, data) } })
        }
    }

    private fun pinnedInfo(ctx: Context, c: Contact, data: OrbitData): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(ctx, "fav-" + c.id)
            .setShortLabel(Logic.shortName(c).take(24))
            .setLongLabel(Logic.displayName(c, false).take(40))
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon(c, data)))
            .setIntent(
                Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse("orbit://contact/${c.id}"))
                    .putExtra(MainActivity.EXTRA_CONTACT, c.id),
            )
            .build()

    /** Adaptive 108 dp bitmap: the photo full-bleed, else the initials on the group colour. */
    private fun icon(c: Contact, data: OrbitData): Bitmap {
        val px = 216
        c.photo?.let { name ->
            val file = OrbitRepository.photoFile(name)
            val src = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
            if (src != null) {
                val side = minOf(src.width, src.height)
                val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                return Bitmap.createScaledBitmap(square, px, px, true)
            }
        }
        val color = c.groupIds.firstNotNullOfOrNull { id -> data.groups.firstOrNull { it.id == id } }?.color ?: 0xFF7986CB
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(color.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = px * 0.26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val y = px / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(Logic.initials(c), px / 2f, y, paint)
        return out
    }
}
