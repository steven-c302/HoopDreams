package com.partyos.tv.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.components.MarqueeFrame
import com.partyos.tv.ui.components.OnAirSign
import com.partyos.tv.ui.components.Slam
import com.partyos.tv.ui.components.Stagger
import com.partyos.tv.ui.components.StudioBackdrop
import com.partyos.tv.ui.components.prop
import com.partyos.tv.ui.theme.Party

data class HomeTile(val title: String, val subtitle: String, val fill: Color, val shadow: Color, val glyph: String, val onClick: () -> Unit)

/** Only destinations that work today are shown; later sub-projects add tiles here. */
@Composable
fun HomeScreen(playersOnline: Int, onPlay: () -> Unit, onQuickPlay: () -> Unit, onSettings: () -> Unit) {
    val tiles = listOf(
        HomeTile("Play Games", "Pick a game and start", Party.Gold, Party.Pink, "▶", onPlay),
        HomeTile("Quick Play", "Jump straight in", Party.Pink, Party.Gold, "⚡", onQuickPlay),
        HomeTile("Settings", "PIN, network, performance", Party.Cream, Party.Sky, "⚙", onSettings),
    )
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    StudioBackdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (playersOnline == 0) "Grab your phones. No app needed." else "$playersOnline ${if (playersOnline == 1) "phone" else "phones"} connected",
                    style = MaterialTheme.typography.titleLarge, color = Party.Cream, modifier = Modifier.weight(1f),
                )
                OnAirSign(lit = playersOnline > 0)
            }
            Spacer(Modifier.weight(0.6f))
            Slam(from = 1.6f, tilt = -5f) {
                MarqueeFrame(contentPadding = 30.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PARTY OS", style = TextStyle(fontFamily = Party.Marquee, fontSize = 76.sp, lineHeight = 82.sp), color = Party.Gold)
                        Text("LIVE FROM YOUR LIVING ROOM", style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, letterSpacing = 5.sp), color = Party.Pink)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                tiles.forEachIndexed { i, t ->
                    Stagger(i + 2) {
                        Card(
                            onClick = t.onClick,
                            modifier = Modifier.width(250.dp).height(150.dp).testTag("tile:${t.title}")
                                .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                            shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
                            colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                            scale = CardDefaults.scale(focusedScale = 1.1f),
                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Party.Cream), shape = RoundedCornerShape(20.dp))),
                            glow = CardDefaults.glow(focusedGlow = androidx.tv.material3.Glow(t.fill, 22.dp)),
                        ) {
                            Box(Modifier.fillMaxSize().prop(fill = t.fill, shadow = t.shadow, radius = 20.dp, offset = 0.dp, stroke = 4.dp).padding(18.dp)) {
                                Text(t.glyph, style = MaterialTheme.typography.displayMedium, color = Party.Ink.copy(alpha = 0.8f),
                                    modifier = Modifier.align(Alignment.TopEnd))
                                Column(Modifier.align(Alignment.BottomStart)) {
                                    Text(t.title.uppercase(), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp), color = Party.Ink)
                                    Text(t.subtitle, style = MaterialTheme.typography.titleMedium, color = Party.Ink.copy(alpha = 0.75f))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}
