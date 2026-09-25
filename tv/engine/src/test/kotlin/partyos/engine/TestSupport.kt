package partyos.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertIs

/** Deterministic entropy so two engines built with the same seed behave identically. */
class SeededEntropy(seed: Long) : Entropy {
    private val r = java.util.Random(seed)
    override fun token() = (1..4).joinToString("") { "%08x".format(r.nextInt()) }
    override fun nextInt(bound: Int) = r.nextInt(bound)
    override fun nextLong() = r.nextLong()
}

@Serializable
data class CState(val phase: String, val order: List<PlayerId>, val participants: Set<PlayerId>, val tapped: Set<PlayerId> = emptySet())

/** Minimal game: one 10 s input phase where each participant taps once (+10), then a 5 s "done" phase, then finish. */
object CountdownGame : GameModule<CState> {
    override val info = GameInfo(
        id = "countdown", title = "Countdown", tagline = "Tap!", minPlayers = 2, maxPlayers = 16,
        tutorial = listOf(TutorialCard("Tap", "Tap once.")), lateJoin = LateJoin.NEXT_GAME,
    )
    override val stateSerializer = CState.serializer()
    override fun start(ctx: GameContext): Step<CState> {
        val ps = ctx.players.map { it.id }
        return Step(CState("input", ps.shuffled(ctx.random), ps.toSet()), listOf(Effect.Phase(10_000)))
    }
    override fun onAction(s: CState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<CState> {
        if (payload["kind"]?.jsonPrimitive?.content != "tap") throw Reject("BAD_ACTION")
        if (s.phase != "input" || who !in s.participants) throw Reject("NOT_NOW")
        if (who in s.tapped) return Step(s)
        return Step(s.copy(tapped = s.tapped + who), listOf(Effect.Award(who, 10, "tap")))
    }
    override fun onDeadline(s: CState, ctx: GameContext): Step<CState> = when (s.phase) {
        "input" -> Step(s.copy(phase = "done"), listOf(Effect.Phase(5_000)))
        else -> Step(s, listOf(Effect.Finish))
    }
    override fun waitingOn(s: CState) = if (s.phase == "input") s.participants - s.tapped else null
    override fun tvView(s: CState, ctx: GameContext) = GenericTv(s.phase, s.order.map { it.v })
    override fun playerView(s: CState, who: PlayerId, ctx: GameContext): Screen =
        if (s.phase == "input" && who !in s.tapped) Screen.ChoiceList("Tap!", listOf(Choice("tap", "Tap")), null, "tap")
        else Screen.Waiting("Waiting")
}

val tap: JsonObject = buildJsonObject { put("kind", JsonPrimitive("tap")) }
val ack: JsonObject = buildJsonObject { put("kind", JsonPrimitive("ack")) }

fun PartyEngine.add(name: String, role: Role = Role.PLAYER, connected: Boolean = true): PlayerId {
    val j = assertIs<JoinResult.Joined>(join(roomCode, name, Avatar("🙂", "#123456"), role))
    if (connected) setPresence(j.player.id, true)
    return j.player.id
}
