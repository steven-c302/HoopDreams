package com.partyos.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.partyos.tv.PartyRuntime
import com.partyos.tv.ui.bluff.GameStage
import com.partyos.tv.ui.components.Backdrop
import com.partyos.tv.ui.home.HomeScreen
import com.partyos.tv.ui.host.HostOverlay
import com.partyos.tv.ui.lobby.LobbyScreen
import com.partyos.tv.ui.settings.SettingsScreen
import com.partyos.tv.ui.theme.Party
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import partyos.engine.Role

enum class Route { Home, Lobby, Settings }

private val REJECTS = mapOf(
    "NOT_ENOUGH_PLAYERS" to "Need more players connected to start",
    "GAME_RUNNING" to "A game is already running",
)

@Composable
fun TvApp(runtime: PartyRuntime, menuPresses: Flow<Unit>, onBenchmark: () -> Unit, onExit: () -> Unit) {
    val tv by runtime.tv.collectAsState()
    val joinUrl by runtime.joinUrl.collectAsState()
    val ip by runtime.network.ip.collectAsState()
    val live by runtime.live.collectAsState()
    val settings by runtime.settings.settings.collectAsState(initial = null)
    var route by remember { mutableStateOf(Route.Home) }
    var overlay by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    val controller = remember { TvController(runtime) { code -> toast = REJECTS[code] ?: "Couldn't do that ($code)" } }

    LaunchedEffect(Unit) { menuPresses.collect { overlay = !overlay } }
    LaunchedEffect(toast) { if (toast != null) { delay(3_000); toast = null } }

    val state = tv
    val stage = state?.stage
    // Back never silently exits: in a game it opens host controls; at Home it asks before stopping the server.
    BackHandler {
        when {
            confirmExit -> confirmExit = false
            overlay -> overlay = false
            stage != null -> overlay = true
            route != Route.Home -> route = Route.Home
            else -> confirmExit = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state == null || settings == null -> Backdrop { Text("Starting PARTY OS…", style = MaterialTheme.typography.headlineLarge, color = Party.Text, modifier = Modifier.align(Alignment.Center)) }
            stage != null -> GameStage(stage, state.players.filter { it.role == Role.PLAYER }, state.scores)
            route == Route.Lobby -> LobbyScreen(state.roomCode, joinUrl, state.players, runtime.games.all.map { it.info }, state.lastResult) { g ->
                controller.start(g.id, settings!!.rounds)
            }
            route == Route.Settings -> SettingsScreen(settings!!, ip, live?.server?.port, joinUrl, controller) { route = Route.Lobby; onBenchmark() }
            else -> HomeScreen(
                playersOnline = state.players.count { it.connected },
                onPlay = { route = Route.Lobby },
                onQuickPlay = {
                    val online = state.players.count { it.connected && it.role == Role.PLAYER }
                    val eligible = runtime.games.all.map { it.info }.filter { online >= it.minPlayers }
                    route = Route.Lobby
                    if (eligible.isEmpty()) toast = "Get ${runtime.games.all.minOf { it.info.minPlayers }} players to join, then Quick Play"
                    else controller.start(eligible.random().id, settings!!.rounds)
                },
                onSettings = { route = Route.Settings },
            )
        }
        if (overlay && state != null) HostOverlay(stage, state.players, controller) { overlay = false }
        if (confirmExit) ExitDialog(playersOnline = state?.players?.count { it.connected } ?: 0, onStay = { confirmExit = false }, onExit = onExit)
        toast?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, color = Party.Text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).background(Party.Night, RoundedCornerShape(14.dp)).padding(horizontal = 24.dp, vertical = 14.dp))
        }
    }
}

@Composable
private fun ExitDialog(playersOnline: Int, onStay: () -> Unit, onExit: () -> Unit) {
    val stay = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { stay.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Party.Ink.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            Modifier.background(Party.Night, RoundedCornerShape(24.dp)).padding(28.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            Text("Stop hosting?", style = MaterialTheme.typography.headlineLarge, color = Party.Text)
            Text(
                if (playersOnline > 0) "$playersOnline connected ${if (playersOnline == 1) "phone" else "phones"} will be disconnected. Your party is saved."
                else "Phones won't be able to join until you open PARTY OS again. Your party is saved.",
                style = MaterialTheme.typography.bodyLarge, color = Party.Muted,
            )
            androidx.tv.material3.Button(onClick = onStay, modifier = Modifier.focusRequester(stay)) { Text("Keep hosting") }
            androidx.tv.material3.Button(onClick = onExit) { Text("Stop and exit") }
        }
    }
}
