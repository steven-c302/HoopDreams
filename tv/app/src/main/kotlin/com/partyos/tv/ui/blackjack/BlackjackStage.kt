
package com.partyos.tv.ui.blackjack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.bluff.Header
import com.partyos.tv.ui.bluff.Podium
import com.partyos.tv.ui.components.AvatarDot
import com.partyos.tv.ui.components.Deal
import com.partyos.tv.ui.components.NeonSign
import com.partyos.tv.ui.components.Pop
import com.partyos.tv.ui.components.Slam
import com.partyos.tv.ui.components.Stamp
import com.partyos.tv.ui.theme.Party
import partyos.engine.BjSeat
import partyos.engine.BlackjackTv
import partyos.engine.PlayingCard
import partyos.engine.ScoreRow
import partyos.engine.StageInfo
import partyos.engine.games.blackjack.DrunkBlackjack

/** Drunk Blackjack on the Android TV: the House's cards up top, every seat's hand and drink call below. */
@Composable
fun BlackjackStage(stage: StageInfo, g: BlackjackTv, scores: List<ScoreRow>) {
    val total = when (g.phase) {
        "bet" -> DrunkBlackjack.BET_MS
        "play" -> DrunkBlackjack.PLAY_MS
        "dealer" -> DrunkBlackjack.DEALER_MS
        "settle" -> DrunkBlackjack.SETTLE_MS
        else -> DrunkBlackjack.PODIUM_MS
    }
    val chips = buildList {
        add((if (g.phase == "podium") "SIPS HANDED OUT" else "HAND ${g.round} OF ${g.totalRounds}") to Party.Cream)
        if (g.phase != "podium") add("${g.onTheLine} SIPS ON THE LINE" to Party.Pink)
    }
    val status = when (g.phase) {
        "bet" -> "${g.submitted}/${g.expected} BETS IN"
        "play" -> "${g.submitted}/${g.expected} DONE"
        else -> null
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
        Header(stage, total, chips, status, title = "DRUNK BLACKJACK", titleColor = Party.Gold)
        if (g.phase == "podium") {
            Podium(scores)
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(230.dp).background(Party.Night, RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text("HOUSE RULE", style = MaterialTheme.typography.labelMedium, color = Party.Muted)
                Text(g.ruleName, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Party.Display), color = Party.Pink)
                Text(g.ruleText, style = MaterialTheme.typography.bodyMedium, color = Party.Cream)
            }
            Spacer(Modifier.weight(1f))
            g.dealerAvatar?.let { AvatarDot(it, 36.dp) }
            Spacer(Modifier.width(8.dp))
            Text("${g.dealerName.uppercase()} DEALS", style = MaterialTheme.typography.labelLarge, color = Party.Gold)
            Spacer(Modifier.width(12.dp))
            Hand(g.dealer, if (g.seats.size > 12) 48.dp else 64.dp)
            Spacer(Modifier.width(12.dp))
            g.dealerTotal?.let { Led("$it", Party.Gold) }
            Spacer(Modifier.weight(1f))
            val banner = when (g.phase) {
                "bet" -> "PLACE YOUR BETS"
                "settle" -> DrunkBlackjack.total(g.dealer).let { if (it > 21) "${g.dealerName.uppercase()} BUSTS!" else "DEALER HAS $it" }
                "dealer" -> "${g.dealerName.uppercase()} IS PLAYING"
                else -> null
            }
            Box(Modifier.width(230.dp), contentAlignment = Alignment.Center) { banner?.let { key(it) { Slam { NeonSign(it, true, if (it.endsWith("BUSTS!")) Party.Mint else Party.Gold) } } } }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).drawBehind {
                drawRoundRect(Brush.radialGradient(listOf(Color(0xFF17806A), Color(0xFF0F5C4A), Color(0xFF073A2E)), Offset(size.width / 2, 0f), size.width * 0.7f), cornerRadius = CornerRadius(60.dp.toPx()))
                drawRoundRect(Party.Brass, cornerRadius = CornerRadius(60.dp.toPx()), style = Stroke(4.dp.toPx()))
            }.padding(18.dp),
        ) {
            val n = g.seats.size
            val rows = if (n <= 6) 1 else if (n <= 12) 2 else 3
            val perRow = (n + rows - 1) / rows
            val cardW = when (rows) { 1 -> 54.dp; 2 -> 40.dp; else -> 32.dp }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                g.seats.chunked(perRow).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        row.forEachIndexed { c, s -> Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) { Seat(s, g.phase, cardW, r * perRow + c) } }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Seat(s: BjSeat, phase: String, cardW: Dp, index: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Hand(s.cards, cardW)
            when (s.status) {
                "bust" -> Stamp("BUST", Party.Red, size = (cardW.value * 0.34f).toInt())
                "blackjack" -> Stamp("BLACKJACK", Party.Gold, size = (cardW.value * 0.28f).toInt(), tilt = 8f)
            }
            if (phase == "settle" && s.outcome != null) {
                val d = s.drinks ?: 0
                Pop(Modifier.align(Alignment.TopCenter).offset(y = -cardW * 0.25f), delayMs = 500 + index * 60) {
                    Text(
                        when { d > 0 -> "DRINK $d"; d < 0 -> "DEALER +${-d}"; else -> "SAFE" },
                        style = TextStyle(fontFamily = Party.Display, fontSize = (cardW.value * 0.3f).sp), color = Party.Ink,
                        modifier = Modifier.background(if (d > 0) Party.Red else if (d < 0) Party.Gold else Party.Cream, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.background(Party.Ink, RoundedCornerShape(50)).padding(end = 8.dp)
                .graphicsLayer { alpha = if (phase == "bet" && s.status == "betting") 0.5f else 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarDot(s.avatar, 22.dp)
            Spacer(Modifier.width(4.dp))
            Text(s.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = if (phase == "play" && s.status == "playing") Party.Gold else Party.Cream,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (s.cards.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Led("${s.total}", if (s.total > 21) Party.Red else if (s.total == 21) Party.Gold else Party.Mint) }
            if (s.bet > 0) Text(" ${if (s.bet == DrunkBlackjack.SHOT) "SHOT" else "${s.bet}🍺"}${if (s.doubled) "×2" else ""}", style = TextStyle(fontFamily = Party.Display, fontSize = 11.sp), color = Party.Gold)
        }
    }
}

@Composable
private fun Led(text: String, color: Color) = Text(
    text, style = TextStyle(fontFamily = Party.Display, fontSize = 13.sp), color = color,
    modifier = Modifier.background(Color(0xFF0A0508), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
)

@Composable
private fun Hand(cards: List<PlayingCard>, width: Dp) {
    Row {
        cards.forEachIndexed { i, c ->
            key(i) {
                Deal(Modifier.offset(x = -(width * 0.55f) * i), delayMs = i * 90, tilt = 14f) { CardFace(c, width, if (i % 2 == 0) -3f else 3f) }
            }
        }
    }
}

private val SUITS = listOf("♠", "♥", "♦", "♣")
private val RANKS = listOf("", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

/** A playing card drawn in code: ivory face with corner index and a big centre suit, or the velvet-and-gold back. */
@Composable
fun CardFace(card: PlayingCard, width: Dp, tilt: Float = 0f) {
    val up = card.rank > 0
    val ink = if (card.suit == 1 || card.suit == 2) Color(0xFFD8283F) else Color(0xFF17121F)
    Box(
        Modifier.size(width, width * 1.4f).graphicsLayer { rotationZ = tilt }.drawBehind {
            val r = CornerRadius(size.width * 0.1f)
            drawRoundRect(Color(0x55000000), Offset(0f, size.height * 0.04f), size, r)
            if (up) {
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFFFDF7), Color(0xFFF1E9D6))), cornerRadius = r)
                drawRoundRect(Color(0xFFD9CCAD), cornerRadius = r, style = Stroke(1.dp.toPx()))
            } else {
                drawRoundRect(Party.Velvet, cornerRadius = r)
                val inset = size.width * 0.08f
                drawRoundRect(Color(0xFF5C0F2E), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(r.x * 0.6f))
                drawRoundRect(Party.Brass, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(r.x * 0.6f), style = Stroke(1.5.dp.toPx()))
                drawCircle(Party.Gold, size.width * 0.16f, center, style = Stroke(1.5.dp.toPx()))
            }
        },
    ) {
        if (up) {
            Text(RANKS[card.rank], color = ink, style = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Black, fontSize = (width.value * 0.3f).sp),
                modifier = Modifier.padding(start = width * 0.08f, top = width * 0.02f))
            Text(if (card.rank >= 11) RANKS[card.rank] else SUITS[card.suit], color = ink,
                style = TextStyle(fontFamily = if (card.rank >= 11) Party.Marquee else Party.Body, fontSize = (width.value * 0.55f).sp),
                modifier = Modifier.align(Alignment.Center).offset(y = width * 0.06f))
        }
    }
}

