package io.github.thelony97.orbit.ui.call

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.T9
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.components.spotlight
import io.github.thelony97.orbit.ui.theme.Grotesk
import io.github.thelony97.orbit.ui.theme.Space

/** One suggestion under the typed digits. */
data class DialRow(val hit: T9.Hit, val contact: Contact, val color: Color)

/**
 * Tastenfeld (PLAN 3.12 „Wählen“): T9 search over names and numbers, without archive and hidden
 * members. Works without the Orbit lock (calls are never blocked); wiring follows with the phone role.
 */
@Composable
fun Dialpad(
    data: OrbitData,
    idx: Index,
    onCall: (String) -> Unit,
    onClose: () -> Unit,
    initial: String = "",
    /** Long press on 1 (PLAN 3.12 „Mailbox“). */
    onVoicemail: () -> Unit = {},
) {
    var digits by rememberSaveable { mutableStateOf(initial) }
    val lastNameFirst = data.settings.sortByLastName
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val homeCc = remember { T9.callingCode(io.github.thelony97.orbit.domain.Phones.countryIso(ctx)) }
    val rows = remember(digits, data) {
        T9.search(
            data.contacts, digits,
            // Search the real name only: a nameless contact must not match „Ohne Namen“.
            name = { c -> if (io.github.thelony97.orbit.core.domain.CallerIndex.nameOf(c).isBlank()) "" else Logic.displayName(c, lastNameFirst) },
            homeCc = homeCc,
            lastAt = { c -> c.entries.maxOfOrNull { it.at } },
            limit = 4,
        ).mapNotNull { h -> data.contacts.firstOrNull { it.id == h.contactId }?.let { DialRow(h, it, contactColor(it, idx)) } }
    }
    DialpadContent(
        digits = digits,
        rows = rows,
        onKey = { digits += it },
        onDelete = { digits = digits.dropLast(1) },
        onClear = { digits = "" },
        onCall = onCall,
        onClose = onClose,
        onVoicemail = onVoicemail,
    )
}

@Composable
fun DialpadContent(
    digits: String,
    rows: List<DialRow>,
    onKey: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onCall: (String) -> Unit,
    onClose: () -> Unit,
    onVoicemail: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    SpaceBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", onClose)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Bottom) {
                rows.forEach { r -> Suggestion(r) { onCall(r.contact.phones[r.hit.phoneIndex].value) } }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Text(
                    digits,
                    fontFamily = Grotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (digits.length > 13) 28.sp else 36.sp,
                    color = Space.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (digits.isNotEmpty()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Backspace, "Löschen",
                            tint = Space.TextDim,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = { haptics.perform(Haptic.Tick); onDelete() },
                                    onLongClick = { haptics.perform(Haptic.Reject); onClear() },
                                )
                                .padding(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Keypad(onKey, onVoicemail)
            Spacer(Modifier.height(16.dp))
            val number = digits.takeIf { it.any(Char::isDigit) }
            Box(
                Modifier
                    .size(68.dp)
                    .then(if (number != null) Modifier.glow(Space.Teal, 20.dp, CircleShape, 0.5f) else Modifier)
                    .clip(CircleShape)
                    .background(if (number != null) Space.Teal else Space.PanelHigh)
                    .clickable(enabled = number != null) {
                        haptics.perform(Haptic.Confirm)
                        number?.let(onCall)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Call, "Anrufen", tint = if (number != null) Space.Void else Space.TextFaint, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val KEYS = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

@Composable
private fun Keypad(onKey: (Char) -> Unit, onVoicemail: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (d, letters) ->
                    // Long press: 0 types „+“ for international numbers, 1 calls the mailbox.
                    val long: (() -> Unit)? = when (d) {
                        '0' -> ({ onKey('+') })
                        '1' -> onVoicemail
                        else -> null
                    }
                    Key(d, letters, { onKey(d) }, long, voicemail = d == '1')
                }
            }
        }
    }
}

@Composable
private fun Key(digit: Char, letters: String, onTap: () -> Unit, onLong: (() -> Unit)?, voicemail: Boolean = false) {
    val haptics = rememberHaptics()
    Column(
        Modifier
            .size(width = 96.dp, height = 64.dp)
            .clip(RoundedCornerShape(50))
            .background(Space.Panel.copy(alpha = 0.55f))
            .spotlight()
            .combinedClickable(
                onClick = { haptics.perform(Haptic.Tick); onTap() },
                onLongClick = onLong?.let { l -> { haptics.perform(Haptic.Confirm); l() } },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(digit.toString(), fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 28.sp, color = Space.Text)
        if (letters.isNotEmpty()) {
            Text(letters, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = Space.TextFaint)
        } else if (voicemail) {
            Icon(Icons.Rounded.Voicemail, "Mailbox", tint = Space.TextFaint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Suggestion(r: DialRow, onClick: () -> Unit) {
    val phone = r.contact.phones[r.hit.phoneIndex]
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(r.contact, r.color, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (r.hit.name.isBlank()) AnnotatedString("Ohne Namen") else marked(r.hit.name, r.hit.nameMarks), style = MaterialTheme.typography.titleMedium, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                marked(phone.value, r.hit.numberMarks).let { v -> if (phone.label.isBlank()) v else AnnotatedString(phone.label + " · ") + v },
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = Space.TextDim,
                maxLines = 1,
            )
        }
    }
}

/** The matched characters in violet and bold. */
private fun marked(text: String, marks: List<Int>): AnnotatedString = buildAnnotatedString {
    val set = marks.toSet()
    text.forEachIndexed { i, ch ->
        if (i in set) withStyle(SpanStyle(color = Space.Violet, fontWeight = FontWeight.SemiBold)) { append(ch) } else append(ch)
    }
}
