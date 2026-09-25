package com.partyos.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.partyos.tv.ui.bluff.GameStage
import com.partyos.tv.ui.theme.PartyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import partyos.engine.BluffReveal
import partyos.engine.BluffTv
import partyos.engine.StageInfo
import partyos.engine.TutorialCard
import partyos.engine.TutorialView
import partyos.engine.games.bluff.BluffBattle

/** A full 16-player round must fit on a 960x540dp TV screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BluffStageLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val prompt = "Cleopatra lived closer in time to the Moon landing than to the building of ____."
    private val fakes = (1..16).map { "Fake answer number $it" }
    private fun stage(g: BluffTv?, tutorial: TutorialView? = null) =
        StageInfo("bluff", "Bluff Battle", 3, null, 20_000, false, null, tutorial, g)

    @Test fun allSeventeenPickOptionsAreOnScreen() {
        val g = BluffTv("pick", 1, 5, false, prompt, 0, 16, options = fakes + "the Great Pyramid")
        rule.mainClock.autoAdvance = false // the backdrop and countdown animate forever
        rule.setContent { PartyTheme { GameStage(stage(g), emptyList(), emptyList()) } }
        rule.mainClock.advanceTimeBy(3_000)
        (fakes + "the Great Pyramid").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun truthIsOnScreenAtTheEndOfASixteenPlayerReveal() {
        val reveal = fakes.map { BluffReveal(it, "fake", listOf("P"), emptyList()) } + BluffReveal("the Great Pyramid", "truth", emptyList(), listOf("Ava"))
        val g = BluffTv("reveal", 1, 5, false, prompt, 16, 16, reveal = reveal)
        rule.mainClock.autoAdvance = false
        rule.setContent { PartyTheme { GameStage(stage(g), emptyList(), emptyList()) } }
        rule.mainClock.advanceTimeBy(BluffBattle.REVEAL_STEP_MS * reveal.size + 1_000)
        rule.onNodeWithText("the Great Pyramid").assertIsDisplayed()
        rule.onNodeWithText("THE TRUTH · Found by Ava").assertIsDisplayed()
    }

    @Test fun tutorialCardsShowTheirWholeText() {
        val t = TutorialView(BluffBattle().info.tutorial, emptyList())
        rule.mainClock.autoAdvance = false
        rule.setContent { PartyTheme { GameStage(stage(null, t), emptyList(), emptyList()) } }
        rule.mainClock.advanceTimeBy(2_000)
        t.cards.forEach { rule.onNodeWithText(it.body).assertIsDisplayed() }
    }
}
