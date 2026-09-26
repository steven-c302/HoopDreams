package com.partyos.tv.ui.lobby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.components.AvatarDot
import com.partyos.tv.ui.components.MarqueeFrame
import com.partyos.tv.ui.components.OnAirSign
import com.partyos.tv.ui.components.Pop
import com.partyos.tv.ui.components.StudioBackdrop
import com.partyos.tv.ui.components.colorValue
import com.partyos.tv.ui.components.prop
import com.partyos.tv.ui.theme.Party
import partyos.engine.GameInfo
import partyos.engine.GameResult
import partyos.engine.PlayerSummary
import partyos.engine.Role

/** Lobby: the join marquee on the left, contestants and the game picker on the right. */
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
    StudioBackdrop {
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.width(300.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                MarqueeFrame(Modifier.fillMaxWidth(), contentPadding = 24.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SCAN TO JOIN", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp), color = Party.Gold)
                        Spacer(Modifier.height(10.dp))
                        if (joinUrl != null) QrCode(joinUrl, 196.dp) else NoNetworkCard()
                        Spacer(Modifier.height(10.dp))
                        Text("ROOM", style = MaterialTheme.typography.labelMedium, color = Party.Cream.copy(alpha = 0.7f))
                        Text(roomCode, style = TextStyle(fontFamily = Party.Marquee, fontSize = 44.sp, lineHeight = 50.sp), color = Party.Gold,
                            modifier = Modifier.testTag("roomCode"))
                    }
                }
                Spacer(Modifier.height(10.dp))
                joinUrl?.let { Text(it.removePrefix("http://"), style = MaterialTheme.typography.titleMedium, color = Party.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.weight(1f))
                Text(
                    "Same Wi-Fi as the TV. Guest networks often block phones from reaching it.",
                    style = MaterialTheme.typography.bodyMedium, color = Party.Muted, textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (online == 1) "1 CONTESTANT" else "$online CONTESTANTS", style = MaterialTheme.typography.headlineLarge, color = Party.Cream)
                    Spacer(Modifier.width(14.dp))
                    if (spectators > 0) Text("+ $spectators in the audience", style = MaterialTheme.typography.titleLarge, color = Party.Muted)
                    Spacer(Modifier.weight(1f))
                    OnAirSign(lit = online > 0)
                }
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(gamePlayers, key = { _, p -> p.id.v }) { i, p -> NameTag(p, i) }
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
    Modifier.size(196.dp).background(Party.Card, RoundedCornerShape(18.dp)).padding(18.dp),
    contentAlignment = Alignment.Center,
) { Text("Connect the TV to Wi-Fi to show the join code", style = MaterialTheme.typography.titleLarge, color = Party.Muted) }

/** A "HELLO my name is" tag in the player's colour, a little askew. It pops on when they join. */
@Composable
private fun NameTag(p: PlayerSummary, index: Int) {
    val color = p.avatar.colorValue()
    Pop {
        Column(
            Modifier.fillMaxWidth().graphicsLayer { alpha = if (p.connected) 1f else 0.5f }
                .prop(fill = Party.Cream, shadow = color, radius = 12.dp, offset = 5.dp, tilt = listOf(-2f, 1.5f, -1f, 2f)[index % 4]),
        ) {
            Text(
                "HELLO", style = MaterialTheme.typography.labelMedium, color = Party.Cream,
                modifier = Modifier.fillMaxWidth().background(color, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarDot(p.avatar, 32.dp, dim = !p.connected)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, color = Party.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!p.connected) Text("away", style = MaterialTheme.typography.bodyMedium, color = Party.Ink.copy(alpha = 0.6f))
                }
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
                modifier = Modifier.weight(1f).height(140.dp).testTag("game:${g.id}").then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
                scale = CardDefaults.scale(focusedScale = 1.06f),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Party.Gold), shape = RoundedCornerShape(20.dp))),
                glow = CardDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Party.Gold, 18.dp)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    GameArt(g.id, Modifier.fillMaxSize())
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(g.title.uppercase(), style = MaterialTheme.typography.headlineLarge, color = Party.Cream)
                        Text(
                            if (enough) "${g.tagline} · press OK to start" else "Needs ${g.minPlayers} players (${online} ready)",
                            style = MaterialTheme.typography.titleMedium, color = if (enough) Party.Gold else Party.Cream.copy(alpha = 0.7f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
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
            val c = Offset(size.width * 0.86f, size.height * 0.36f)
            val r = size.height * 0.27f
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
