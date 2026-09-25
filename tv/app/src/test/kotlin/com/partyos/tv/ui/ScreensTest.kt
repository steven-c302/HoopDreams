package com.partyos.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import com.partyos.tv.ui.home.HomeScreen
import com.partyos.tv.ui.lobby.LobbyScreen
import com.partyos.tv.ui.theme.PartyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import partyos.engine.Avatar
import partyos.engine.PlayerId
import partyos.engine.PlayerSummary
import partyos.engine.Role
import partyos.engine.games.bluff.BluffBattle

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeShowsExactlyTheWorkingDestinations() {
        rule.setContent { PartyTheme { HomeScreen(0, {}, {}, {}) } }
        val tileTag = SemanticsMatcher("has a tile: tag") { n ->
            n.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("tile:") == true
        }
        val tags = rule.onAllNodes(tileTag).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(listOf("tile:Play Games", "tile:Quick Play", "tile:Settings"), tags)
    }

    @Test fun lobbyShowsQrRoomCodeAndPlayers() {
        val players = listOf(
            PlayerSummary(PlayerId("a"), "Ava", Avatar("🦊", "#FF7A00"), Role.PLAYER, true),
            PlayerSummary(PlayerId("b"), "Ben", Avatar("🐸", "#22AA55"), Role.PLAYER, false),
        )
        rule.setContent {
            PartyTheme { LobbyScreen("KXQT", "http://192.168.1.20:8080/j/KXQT", players, listOf(BluffBattle().info), null) {} }
        }
        rule.onNodeWithTag("roomCode").assertIsDisplayed()
        rule.onNodeWithText("KXQT").assertIsDisplayed()
        rule.onNodeWithContentDescription("QR code for http://192.168.1.20:8080/j/KXQT").assertIsDisplayed()
        rule.onNodeWithText("Ava").assertIsDisplayed()
        rule.onNodeWithText("Needs 3 players (1 ready)").assertIsDisplayed()
    }
}
