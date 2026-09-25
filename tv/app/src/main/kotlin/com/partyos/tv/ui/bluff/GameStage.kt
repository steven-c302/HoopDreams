@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.partyos.tv.ui.bluff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.ui.components.AvatarDot
import com.partyos.tv.ui.components.Backdrop
import com.partyos.tv.ui.components.CountdownRing
import com.partyos.tv.ui.theme.Party
import kotlinx.coroutines.delay
import partyos.engine.BluffReveal
import partyos.engine.BluffTv
import partyos.engine.PlayerSummary
import partyos.engine.ScoreRow
import partyos.engine.StageInfo
import partyos.engine.TutorialView
import partyos.engine.games.bluff.BluffBattle

/** Everything the TV shows while a game is running. */
@Composable
fun GameStage(stage: StageInfo, players: List<PlayerSummary>, scores: List<ScoreRow>) {
    Backdrop(a = Party.Felt, b = Party.Brass.copy(alpha = 0.25f)) {
        val tutorial = stage.tutorial
        val game = stage.game
        when {
            tutorial != null -> TutorialStage(stage, tutorial, players)
            game is BluffTv -> BluffStage(stage, game, scores)
        }
        if (stage.paused) PausedOverlay(stage.pauseReason)
    }
}

@Composable
private fun Header(title: String, subtitle: String, stage: StageInfo, totalMs: Long, badge: String? = null, status: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Party.Brass)
            Text(subtitle, style = MaterialTheme.typography.titleLarge, color = Party.Muted)
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.headlineMedium, color = Party.Gold)
            Spacer(Modifier.width(20.dp))
        }
        badge?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = Party.Ink,
                modifier = Modifier.background(Party.Gold, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
            Spacer(Modifier.width(20.dp))
        }
        if (stage.deadlineAt != null || stage.remainingMs != null) {
            CountdownRing(stage.deadlineAt, totalMs, if (stage.paused) stage.remainingMs else null)
        }
    }
}

