package com.partyos.tv.perf

import com.partyos.tv.PartyRuntime
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.Avatar
import partyos.engine.BluffTv
import partyos.engine.HostCmd
import partyos.engine.JoinResult
import partyos.engine.PlayerId
import partyos.engine.Role
import partyos.engine.Screen

/**
 * Drives the real UI through lobby → tutorial → one full Bluff Battle round (write, pick, reveal, scores)
 * with 16 in-process bot players, recording frame timing the whole way. Bots are removed afterwards.
 */
class BenchmarkTour(private val runtime: PartyRuntime, private val perf: PerfMonitor) {
    private val emoji = listOf("🦊", "🐸", "🐙", "🦄", "🐯", "🐼", "🦖", "🐝", "👽", "🤖", "🎃", "🍕", "🌮", "🚀", "🎸", "💎")
    private val colors = listOf("#FF4D8D", "#FF7A00", "#FFD23F", "#3DDC97", "#2EC4F1", "#6C63FF", "#B15CFF", "#F5F5F5")

    suspend fun run(): PerfReport? {
        val host = runtime.live.value?.host ?: return null
        if (host.tv.value.stage != null) return null
        perf.setEnabled(true)
        perf.startRecording()
        val bots = mutableListOf<PlayerId>()
        try {
            val room = host.tv.value.roomCode
            for (i in 0 until 16) {
                val r = host.mutate { join(room, "Bot ${i + 1}", Avatar(emoji[i], colors[i % colors.size]), Role.PLAYER) }
                if (r is JoinResult.Joined) {
                    bots += r.player.id
                    host.mutate { setPresence(r.player.id, true) }
                }
                delay(180)
            }
            delay(1_500)
            host.mutate { host(HostCmd.StartGame("bluff", mapOf("rounds" to 3))) }
            delay(4_000)
            bots.forEachIndexed { i, id -> act(id, "ack$i") { JsonObject(mapOf("kind" to JsonPrimitive("ack"))) }; delay(60) }
            delay(3_000)
            bots.forEachIndexed { i, id ->
                act(id, "w$i") { JsonObject(mapOf("kind" to JsonPrimitive("write"), "text" to JsonPrimitive("Bot bluff number ${i + 1}"))) }
                delay(250)
            }
            delay(2_000)
            bots.forEachIndexed { i, id ->
                val option = host.read { (phoneState(id).screen as? Screen.ChoiceList)?.options?.let { it[i % it.size].id } }
                if (option != null) act(id, "p$i") { JsonObject(mapOf("kind" to JsonPrimitive("pick"), "option" to JsonPrimitive(option))) }
                delay(200)
            }
            // Let the reveal and scoreboard animations play out in full.
            while ((host.tv.value.stage?.game as? BluffTv)?.phase.let { it == "reveal" || it == "pick" }) delay(250)
            delay(8_500)
            return perf.stopRecording("bluff-round")
        } finally {
            host.mutate { if (tvState().stage != null) host(HostCmd.EndGame) }
            bots.forEach { id -> host.mutate { kick(id) } }
        }
    }

    private suspend fun act(id: PlayerId, actionId: String, payload: () -> JsonObject) {
        val host = runtime.live.value?.host ?: return
        host.mutate { action(id, "tour-$actionId", tvState().stage?.phaseSeq ?: 0, payload()) }
    }
}
