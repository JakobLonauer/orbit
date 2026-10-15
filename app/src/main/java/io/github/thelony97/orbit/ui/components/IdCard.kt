package io.github.thelony97.orbit.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.github.thelony97.orbit.core.designsystem.holo
import io.github.thelony97.orbit.ui.theme.Grotesk
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ausweis (PLAN 9 D4): a customer or member number as a card at full brightness – the number big,
 * underneath a Code 128 barcode (or a QR code when the number does not fit one), a holo sheen on
 * the card that moves as the phone tilts, never over the code.
 */
@Composable
fun IdCard(title: String, label: String, value: String, color: Color, onDictate: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val code by produceState<Bitmap?>(null, value) {
        this.value = withContext(Dispatchers.Default) { barcode(value) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
            onDispose { }
        }
        val calm = remember { ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
        val tilt = if (calm) null else rememberTilt()
        val drift = if (calm) null else rememberInfiniteTransition(label = "idHolo").animateFloat(
            0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "drift",
        )
        Box(modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onClose), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Space.PanelHigh)
                    .holo(phase = { drift?.value ?: 0.2f }, tiltX = { tilt?.value?.x ?: 0f }, tiltY = { tilt?.value?.y ?: 0f }, behind = true)
                    .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text, textAlign = TextAlign.Center)
                Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                Spacer(Modifier.height(18.dp))
                Text(
                    value,
                    fontFamily = Grotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (value.length > 14) 30.sp else 40.sp,
                    letterSpacing = 1.5.sp,
                    color = Space.Text,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.height(20.dp))
                // White ground for the scanner; the code is one pixel per module, scaled sharp.
                val square = code?.let { it.width == it.height } ?: false
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (square) 240.dp else 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    code?.let {
                        Image(
                            it.asImageBitmap(), "Code",
                            filterQuality = FilterQuality.None,
                            contentScale = if (square) ContentScale.Fit else ContentScale.FillBounds,
                            modifier = if (square) Modifier.size(216.dp) else Modifier.fillMaxSize(),
                        )
                    }
                }
                if (onDictate != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        GhostButton("Diktieren", onDictate, icon = Icons.Rounded.RecordVoiceOver)
                    }
                }
            }
        }
    }
}

/** Code 128 for plain numbers and letters, QR for everything else; null when nothing fits. */
internal fun barcode(value: String): Bitmap? = runCatching {
    val v = value.trim()
    val plain = v.length in 1..32 && v.all { it.code in 32..126 }
    val matrix = if (plain) {
        com.google.zxing.oned.Code128Writer().encode(v, com.google.zxing.BarcodeFormat.CODE_128, 0, 1)
    } else {
        com.google.zxing.qrcode.QRCodeWriter().encode(v, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0)
    }
    val w = matrix.width
    val h = if (plain) 1 else matrix.height
    val px = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** UTF-8 QR with medium error correction (GiroCode, WLAN); null when it does not fit. */
internal fun qrCode(text: String): Bitmap? = runCatching {
    val hints = mapOf(
        com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
        com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
        com.google.zxing.EncodeHintType.MARGIN to 1,
    )
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints)
    val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}.getOrNull()
