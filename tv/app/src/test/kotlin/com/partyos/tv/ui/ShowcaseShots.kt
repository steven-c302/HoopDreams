package com.partyos.tv.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.partyos.tv.ui.bluff.GameStage
import com.partyos.tv.ui.home.HomeScreen
import com.partyos.tv.ui.lobby.LobbyScreen
import com.partyos.tv.ui.theme.PartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import partyos.engine.Avatar
import partyos.engine.BjSeat
import partyos.engine.BlackjackTv
import partyos.engine.BluffDelta
import partyos.engine.PlayingCard
import partyos.engine.BluffReveal
import partyos.engine.BluffTv
import partyos.engine.PlayerId
import partyos.engine.PlayerSummary
import partyos.engine.Role
import partyos.engine.ScoreRow
import partyos.engine.StageInfo
import partyos.engine.TutorialView
import partyos.engine.games.bluff.BluffBattle
import java.io.File

/**
 * Renders the main TV screens to PNGs in app/build/showcase/ for design review.
 * Not an assertion suite: run with `./gradlew :app:testDebugUnitTest --tests '*ShowcaseShots*'`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShowcaseShots {
    @get:Rule val rule = createComposeRule()

    private val names = listOf("Ava", "Ben", "Cleo", "Dev", "Eli", "Fin", "Gus", "Hana", "Ivy", "Jay", "Kai", "Lu")
    private val emojis = listOf("🦊", "🐸", "🐙", "🦄", "🐼", "🐯", "🦉", "🐝", "🐧", "🦖", "🐨", "🍕")
    private val colors = listOf("#FF7A00", "#22AA55", "#8E5CFF", "#FF4D8D", "#2EC4F1", "#FFD23F", "#3DDC97", "#FF5A5A")
    private val players = names.mapIndexed { i, n ->
        PlayerSummary(PlayerId("p$i"), n, Avatar(emojis[i], colors[i % colors.size]), Role.PLAYER, i != 7)
    }
    private val scores = players.take(8).mapIndexed { i, p -> ScoreRow(p.id, p.name, p.avatar, 4200 - i * 450) }
    private val prompt = "Cleopatra lived closer in time to the Moon landing than to the building of ____."

    private fun stage(g: BluffTv?, tutorial: TutorialView? = null) =
        StageInfo("bluff", "Bluff Battle", 3, System.currentTimeMillis() + 14_000, 14_000, false, null, tutorial, g)

    private fun shot(name: String, advanceMs: Long, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false // backdrops animate forever
        rule.setContent { PartyTheme { content() } }
        rule.mainClock.advanceTimeBy(advanceMs)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/showcase").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shot("1-home", 1_500) { HomeScreen(11, {}, {}, {}) }

    @Test fun lobby() = shot("2-lobby", 1_500) {
        LobbyScreen("KXQT", "http://192.168.1.20:8080/j/KXQT", players, listOf(BluffBattle().info), null) {}
    }

    @Test fun tutorial() = shot("3-tutorial", 2_000) {
        GameStage(stage(null, TutorialView(BluffBattle().info.tutorial, players.take(5).map { it.id })), players, emptyList())
    }

    @Test fun write() = shot("4-write", 1_500) {
        GameStage(stage(BluffTv("write", 2, 5, false, prompt, 7, 11)), players, scores)
    }

    @Test fun pick() = shot("5-pick", 2_000) {
        val opts = listOf("the Great Pyramid", "the Colosseum", "Stonehenge", "the first Olympics", "the Great Wall", "Machu Picchu")
        GameStage(stage(BluffTv("pick", 2, 5, false, prompt, 4, 11, options = opts)), players, scores)
    }

    @Test fun reveal() = shot("6-reveal", 600L + BluffBattle.REVEAL_STEP_MS * 2 + 500) {
        val reveal = listOf(
            BluffReveal("the Colosseum", "fake", listOf("Ben"), listOf("Ava", "Cleo", "Dev")),
            BluffReveal("Stonehenge", "decoy", emptyList(), listOf("Eli")),
            BluffReveal("the Great Pyramid", "truth", emptyList(), listOf("Fin", "Gus")),
        )
        GameStage(stage(BluffTv("reveal", 2, 5, false, prompt, 11, 11, reveal = reveal)), players, scores)
    }

    @Test fun scores() = shot("7-scores", 2_500) {
        val deltas = scores.take(5).mapIndexed { i, s -> BluffDelta(s.id, s.name, 1000 - i * 200) }
        GameStage(stage(BluffTv("scores", 2, 5, false, prompt, 11, 11, deltas = deltas)), players, scores)
    }

    @Test fun podium() = shot("8-podium", 4_000) {
        GameStage(stage(BluffTv("podium", 5, 5, true, prompt, 11, 11)), players, scores)
    }

    private fun bj(phase: String, n: Int = 7): BlackjackTv {
        val hands = listOf(listOf(1, 13), listOf(10, 6, 9), listOf(7, 7), listOf(9, 11), listOf(5, 4, 12), listOf(2, 8, 10), listOf(12, 8))
        val seats = players.take(n).mapIndexed { i, p ->
            val cards = hands[i % hands.size].mapIndexed { k, r -> PlayingCard(r, (i + k) % 4) }
            val t = partyos.engine.games.blackjack.DrunkBlackjack.total(cards)
            val status = if (t > 21) "bust" else if (t == 21 && cards.size == 2) "blackjack" else if (phase == "play" && i % 3 == 0) "playing" else "stood"
            val outcome = when { phase != "settle" -> null; t > 21 -> "bust"; status == "blackjack" -> "blackjack"; t > 18 -> "win"; t == 18 -> "push"; else -> "lose" }
            val drinks = when (outcome) { "bust" -> 2; "lose" -> 1; "win" -> -1; "blackjack" -> -3; else -> 0 }
            BjSeat(p.id, p.name, p.avatar, cards, t, listOf(1, 2, 5)[i % 3], i == 2, status, outcome, if (outcome == null) null else drinks)
        }
        val dealer = if (phase == "play") listOf(PlayingCard(10, 1), PlayingCard(0, 0)) else listOf(PlayingCard(10, 1), PlayingCard(8, 3))
        return BlackjackTv(phase, 2, 5, false, "double_trouble", "Double Trouble", "Every drink this hand is doubled.", players[7].id, players[7].name, players[7].avatar, dealer, if (phase == "play") 10 else 18, 14, 6, seats, 4, n)
    }

    @Test fun blackjackPlay() = shot("9-blackjack-play", 2_000) { GameStage(StageInfo("blackjack", "Drunk Blackjack", 3, System.currentTimeMillis() + 14_000, 14_000, false, null, null, bj("play")), players, scores) }

    @Test fun blackjackSettle() = shot("10-blackjack-settle", 2_500) { GameStage(StageInfo("blackjack", "Drunk Blackjack", 3, System.currentTimeMillis() + 8_000, 8_000, false, null, null, bj("settle")), players, scores) }
}