@Composable
private fun TutorialStage(stage: StageInfo, t: TutorialView, players: List<PlayerSummary>) {
    Column(Modifier.fillMaxSize().padding(48.dp)) {
        Header(stage.title, "How to play · tap “Got it” on your phone", stage, 30_000)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            t.cards.forEachIndexed { i, c ->
                Stagger(i) {
                    Column(
                        Modifier.width(280.dp).heightIn(min = 210.dp).background(Party.FeltDeep, RoundedCornerShape(20.dp))
                            .border(2.dp, Party.Brass.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(20.dp),
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.displayMedium, color = Party.Brass)
                        Text(c.title, style = MaterialTheme.typography.headlineMedium, color = Party.Text)
                        Spacer(Modifier.height(6.dp))
                        Text(c.body, style = MaterialTheme.typography.bodyLarge, color = Party.Muted)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ready:", style = MaterialTheme.typography.titleLarge, color = Party.Muted)
            players.filter { it.connected }.forEach { p -> AvatarDot(p.avatar, 40.dp, dim = p.id !in t.acked) }
        }
    }
}

@Composable
private fun BluffStage(stage: StageInfo, g: BluffTv, scores: List<ScoreRow>) {
    val badge = if (g.finalRound && g.phase in setOf("write", "pick", "reveal")) "FINAL ROUND · DOUBLE POINTS" else null
    val subtitle = if (g.phase == "podium") "Final results" else "Round ${g.round} of ${g.totalRounds}"
    val total = when (g.phase) {
        "write" -> BluffBattle.WRITE_MS
        "pick" -> BluffBattle.PICK_MS
        "scores" -> BluffBattle.SCORES_MS
        "podium" -> BluffBattle.PODIUM_MS
        else -> BluffBattle.REVEAL_STEP_MS * g.reveal.size + BluffBattle.REVEAL_TAIL_MS
    }
    val status = when (g.phase) {
        "write" -> "${g.submitted}/${g.expected} bluffs in"
        "pick" -> "${g.submitted}/${g.expected} picked"
        else -> null
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Header("BLUFF BATTLE", subtitle, stage, total, badge, status)
        Spacer(Modifier.height(12.dp))
        when (g.phase) {
            "write" -> WritePhase(g)
            "pick" -> PickPhase(g)
            "reveal" -> RevealPhase(g, stage.phaseSeq)
            "scores" -> ScoresPhase(g, scores)
            else -> Podium(scores)
        }
    }
}

@Composable
private fun PromptCard(text: String, big: Boolean) {
    Box(
        Modifier.fillMaxWidth().background(Party.FeltDeep, RoundedCornerShape(24.dp))
            .border(3.dp, Party.Brass, RoundedCornerShape(24.dp)).padding(horizontal = 36.dp, vertical = if (big) 44.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (big) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
            color = Party.Text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WritePhase(g: BluffTv) = Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.weight(0.5f))
    PromptCard(g.prompt, big = true)
    Spacer(Modifier.height(24.dp))
    Text("Write a fake answer on your phone that sounds true", style = MaterialTheme.typography.titleLarge, color = Party.Muted)
    Spacer(Modifier.weight(1f))
}

@Composable
private fun PickPhase(g: BluffTv) = Column(Modifier.fillMaxSize()) {
    PromptCard(g.prompt, big = false)
    Spacer(Modifier.height(12.dp))
    val n = g.options.size
    val cols = when {
        n <= 4 -> 2
        n <= 9 -> 3
        else -> 4
    }
    val dense = n > 9
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        g.options.chunked(cols).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { c, text ->
                    Stagger(r * cols + c, Modifier.weight(1f)) { OptionCard(text, Party.Card, Party.Line, dense) }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OptionCard(text: String, fill: Color, edge: Color, dense: Boolean = false, content: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(fill, RoundedCornerShape(14.dp)).border(2.dp, edge, RoundedCornerShape(14.dp))
            .padding(horizontal = if (dense) 12.dp else 20.dp, vertical = if (dense) 8.dp else 14.dp),
    ) {
        Text(
            text, style = if (dense) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineMedium,
            color = Party.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

@Composable
private fun RevealPhase(g: BluffTv, phaseSeq: Int) {
    var shown by remember(phaseSeq) { mutableIntStateOf(0) }
    LaunchedEffect(phaseSeq) {
        while (shown < g.reveal.size) {
            delay(if (shown == 0) 600 else BluffBattle.REVEAL_STEP_MS)
            shown++
        }
    }
    Column(Modifier.fillMaxSize()) {
        PromptCard(g.prompt, big = false)
        Spacer(Modifier.height(14.dp))
        val current = g.reveal.getOrNull(shown - 1)
        if (current == null) {
            Text("Let's see who got fooled…", style = MaterialTheme.typography.headlineMedium, color = Party.Muted)
        } else {
            key(shown) { Stagger(0) { Spotlight(current) } }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            g.reveal.take((shown - 1).coerceAtLeast(0)).forEach { RevealChip(it) }
        }
    }
}

private fun BluffReveal.caption(): String {
    val truth = kind == "truth"
    val who = when (kind) {
        "truth" -> "THE TRUTH"
        "decoy" -> "House fake"
        else -> "Fake by ${authors.joinToString(" & ")}"
    }
    val fooledText = if (fooled.isEmpty()) (if (truth) "Nobody found it!" else "Fooled nobody")
    else (if (truth) "Found by " else "Fooled ") + fooled.joinToString(", ")
    return "$who · $fooledText"
}

@Composable
private fun Spotlight(item: BluffReveal) {
    val truth = item.kind == "truth"
    OptionCard(item.text, fill = if (truth) Party.Gold.copy(alpha = 0.22f) else Party.Card, edge = if (truth) Party.Gold else Party.Pink) {
        Text(item.caption(), style = MaterialTheme.typography.titleLarge, color = if (truth) Party.Gold else Party.Pink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RevealChip(item: BluffReveal) = Text(
    "${item.text} · ${item.fooled.size} fooled",
    style = MaterialTheme.typography.bodyMedium, color = Party.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
    modifier = Modifier.widthIn(max = 260.dp).background(Party.Card, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
)

@Composable
private fun ScoresPhase(g: BluffTv, scores: List<ScoreRow>) {
    val deltas = g.deltas.associate { it.id to it.points }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        scores.take(8).forEachIndexed { i, row ->
            Stagger(i) {
                Row(
                    Modifier.fillMaxWidth().background(Party.Card, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.headlineMedium, color = Party.Muted, modifier = Modifier.width(40.dp))
                    AvatarDot(row.avatar, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(row.name, style = MaterialTheme.typography.headlineMedium, color = Party.Text, modifier = Modifier.weight(1f))
                    deltas[row.id]?.let { Text("+$it", style = MaterialTheme.typography.headlineMedium, color = Party.Mint) }
                    Spacer(Modifier.width(20.dp))
                    Text("%,d".format(row.score), style = MaterialTheme.typography.headlineMedium, color = Party.Gold)
                }
            }
        }
        if (scores.size > 8) Text("+ ${scores.size - 8} more", style = MaterialTheme.typography.bodyLarge, color = Party.Muted)
    }
}

@Composable
private fun Podium(scores: List<ScoreRow>) {
    val top = scores.take(3)
    val order = listOf(1, 0, 2).filter { it < top.size }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        order.forEachIndexed { i, rank ->
            val row = top[rank]
            val height = listOf(300.dp, 220.dp, 160.dp)[rank]
            Stagger(i) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarDot(row.avatar, 72.dp)
                    Text(row.name, style = MaterialTheme.typography.headlineMedium, color = Party.Text)
                    Text("%,d".format(row.score), style = MaterialTheme.typography.titleLarge, color = Party.Gold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.width(200.dp).height(height).background(if (rank == 0) Party.Brass else Party.FeltDeep, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                        contentAlignment = Alignment.TopCenter,
                    ) { Text("${rank + 1}", style = MaterialTheme.typography.displayLarge, color = if (rank == 0) Party.Ink else Party.Brass, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PausedOverlay(reason: String?) = Box(Modifier.fillMaxSize().background(Party.Ink.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PAUSED", style = MaterialTheme.typography.displayLarge, color = Party.Text)
        Text(
            when (reason) {
                "WAITING_FOR_PLAYERS" -> "Waiting for players to reconnect · press Back for host controls"
                "RESTORED" -> "Picked up where you left off · press Back and choose Resume"
                else -> "Press Back for host controls"
            },
            style = MaterialTheme.typography.titleLarge, color = Party.Muted,
        )
    }
}

/** Fades and slides children in one after another. */
@Composable
private fun Stagger(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(state, modifier, enter = fadeIn(tween(250, delayMillis = index * 70)) + slideInVertically(tween(250, delayMillis = index * 70)) { it / 3 }) {
        content()
    }
}
