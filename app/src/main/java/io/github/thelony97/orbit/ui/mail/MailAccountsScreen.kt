package io.github.thelony97.orbit.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.mail.MailAccount
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.mail.MailCache
import io.github.thelony97.orbit.mail.MailClient
import io.github.thelony97.orbit.mail.MailProviders
import io.github.thelony97.orbit.mail.Secrets
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.components.ConfirmDialog
import io.github.thelony97.orbit.ui.components.GlassCard
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SectionTitle
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
fun MailAccountsScreen(nav: Nav) {
    val accounts by MailAccounts.accounts.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<MailAccount?>(null) }
    val scope = rememberCoroutineScope()

    SpaceBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", nav.back, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text("E-Mail-Konten", style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text, modifier = Modifier.weight(1f))
                GlowButton("Hinzufügen", { adding = true }, icon = Icons.Rounded.Add)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Lock, null, tint = Space.Teal, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Orbit liest deine Postfächer nur: Absender, Betreff und Datum der Mails von deinen Kontakten. " +
                                "Den Text lädt Orbit erst, wenn du eine Mail antippst. Passwörter liegen verschlüsselt im Schlüsselspeicher " +
                                "dieses Handys und kommen nie in ein Backup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Space.TextDim,
                        )
                    }
                }
                if (accounts.isEmpty()) {
                    GlassCard {
                        Text("Noch kein Konto verbunden", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                        Text(
                            "Füge so viele Postfächer hinzu, wie du willst. Im Kontakt siehst du dann die letzten Mails dieser Person – gesammelt aus allen ihren Adressen und allen deinen Konten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Space.TextDim,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                accounts.forEach { a ->
                    GlassCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Space.Violet.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Email, null, tint = Space.Violet, modifier = Modifier.size(18.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.email, style = MaterialTheme.typography.bodyLarge, color = Space.Text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (a.lastError == null) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                                        tint = if (a.lastError == null) Space.Teal else Space.Coral,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        a.lastError ?: "${MailProviders.byKey(a.provider).label} · ${a.host}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (a.lastError == null) Space.TextFaint else Space.Coral,
                                    )
                                }
                            }
                            OrbButton(Icons.Rounded.Delete, "Entfernen", { removing = a }, size = 34.dp)
                        }
                    }
                }
            }
        }
    }

    if (adding) AddAccountSheet(onDismiss = { adding = false })
    removing?.let { a ->
        ConfirmDialog(
            title = "Konto entfernen?",
            text = "${a.email} wird aus Orbit entfernt. Im Postfach selbst ändert sich nichts.",
            confirm = "Entfernen",
            onConfirm = { scope.launch { MailAccounts.remove(a.id) } },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun AddAccountSheet(onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()
    val host = rememberTextFieldState()
    val port = rememberTextFieldState("993")
    val user = rememberTextFieldState()
    var providerKey by remember { mutableStateOf("gmail") }
    var manualProvider by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Pick the provider from the address while typing, unless chosen by hand.
    LaunchedEffect(Unit) {
        snapshotFlow { email.text.toString() }.collect { e ->
            if (!manualProvider && e.contains('@')) providerKey = MailProviders.forEmail(e).key
        }
    }
    val provider = MailProviders.byKey(providerKey)
    LaunchedEffect(providerKey) {
        if (provider.host.isNotEmpty()) {
            host.setTextAndPlaceCursorAtEnd(provider.host)
            port.setTextAndPlaceCursorAtEnd(provider.port.toString())
        } else if (MailProviders.all.any { it.host.isNotEmpty() && it.host == host.text.toString() }) {
            // Switching to "Anderer Server" must not keep the previous provider's server.
            host.setTextAndPlaceCursorAtEnd("")
            port.setTextAndPlaceCursorAtEnd("993")
        }
    }

    fun connect() {
        val mail = email.text.toString().trim()
        val pw = password.text.toString()
        val server = host.text.toString().trim()
        val p = port.text.toString().trim().toIntOrNull() ?: 993
        val login = user.text.toString().trim().ifEmpty { mail }
        if (!mail.contains('@') || pw.isEmpty() || server.isEmpty()) {
            error = "Bitte E-Mail, Passwort und Server angeben."
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = MailClient.test(server, p, login, pw)
            if (result.isSuccess) {
                MailAccounts.upsert(
                    MailAccount(
                        email = mail,
                        host = server,
                        port = p,
                        username = login,
                        secret = Secrets.encrypt(pw),
                        provider = providerKey,
                    ),
                )
                MailCache.clear()
                busy = false
                onDismiss()
            } else {
                busy = false
                error = MailClient.describe(result.exceptionOrNull() ?: Exception("Unbekannter Fehler"))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheet,
        containerColor = Space.PanelHigh,
        contentColor = Space.Text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Postfach verbinden", style = MaterialTheme.typography.titleLargeEmphasized)
            SpaceField(email, "E-Mail-Adresse", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            SectionTitle("Anbieter")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MailProviders.all.forEach { p ->
                    SpaceChip(
                        p.label,
                        providerKey == p.key,
                        { providerKey = p.key; manualProvider = true },
                        accent = if (p.supported) Space.Violet else Space.Coral,
                    )
                }
            }
            Text(
                provider.hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (provider.supported) Space.TextDim else Space.Coral,
            )
            SecureField(password, if (provider.key in setOf("gmail", "icloud", "yahoo")) "App-Passwort" else "Passwort")
            if (provider.key == "other" || provider.host.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceField(host, "IMAP-Server", Modifier.weight(1f), placeholder = "imap.example.com")
                    SpaceField(port, "Port", Modifier.width(90.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                SpaceField(user, "Benutzername (falls nicht die E-Mail)")
            }
            error?.let {
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), tint = Space.Panel, border = Space.Coral.copy(alpha = 0.5f)).padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Space.Coral, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Space.Text)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowButton(if (busy) "Verbinde …" else "Verbinden & speichern", { if (!busy) connect() }, icon = Icons.Rounded.Lock, enabled = !busy)
                if (busy) {
                    Spacer(Modifier.width(12.dp))
                    LoadingIndicator(Modifier.size(32.dp), color = Space.Violet)
                }
            }
        }
    }
}

/** Password field in the glass style; the text stays hidden. */
@Composable
private fun SecureField(state: TextFieldState, label: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Space.TextDim, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        BasicSecureTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Space.Text),
            cursorBrush = SolidColor(Space.Violet),
            decorator = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .glass(RoundedCornerShape(14.dp), tint = Space.Panel)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}
