package com.partyos.tv.ui.lobby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.components.AvatarDot
import com.partyos.tv.ui.components.Backdrop
import com.partyos.tv.ui.theme.Party
import partyos.engine.GameInfo
import partyos.engine.GameResult
import partyos.engine.PlayerSummary
import partyos.engine.Role

/** Lobby: join QR and roster on the left, game picker on the right. */
@Composable
fun LobbyScreen(
    roomCode: String,
    joinUrl: String?,
    players: List<PlayerSummary>,
    games: List<GameInfo>,
    lastResult: GameResult?,
    onStart: (GameInfo) -> Unit,
) {
    val gamePlayers = players.filter { it.role == Role.PLAYER }
    val spectators = players.count { it.role == Role.SPECTATOR }
    val online = gamePlayers.count { it.connected }
    Backdrop {
        Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.width(300.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Scan to join", style = MaterialTheme.typography.headlineMedium, color = Party.Text)
                Spacer(Modifier.height(12.dp))
                if (joinUrl != null) QrCode(joinUrl, 220.dp) else NoNetworkCard()
                Spacer(Modifier.height(14.dp))
                Text("ROOM", style = MaterialTheme.typography.labelLarge, color = Party.Muted)
                Text(roomCode, style = MaterialTheme.typography.displayMedium, color = Party.Gold, modifier = Modifier.testTag("roomCode"))
                joinUrl?.let { Text(it.removePrefix("http://"), style = MaterialTheme.typography.bodyMedium, color = Party.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.weight(1f))
                Text(
                    "Same Wi-Fi as the TV. Guest networks often block phones from reaching it.",
                    style = MaterialTheme.typography.bodyMedium, color = Party.Muted,
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$online ready", style = MaterialTheme.typography.headlineLarge, color = Party.Text)
                    Spacer(Modifier.width(12.dp))
                    if (spectators > 0) Text("+ $spectators watching", style = MaterialTheme.typography.titleLarge, color = Party.Muted)
                }
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(gamePlayers, key = { it.id.v }) { p -> PlayerChip(p) }
                }
                lastResult?.let { r ->
                    Text("Last game: ${r.title} · winner ${r.standings.firstOrNull()?.name ?: "-"}", style = MaterialTheme.typography.bodyLarge, color = Party.Muted)
                    Spacer(Modifier.height(10.dp))
                }
                GamePicker(games, online, onStart)
            }
        }
    }
}

@Composable
private fun NoNetworkCard() = Box(
    Modifier.size(252.dp).background(Party.Card, RoundedCornerShape(18.dp)).padding(20.dp),
    contentAlignment = Alignment.Center,
) { Text("Connect the TV to Wi-Fi to show the join code", style = MaterialTheme.typography.titleLarge, color = Party.Muted) }

@Composable
private fun PlayerChip(p: PlayerSummary) {
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(shown, enter = scaleIn(initialScale = 0.6f) + fadeIn()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Party.Card).border(1.dp, Party.Line, RoundedCornerShape(14.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarDot(p.avatar, 36.dp, dim = !p.connected)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(p.name, style = MaterialTheme.typography.titleLarge, color = Party.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!p.connected) Text("away", style = MaterialTheme.typography.bodyMedium, color = Party.Muted)
            }
        }
    }
}

@Composable
fun GamePicker(games: List<GameInfo>, online: Int, onStart: (GameInfo) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        games.forEachIndexed { i, g ->
            val enough = online >= g.minPlayers
            Card(
                onClick = { onStart(g) },
                modifier = Modifier.width(340.dp).height(150.dp).testTag("game:${g.id}").then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
                scale = CardDefaults.scale(focusedScale = 1.06f),
                glow = CardDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Party.Brass, 16.dp)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    GameArt(g.id, Modifier.fillMaxSize())
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(g.title, style = MaterialTheme.typography.headlineLarge, color = Party.Text)
                        Text(
                            if (enough) "${g.tagline} · press OK to start" else "Needs ${g.minPlayers} players (${online} ready)",
                            style = MaterialTheme.typography.bodyMedium, color = if (enough) Party.Gold else Party.Muted,
                        )
                    }
                }
            }
        }
    }
}

/** Code-drawn key art. Bluff Battle: felt table, brass ring, and a masquerade mask. */
@Composable
fun GameArt(id: String, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            drawRect(Brush.linearGradient(listOf(Party.Felt, Party.FeltDeep), Offset.Zero, Offset(size.width, size.height)))
            val c = Offset(size.width * 0.8f, size.height * 0.42f)
            val r = size.height * 0.34f
            drawCircle(Party.Brass.copy(alpha = 0.9f), r, c, style = Stroke(r * 0.12f))
            val mask = Path().apply {
                moveTo(c.x - r * 0.95f, c.y - r * 0.15f)
                cubicTo(c.x - r * 0.6f, c.y - r * 0.6f, c.x - r * 0.15f, c.y - r * 0.35f, c.x, c.y - r * 0.1f)
                cubicTo(c.x + r * 0.15f, c.y - r * 0.35f, c.x + r * 0.6f, c.y - r * 0.6f, c.x + r * 0.95f, c.y - r * 0.15f)
                cubicTo(c.x + r * 0.8f, c.y + r * 0.45f, c.x + r * 0.2f, c.y + r * 0.35f, c.x, c.y + r * 0.2f)
                cubicTo(c.x - r * 0.2f, c.y + r * 0.35f, c.x - r * 0.8f, c.y + r * 0.45f, c.x - r * 0.95f, c.y - r * 0.15f)
                close()
            }
            drawPath(mask, Party.Brass)
            drawOval(Party.FeltDeep, Offset(c.x - r * 0.62f, c.y - r * 0.2f), androidx.compose.ui.geometry.Size(r * 0.42f, r * 0.24f))
            drawOval(Party.FeltDeep, Offset(c.x + r * 0.2f, c.y - r * 0.2f), androidx.compose.ui.geometry.Size(r * 0.42f, r * 0.24f))
        },
    )
}
