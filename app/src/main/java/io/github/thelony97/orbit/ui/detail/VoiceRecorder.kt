package io.github.thelony97.orbit.ui.detail

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.domain.Wave
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.delay

/**
 * Sprachnotiz (PLAN 9 E12): records while Senden is held (note mode, empty line). The file lies
 * next to the photos and travels with the backup; the waveform is sampled while recording.
 */
class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var file: String? = null
    private var startedAt = 0L
    val amps = ArrayList<Int>()

    fun start(): Boolean = runCatching {
        val name = "v-${newId()}.m4a"
        val r = MediaRecorder(ctx).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(BITRATE)
            setAudioSamplingRate(SAMPLE_RATE)
            setOutputFile(OrbitRepository.photoFile(name).path)
            prepare()
            start()
        }
        recorder = r
        file = name
        startedAt = System.currentTimeMillis()
        amps.clear()
        true
    }.getOrDefault(false)

    fun sample() {
        runCatching { recorder?.maxAmplitude }.getOrNull()?.let { amps += it }
    }

    /** Stops; the entry goes into [contactId]'s thread unless it was shorter than a second. */
    fun stop(contactId: String) {
        val r = recorder ?: return
        recorder = null
        val name = file ?: return
        val ms = System.currentTimeMillis() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || ms < MIN_MS) {
            OrbitRepository.photoFile(name).delete()
            return
        }
        val e = HistoryEntry(
            newId(), contactId, BuiltinTypes.NOTE, startedAt, note = "Sprachnotiz",
            durationSec = ms / 1_000, audio = name, wave = Wave.encode(amps),
        )
        OrbitRepository.edit { d -> Mutations.addEntry(d, contactId, e) }
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        file?.let { OrbitRepository.photoFile(it).delete() }
    }

    companion object {
        const val SAMPLE_MS = 100L
        private const val MIN_MS = 1_000L
        private const val BITRATE = 64_000
        private const val SAMPLE_RATE = 44_100
    }
}

/** While held: the live waveform grows from the amplitudes (Moment). */
@Composable
internal fun RecordingLine(rec: VoiceRecorder, modifier: Modifier = Modifier) {
    var tick by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rec) {
        while (true) {
            rec.sample()
            tick++
            delay(VoiceRecorder.SAMPLE_MS)
        }
    }
    val bars = remember(tick) { Wave.decode(Wave.encode(rec.amps.toList())) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Space.Coral))
        Spacer(Modifier.width(8.dp))
        Text(Fmt.duration((rec.amps.size * VoiceRecorder.SAMPLE_MS / 1_000)), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = Space.Text)
        Spacer(Modifier.width(8.dp))
        WaveBars(bars, 1f, Space.Coral, Modifier.weight(1f).height(24.dp))
    }
}

@Composable
private fun WaveBars(bars: List<Float>, progress: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (bars.isEmpty()) return@Canvas
        val step = size.width / Wave.MAX
        val w = (step * 0.6f).coerceAtLeast(1.5f)
        bars.forEachIndexed { i, v ->
            val h = (size.height * (0.15f + 0.85f * v))
            val played = (i + 0.5f) / bars.size <= progress
            drawRoundRect(
                if (played) color else color.copy(alpha = 0.35f),
                topLeft = Offset(i * step, (size.height - h) / 2), size = Size(w, h), cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

/** A Sprachnotiz in the thread: play/pause, the waveform fills as it plays. */
@Composable
internal fun VoicePlayer(entry: HistoryEntry, modifier: Modifier = Modifier) {
    val file = entry.audio ?: return
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember(file) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(file) { onDispose { player.value?.release(); player.value = null } }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player.value ?: break
            progress = runCatching { p.currentPosition.toFloat() / p.duration.coerceAtLeast(1) }.getOrDefault(0f)
            if (!p.isPlaying) {
                playing = false
                progress = 0f
            }
            delay(PROGRESS_MS)
        }
    }
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Space.Violet.copy(alpha = 0.2f)).clickable {
                val p = player.value ?: runCatching {
                    MediaPlayer().apply { setDataSource(OrbitRepository.photoFile(file).path); prepare() }
                }.getOrNull()?.also { player.value = it } ?: return@clickable
                if (playing) p.pause() else p.start()
                playing = !playing
            },
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Abspielen", tint = Space.Violet, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(8.dp))
        WaveBars(Wave.decode(entry.wave), progress, Space.Violet, Modifier.weight(1f).height(22.dp))
    }
}

private const val PROGRESS_MS = 80L
