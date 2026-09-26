@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.partyos.tv.ui.bluff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.blackjack.BlackjackStage
import com.partyos.tv.ui.components.AvatarDot
import com.partyos.tv.ui.components.Badge
import com.partyos.tv.ui.components.Confetti
import com.partyos.tv.ui.components.CountUp
import com.partyos.tv.ui.components.CountdownRing
import com.partyos.tv.ui.components.Deal
import com.partyos.tv.ui.components.MarqueeFrame
import com.partyos.tv.ui.components.NeonSign
import com.partyos.tv.ui.components.OnAirSign
import com.partyos.tv.ui.components.Pop
import com.partyos.tv.ui.components.Slam
import com.partyos.tv.ui.components.Stagger
import com.partyos.tv.ui.components.Stamp
import com.partyos.tv.ui.components.StudioBackdrop
import com.partyos.tv.ui.components.Wobble
import com.partyos.tv.ui.components.prop
import com.partyos.tv.ui.theme.Party
import kotlinx.coroutines.delay
import partyos.engine.BlackjackTv
import partyos.engine.BluffReveal
import partyos.engine.BluffTv
import partyos.engine.PlayerSummary
import partyos.engine.ScoreRow
import partyos.engine.StageInfo
import partyos.engine.TutorialView
import partyos.engine.games.bluff.BluffBattle

/** Everything the TV shows while a game is running: Bluff Battle's casino table inside the studio. */
@Composable
fun GameStage(stage: StageInfo, players: List<PlayerSummary>, scores: List<ScoreRow>) {
    StudioBackdrop(floor = Party.Felt, rays = Party.Brass) {
        val tutorial = stage.tutorial
        val game = stage.game
        when {
            tutorial != null -> TutorialStage(stage, tutorial, players)
            game is BluffTv -> BluffStage(stage, game, players, scores)
            game is BlackjackTv -> BlackjackStage(stage, game, scores)
        }
        if (stage.paused) PausedOverlay(stage.pauseReason)
    }
}

private val cardText = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 29.sp)

@Composable
internal fun Header(
    stage: StageInfo, totalMs: Long, chips: List<Pair<String, Color>>, status: String? = null,
    title: String = "BLUFF BATTLE", titleColor: Color = Party.Brass,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = titleColor)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { chips.forEach { (t, c) -> Badge(t, c) } }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall, color = Party.Gold)
            Spacer(Modifier.width(18.dp))
        }
        val timed = stage.deadlineAt != null || stage.remainingMs != null
        OnAirSign(lit = timed && !stage.paused)
        if (timed) {
            Spacer(Modifier.width(18.dp))
            CountdownRing(stage.deadlineAt, totalMs, if (stage.paused) stage.remainingMs else null, size = 80.dp)
        }
    }
}

@Composable
private fun TutorialStage(stage: StageInfo, t: TutorialView, players: List<PlayerSummary>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Header(
            stage, 30_000, listOf("HOW TO PLAY" to Party.Cream, "TAP “GOT IT” ON YOUR PHONE" to Party.Pink),
            title = stage.title.uppercase(), titleColor = if (stage.gameId == "blackjack") Party.Gold else Party.Brass,
        )
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            t.cards.forEachIndexed { i, c ->
                Stagger(i, Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 200.dp)
                            .prop(fill = Party.Cream, shadow = Party.Brass, tilt = listOf(-1.5f, 1f, -0.5f)[i % 3])
                            .padding(20.dp),
                    ) {
                        Box(Modifier.size(44.dp).background(Party.Felt, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.headlineLarge, color = Party.Brass)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(c.title, style = MaterialTheme.typography.headlineMedium, color = Party.Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(c.body, style = MaterialTheme.typography.bodyLarge, color = Party.Ink.copy(alpha = 0.75f))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("READY", style = MaterialTheme.typography.labelLarge, color = Party.Gold)
            Spacer(Modifier.width(4.dp))
            players.filter { it.connected }.forEach { p ->
                val ready = p.id in t.acked
                key(p.id.v, ready) { if (ready) Pop { AvatarDot(p.avatar, 40.dp) } else AvatarDot(p.avatar, 40.dp, dim = true) }
            }
        }
    }
}

@Composable
private fun BluffStage(stage: StageInfo, g: BluffTv, players: List<PlayerSummary>, scores: List<ScoreRow>) {
    val chips = buildList {
        add((if (g.phase == "podium") "FINAL RESULTS" else "ROUND ${g.round} OF ${g.totalRounds}") to Party.Cream)
        if (g.finalRound && g.phase in setOf("write", "pick", "reveal")) add("FINAL ROUND · DOUBLE POINTS" to Party.Pink)
    }
    val total = when (g.phase) {
        "write" -> BluffBattle.WRITE_MS
        "pick" -> BluffBattle.PICK_MS
        "scores" -> BluffBattle.SCORES_MS
        "podium" -> BluffBattle.PODIUM_MS
        else -> BluffBattle.REVEAL_STEP_MS * g.reveal.size + BluffBattle.REVEAL_TAIL_MS
    }
    val status = when (g.phase) {
        "write" -> "${g.submitted}/${g.expected} BLUFFS IN"
        "pick" -> "${g.submitted}/${g.expected} PICKED"
        else -> null
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 22.dp)) {
        Header(stage, total, chips, status)
        Spacer(Modifier.height(14.dp))
        when (g.phase) {
            "write" -> WritePhase(g)
            "pick" -> PickPhase(g)
            "reveal" -> RevealPhase(g, stage.phaseSeq, stage.paused, players)
            "scores" -> ScoresPhase(g, scores)
            else -> Podium(scores)
        }
    }
}

