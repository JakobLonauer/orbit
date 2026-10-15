package io.github.thelony97.orbit.actions

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.thelony97.orbit.core.domain.VCard
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.security.AppLock
import java.io.File

/** Contact as vCard file or QR code (PLAN 3.3). Business-card fields only, see [VCard]. */
object ShareContact {

    /** [withFields]: own fields go along (only when picked, PLAN 9 A12). */
    fun vcard(ctx: Context, c: Contact, withFields: Boolean = false) = vcards(ctx, listOf(c), withFields)

    /** Several contacts in one .vcf (multi-select export, PLAN 3.2). */
    fun vcards(ctx: Context, contacts: List<Contact>, withFields: Boolean = false) {
        if (contacts.isEmpty()) return
        try {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            // Old shared cards are cleaned up; recent ones stay until the receiving app has read them.
            val cutoff = System.currentTimeMillis() - 10 * 60_000L
            dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
            val name = if (contacts.size == 1) VCard.fileName(contacts.first()) else "Orbit-${contacts.size}-Kontakte.vcf"
            val file = File(dir, name).apply { writeText(contacts.joinToString("") { VCard.of(it, withFields) }) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/x-vcard")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            AppLock.allowExternal()
            ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            io.github.thelony97.orbit.ui.components.Undo.note(ctx, "Teilen nicht möglich", ok = false)
        }
    }

    /**
     * QR code of the vCard as a bitmap, one pixel per module plus a quiet zone; scale it with
     * nearest-neighbour filtering. `null` if the card is too large for a QR code.
     */
    fun qr(c: Contact, withFields: Boolean = false): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        )
        val m = QRCodeWriter().encode(VCard.of(c, withFields), BarcodeFormat.QR_CODE, 0, 0, hints)
        val pixels = IntArray(m.width * m.height) { i ->
            if (m.get(i % m.width, i / m.width)) 0xFF0B0B14.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
