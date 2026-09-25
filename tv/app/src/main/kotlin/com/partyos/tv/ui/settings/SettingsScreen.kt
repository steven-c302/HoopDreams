package com.partyos.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.settings.Settings
import com.partyos.tv.ui.TvController
import com.partyos.tv.ui.components.Backdrop
import com.partyos.tv.ui.theme.Party

@Composable
fun SettingsScreen(settings: Settings, ip: String?, port: Int?, joinUrl: String?, controller: TvController, onBenchmark: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    var confirmNew by remember { mutableStateOf(false) }
    Backdrop {
        Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.width(460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Settings", style = MaterialTheme.typography.displayMedium, color = Party.Text)
                Label("Host PIN (phones open /host and enter this)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(settings.pin, style = MaterialTheme.typography.displayMedium, color = Party.Gold)
                    Spacer(Modifier.width(20.dp))
                    Button(onClick = controller::newPin, modifier = Modifier.focusRequester(first)) { Text("New PIN") }
                }
                Label("Rounds per game")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { controller.setRounds(settings.rounds - 1) }) { Text("−") }
                    Text("${settings.rounds}", style = MaterialTheme.typography.headlineLarge, color = Party.Text)
                    Button(onClick = { controller.setRounds(settings.rounds + 1) }) { Text("+") }
                }
                Label("Performance")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { controller.setPerfHud(!settings.perfHud) }) { Text(if (settings.perfHud) "Hide FPS meter" else "Show FPS meter") }
                    Button(onClick = onBenchmark) { Text("Run benchmark tour") }
                }
                Label("Party")
                if (confirmNew) {
                    Text("Start a new party? Everyone will need to rejoin with the new code.", color = Party.Muted, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { controller.newParty(); confirmNew = false }) { Text("Yes, new party") }
                        Button(onClick = { confirmNew = false }) { Text("Cancel") }
                    }
                } else {
                    Button(onClick = { confirmNew = true }) { Text("New party (new room code)") }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("Network")
                Text("TV address: ${ip ?: "not connected"}", style = MaterialTheme.typography.titleLarge, color = Party.Text)
                Text("Server port: ${port ?: "-"}", style = MaterialTheme.typography.titleLarge, color = Party.Text)
                Text("Join link: ${joinUrl ?: "-"}", style = MaterialTheme.typography.titleLarge, color = Party.Text)
                Spacer(Modifier.width(1.dp))
                Label("Advertised address override (emulator/dev only, e.g. 192.168.1.20:8080). Leave blank on a real TV.")
                OverrideField(settings.advertisedOverride, controller::setOverride)
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = Party.Muted)

@Composable
private fun OverrideField(value: String, onSave: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it.take(64) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = Party.Text),
        cursorBrush = SolidColor(Party.Pink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave(draft) }),
        modifier = Modifier.width(480.dp).onFocusChanged { if (focused && !it.isFocused) onSave(draft); focused = it.isFocused }
            .background(Party.Card, RoundedCornerShape(12.dp)).border(2.dp, if (focused) Party.Pink else Party.Line, RoundedCornerShape(12.dp)).padding(14.dp),
    )
}