/** The question, printed on the felt with brass trim. */
@Composable
private fun PromptCard(text: String, big: Boolean) {
    Box(
        Modifier.fillMaxWidth()
            .prop(fill = Party.Felt, edge = Party.Brass, shadow = Party.FeltDeep, radius = 24.dp, offset = 8.dp, stroke = 4.dp)
            .padding(horizontal = 40.dp, vertical = if (big) 40.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (big) {
            Text("♠", color = Party.Brass.copy(alpha = 0.6f), fontSize = 26.sp, modifier = Modifier.align(Alignment.TopStart).offset((-22).dp, (-26).dp))
            Text("♦", color = Party.Brass.copy(alpha = 0.6f), fontSize = 26.sp, modifier = Modifier.align(Alignment.BottomEnd).offset(22.dp, 26.dp))
        }
        Text(
            text, color = Party.Cream, textAlign = TextAlign.Center,
            style = if (big) TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 46.sp) else cardText,
        )
    }
}

@Composable
private fun WritePhase(g: BluffTv) = Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.weight(0.4f))
    Slam(from = 1.4f, tilt = -3f) { PromptCard(g.prompt, big = true) }
    Spacer(Modifier.height(30.dp))
    Wobble {
        Text(
            "Write a fake answer that sounds TRUE", style = MaterialTheme.typography.headlineMedium, color = Party.Pink,
            modifier = Modifier.graphicsLayer { rotationZ = -1.5f },
        )
    }
    Spacer(Modifier.height(18.dp))
    ChipStack(g.submitted, g.expected)
    Spacer(Modifier.weight(1f))
}

/** One poker chip per player: gold once their bluff is in. */
@Composable
private fun ChipStack(done: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val lit = i < done
            key(i, lit) {
                val chip: @Composable () -> Unit = {
                    Box(
                        Modifier.size(26.dp).background(if (lit) Party.Gold else Party.FeltDeep, CircleShape)
                            .padding(5.dp).background(if (lit) Party.Brass else Party.Felt, CircleShape),
                    )
                }
                if (lit) Pop(content = chip) else chip()
            }
        }
    }
}

