package com.partyos.tv.ui.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.TvController
import com.partyos.tv.ui.components.AvatarDot
import com.partyos.tv.ui.theme.Party
import partyos.engine.PlayerSummary
import partyos.engine.StageInfo

/** Remote-driven host controls. Opened with Back (or Menu) while a game runs; Back closes it. */
@Composable
fun HostOverlay(stage: StageInfo?, players: List<PlayerSummary>, controller: TvController, onClose: () -> Unit) {
    var confirmEnd by remember { mutableStateOf(false) }
    var kicking by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(confirmEnd, kicking) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Party.Ink.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).background(Party.Night, RoundedCornerShape(24.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (kicking) "Remove a player" else "Host controls", style = MaterialTheme.typography.headlineLarge, color = Party.Text)
            when {
                kicking -> LazyColumn(Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(players, key = { it.id.v }) { p ->
                        Button(onClick = { controller.kick(p.id); kicking = false }, modifier = if (p == players.first()) Modifier.focusRequester(first) else Modifier) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarDot(p.avatar, 28.dp, dim = !p.connected)
                                Spacer(Modifier.width(10.dp))
                                Text(p.name)
                            }
                        }
                    }
                }
                confirmEnd -> {
                    Text("End ${stage?.title ?: "the game"} now? Scores so far are kept in history.", style = MaterialTheme.typography.bodyLarge, color = Party.Muted)
                    Button(onClick = { controller.end(); onClose() }, modifier = Modifier.focusRequester(first),
                        colors = ButtonDefaults.colors(containerColor = Party.Red)) { Text("Yes, end game") }
                    Button(onClick = { confirmEnd = false }) { Text("Keep playing") }
                }
                else -> {
                    if (stage != null) {
                        if (stage.paused) Button(onClick = { controller.resume(); onClose() }, Modifier.focusRequester(first)) { Text("Resume") }
                        else Button(onClick = { controller.pause() }, Modifier.focusRequester(first)) { Text("Pause") }
                        Button(onClick = { controller.skip() }) { Text("Skip this phase") }
                        Button(onClick = { confirmEnd = true }) { Text("End game…") }
                    }
                    if (players.isNotEmpty()) Button(onClick = { kicking = true }, modifier = if (stage == null) Modifier.focusRequester(first) else Modifier) { Text("Remove a player…") }
                    Button(onClick = onClose, modifier = if (stage == null && players.isEmpty()) Modifier.focusRequester(first) else Modifier) { Text("Close") }
                }
            }
        }
    }
}
