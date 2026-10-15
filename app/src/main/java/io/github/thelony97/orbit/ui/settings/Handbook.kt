package io.github.thelony97.orbit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.ui.theme.Space

/** **fett** and `Code` inside a line. */
internal fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val bold = text.startsWith("**", i)
        val code = text[i] == '`'
        val close = when {
            bold -> text.indexOf("**", i + 2)
            code -> text.indexOf('`', i + 1)
            else -> -1
        }
        when {
            bold && close > 0 -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Space.Text)) { append(text.substring(i + 2, close)) }
                i = close + 2
            }
            code && close > 0 -> {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Space.Teal)) { append(text.substring(i + 1, close)) }
                i = close + 1
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

/** Einstellungen › Über Orbit › Handbuch (PLAN 9 E22): gestures, rules, automations – from the bundled HANDBUCH.md. */
@Composable
fun HandbookSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lines = remember { runCatching { ctx.assets.open("HANDBUCH.md").bufferedReader().readLines() }.getOrDefault(emptyList()) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            lines.filter { it.isNotBlank() }.forEach { l ->
                when {
                    l.startsWith("## ") -> Text(l.drop(3), style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Violet, modifier = Modifier.padding(top = 14.dp))
                    l.startsWith("# ") -> Text(l.drop(2), style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
                    l.startsWith("- ") -> Text(inline("•  " + l.drop(2)), style = MaterialTheme.typography.bodyMedium, color = Space.TextDim)
                    else -> Text(inline(l), style = MaterialTheme.typography.bodyMedium, color = Space.TextDim)
                }
            }
        }
    }
}