@Composable
private fun PickPhase(g: BluffTv) = Column(Modifier.fillMaxSize()) {
    PromptCard(g.prompt, big = false)
    Spacer(Modifier.height(16.dp))
    val n = g.options.size
    val cols = when {
        n <= 4 -> 2
        n <= 9 -> 3
        else -> 4
    }
    val dense = n > 9
    Column(verticalArrangement = Arrangement.spacedBy(if (dense) 9.dp else 14.dp)) {
        g.options.chunked(cols).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEachIndexed { c, text ->
                    val i = r * cols + c
                    Stagger(i, Modifier.weight(1f)) { PlayingCard(text, ('A' + i).toString(), dense, tilt = if (i % 2 == 0) -1f else 0.8f) }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A cream playing card with a letter pip in the corner. */
@Composable
private fun PlayingCard(text: String, pip: String, dense: Boolean, tilt: Float) {
    Row(
        Modifier.fillMaxWidth()
            .prop(fill = Party.Cream, shadow = Party.Brass, radius = 14.dp, offset = if (dense) 4.dp else 6.dp, tilt = tilt)
            .padding(horizontal = if (dense) 12.dp else 18.dp, vertical = if (dense) 7.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(pip, style = MaterialTheme.typography.headlineLarge.copy(fontSize = if (dense) 20.sp else 28.sp), color = Party.Pink,
            modifier = Modifier.width(if (dense) 26.dp else 40.dp))
        Text(
            text, style = if (dense) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black) else cardText,
            color = Party.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RevealPhase(g: BluffTv, phaseSeq: Int, paused: Boolean, players: List<PlayerSummary>) {
    var shown by remember(phaseSeq) { mutableIntStateOf(0) }
    LaunchedEffect(phaseSeq, paused) {
        if (paused) return@LaunchedEffect // the reveal holds its place while the game is paused
        while (shown < g.reveal.size) {
            delay(if (shown == 0) 600 else BluffBattle.REVEAL_STEP_MS)
            shown++
        }
    }
    val current = g.reveal.getOrNull(shown - 1)
    val truthUp = current?.kind == "truth"
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PromptCard(g.prompt, big = false)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    if (current == null) {
                        Wobble { Text("Let's see who got fooled…", style = MaterialTheme.typography.headlineMedium, color = Party.Gold) }
                    } else {
                        key(shown) { Slam(from = 1.3f, tilt = -4f) { RevealCard(current, players) } }
                    }
                }
                Spacer(Modifier.width(24.dp))
                NeonSign("APPLAUSE", truthUp, Party.Pink, Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                g.reveal.take((shown - 1).coerceAtLeast(0)).forEach { RevealChip(it) }
            }
        }
        if (truthUp) key(shown) { Confetti() }
    }
}

/** The answer under the spotlight, stamped with what it really was and who fell for it. */
@Composable
private fun RevealCard(item: BluffReveal, players: List<PlayerSummary>) {
    val truth = item.kind == "truth"
    val (stamp, stampColor) = when (item.kind) {
        "truth" -> "THE TRUTH" to Party.Felt
        "decoy" -> "HOUSE FAKE" to Party.Sky
        else -> "FAKE!" to Party.Red
    }
    val avatars = players.associateBy { it.name }
    Box {
        Column(
            Modifier.fillMaxWidth()
                .prop(fill = if (truth) Party.Gold else Party.Cream, shadow = if (truth) Party.Mint else Party.Pink, radius = 20.dp, offset = 9.dp, stroke = 4.dp)
                .padding(horizontal = 28.dp, vertical = 18.dp),
        ) {
            Text(item.text, style = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 42.sp),
                color = Party.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 190.dp))
            if (item.kind == "fake" && item.authors.isNotEmpty()) {
                Text("written by ${item.authors.joinToString(" & ")}", style = MaterialTheme.typography.titleLarge, color = Party.Ink.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when {
                        item.fooled.isEmpty() && truth -> "NOBODY FOUND IT!"
                        item.fooled.isEmpty() -> "FOOLED NOBODY"
                        truth -> "FOUND IT"
                        else -> "FOOLED"
                    },
                    style = MaterialTheme.typography.labelLarge, color = Party.Ink,
                )
                item.fooled.forEachIndexed { i, name ->
                    Pop(delayMs = 350 + i * 110) {
                        Row(
                            Modifier.background(Party.Ink, RoundedCornerShape(50)).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            avatars[name]?.let { AvatarDot(it.avatar, 28.dp) }
                            Spacer(Modifier.width(6.dp))
                            Text(name, style = MaterialTheme.typography.titleMedium, color = Party.Cream)
                        }
                    }
                }
            }
        }
        Stamp(stamp, stampColor, Modifier.align(Alignment.TopEnd).offset((-18).dp, 14.dp), tilt = -9f, size = 28, delayMs = 250)
    }
}

@Composable
private fun RevealChip(item: BluffReveal) = Text(
    "${item.text} · ${item.fooled.size} fooled",
    style = MaterialTheme.typography.titleMedium, color = Party.Cream.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
    modifier = Modifier.widthIn(max = 280.dp).background(Party.FeltDeep, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
)

@Composable
private fun ScoresPhase(g: BluffTv, scores: List<ScoreRow>) {
    val deltas = g.deltas.associate { it.id to it.points }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        scores.take(8).forEachIndexed { i, row ->
            val lead = i == 0
            val gained = deltas[row.id] ?: 0
            Stagger(i) {
                Row(
                    Modifier.fillMaxWidth()
                        .prop(fill = if (lead) Party.Gold else Party.Cream, shadow = if (lead) Party.Pink else Party.Brass, radius = 14.dp, offset = 5.dp)
                        .padding(horizontal = 14.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).background(Party.Ink, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Party.Display), color = Party.Gold)
                    }
                    Spacer(Modifier.width(10.dp))
                    AvatarDot(row.avatar, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(row.name + if (lead) "  👑" else "", style = MaterialTheme.typography.headlineSmall, color = Party.Ink, modifier = Modifier.weight(1f))
                    if (gained > 0) {
                        Pop(delayMs = 300 + i * 90) {
                            Text("+%,d".format(gained), style = MaterialTheme.typography.titleMedium.copy(fontFamily = Party.Display), color = Party.Ink,
                                modifier = Modifier.background(Party.Mint, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    CountUp(row.score - gained, row.score, MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp), Party.Ink,
                        Modifier.widthIn(min = 110.dp), delayMs = 500 + i * 120)
                }
            }
        }
        if (scores.size > 8) Text("+ ${scores.size - 8} more", style = MaterialTheme.typography.bodyLarge, color = Party.Muted)
    }
}

/** Third, then second, then the winner: each one slams onto a lit block, then the cannons fire. */
@Composable
internal fun Podium(scores: List<ScoreRow>) {
    val top = scores.take(3)
    val order = listOf(1, 0, 2).filter { it < top.size }
    val landsAt = listOf(2_600, 1_400, 400) // by rank
    var cannons by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(landsAt[0] + 300L); cannons = true }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
            order.forEach { rank ->
                val row = top[rank]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Slam(delayMs = landsAt[rank], from = 2.2f, tilt = if (rank == 1) 8f else -8f) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (rank == 0) Text("👑", fontSize = 34.sp)
                            AvatarDot(row.avatar, if (rank == 0) 76.dp else 60.dp)
                            Text(row.name, style = MaterialTheme.typography.headlineMedium, color = Party.Cream)
                            Text("%,d".format(row.score), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp), color = Party.Gold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Deal(delayMs = landsAt[rank] - 250, tilt = 0f) {
                        Box(
                            Modifier.width(190.dp).height(listOf(230.dp, 165.dp, 115.dp)[rank])
                                .prop(fill = listOf(Party.Gold, Party.Cream, Party.Brass)[rank], shadow = Party.Pink, radius = 16.dp, offset = 8.dp, stroke = 4.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Text("${rank + 1}", style = TextStyle(fontFamily = Party.Marquee, fontSize = 64.sp), color = Party.Ink, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        if (cannons) Confetti(count = 160)
    }
}

/** The curtains close halfway and the marquee says why. */
@Composable
private fun PausedOverlay(reason: String?) = Box(Modifier.fillMaxSize().background(Party.Ink.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
    Row(Modifier.fillMaxSize()) {
        Curtain(Modifier.weight(1f))
        Spacer(Modifier.weight(1.4f))
        Curtain(Modifier.weight(1f))
    }
    Slam(from = 1.5f, tilt = -4f) {
        MarqueeFrame(Modifier.widthIn(max = 620.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PAUSED", style = TextStyle(fontFamily = Party.Marquee, fontSize = 60.sp), color = Party.Gold)
                Text(
                    when (reason) {
                        "WAITING_FOR_PLAYERS" -> "Waiting for players to reconnect · press Back for host controls"
                        "RESTORED" -> "Picked up where you left off · press Back and choose Resume"
                        else -> "Press Back for host controls"
                    },
                    style = MaterialTheme.typography.titleLarge, color = Party.Cream, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Curtain(modifier: Modifier) = Box(
    modifier.fillMaxHeight().drawWithCache {
        val folds = Brush.horizontalGradient(listOf(Party.Velvet, Party.VelvetHi, Party.Velvet), 0f, size.width / 4f, TileMode.Mirror)
        onDrawBehind { drawRect(folds) }
    },
)
