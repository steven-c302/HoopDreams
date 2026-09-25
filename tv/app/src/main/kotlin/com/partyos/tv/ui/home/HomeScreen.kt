package com.partyos.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.components.Backdrop
import com.partyos.tv.ui.theme.Party

data class HomeTile(val title: String, val subtitle: String, val colors: List<Color>, val glyph: String, val onClick: () -> Unit)

/** Only destinations that work today are shown; later sub-projects add tiles here. */
@Composable
fun HomeScreen(playersOnline: Int, onPlay: () -> Unit, onQuickPlay: () -> Unit, onSettings: () -> Unit) {
    val tiles = listOf(
        HomeTile("Play Games", "Pick a game and start", listOf(Party.Pink, Party.Orange), "▶", onPlay),
        HomeTile("Quick Play", "Jump straight in", listOf(Party.Sky, Party.Mint), "⚡", onQuickPlay),
        HomeTile("Settings", "PIN, network, performance", listOf(Party.Plum, Party.Line), "⚙", onSettings),
    )
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Backdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            Text("PARTY OS", style = MaterialTheme.typography.displayLarge, color = Party.Text)
            Text(
                if (playersOnline == 0) "Grab your phones. No app needed." else "$playersOnline ${if (playersOnline == 1) "phone" else "phones"} connected",
                style = MaterialTheme.typography.titleLarge, color = Party.Muted,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                tiles.forEachIndexed { i, t ->
                    Card(
                        onClick = t.onClick,
                        modifier = Modifier.width(260.dp).height(170.dp).testTag("tile:${t.title}")
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        shape = CardDefaults.shape(RoundedCornerShape(22.dp)),
                        scale = CardDefaults.scale(focusedScale = 1.08f),
                        glow = CardDefaults.glow(focusedGlow = androidx.tv.material3.Glow(t.colors.first(), 18.dp)),
                    ) {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(t.colors)).padding(20.dp)) {
                            Text(t.glyph, style = MaterialTheme.typography.displayMedium, color = Party.Ink.copy(alpha = 0.55f),
                                modifier = Modifier.align(Alignment.TopEnd))
                            Column(Modifier.align(Alignment.BottomStart)) {
                                Text(t.title, style = MaterialTheme.typography.headlineLarge, color = Party.Ink)
                                Text(t.subtitle, style = MaterialTheme.typography.bodyMedium, color = Party.Ink.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}
