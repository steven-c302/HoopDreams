package partyos.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Serializable
data class HState(val phase: Int = 1, val bumps: Int = 0, val seenRemaining: Long? = null, val offers: Int = 0)

/**
 * A game that uses the Home Turf hooks: "bump" resets the deadline without a new phase, "next" starts a new phase,
 * "offer" is phase-free, and every action records the time the engine says is left.
 */
object HookGame : GameModule<HState> {
    override val info = GameInfo("hooks", "Hooks", "", 2, 16, emptyList(), LateJoin.ANYTIME)
    override val stateSerializer = HState.serializer()
    override fun start(ctx: GameContext) = Step(HState(), listOf(Effect.Phase(10_000)))
    override fun onAction(s: HState, who: PlayerId, payload: JsonObject, ctx: GameContext): Step<HState> {
        val seen = s.copy(seenRemaining = ctx.remainingMs)
        return when (payload["kind"]?.jsonPrimitive?.content) {
            "bump" -> Step(seen.copy(bumps = s.bumps + 1), listOf(Effect.Deadline(6_000)))
            "next" -> Step(seen.copy(phase = s.phase + 1), listOf(Effect.Phase(10_000)))
            "offer" -> Step(seen.copy(offers = s.offers + 1))
            "peek" -> Step(seen)
            else -> throw Reject("BAD")
        }
    }
    override fun onDeadline(s: HState, ctx: GameContext) = Step(s.copy(phase = s.phase + 1), listOf(Effect.Phase(10_000)))
    override fun waitingOn(s: HState): Set<PlayerId>? = null
    override fun tvView(s: HState, ctx: GameContext) = GenericTv("hooks", listOf("${s.phase}"))
    override fun playerView(s: HState, who: PlayerId, ctx: GameContext): Screen = Screen.Waiting("hooks")
    override fun phaseFree(payload: JsonObject) = payload["kind"]?.jsonPrimitive?.content == "offer"
}

class EngineHooksTest {
    private val clock = FakeClock(0)
    private val e = PartyEngine(clock, SeededEntropy(1), GameRegistry(listOf(HookGame)))
    private var n = 0
    private val a = e.add("Ava")
    private val b = e.add("Ben")

    init { e.host(HostCmd.StartGame("hooks")) }

    private fun kind(k: String) = buildJsonObject { put("kind", JsonPrimitive(k)) }
    private fun act(k: String, round: Int = seq) = e.action(a, "x${n++}", round, kind(k))
    private val seq get() = e.tvState().stage!!.phaseSeq
    private val phase get() = (e.tvState().stage!!.game as GenericTv).lines.single().toInt()
    private val remaining get() = e.tvState().stage!!.remainingMs
    private val saved get() = Json.decodeFromJsonElement(HState.serializer(), e.snapshot().game!!.state!!)

    @Test fun deadlineResetsTheClockWithoutANewPhase() {
        val before = seq
        clock.advance(8_000)
        assertEquals(2_000, remaining)
        assertEquals(ActionResult.Ack, act("bump"))
        assertEquals(before, seq, "no new round")
        assertEquals(6_000, remaining)
        clock.advance(5_999); e.tick()
        assertEquals(1, phase)
        clock.advance(1); e.tick()
        assertEquals(2, phase, "the reset deadline fires")
        assertEquals(before + 1, seq)
    }

    @Test fun aResetDeadlineSurvivesAPause() {
        clock.advance(3_000)
        act("bump")
        e.host(HostCmd.Pause)
        assertIs<ActionResult.Rejected>(act("bump"), "no actions while paused")
        clock.advance(60_000); e.tick()
        assertEquals(6_000, remaining, "paused time doesn't count")
        e.host(HostCmd.Resume)
        clock.advance(5_000); e.tick()
        assertEquals(1, phase)
        clock.advance(1_000); e.tick()
        assertEquals(2, phase)
    }

    @Test fun gamesSeeTheTimeLeft() {
        clock.advance(2_500)
        act("peek")
        assertEquals(7_500, saved.seenRemaining)
    }

    @Test fun phaseFreeActionsSurviveARoundChange() {
        val old = seq
        act("next")
        assertEquals(old + 1, seq)
        assertEquals(ActionResult.Rejected("STALE"), act("peek", old), "ordinary actions still go stale")
        assertEquals(ActionResult.Ack, act("offer", old), "the game opted this one out")
        assertEquals(1, saved.offers)
    }

    @Test fun turfSettingsAreAccepted() {
        assertEquals(ActionResult.Ack, e.host(HostCmd.SetOption("turfMode", 2)))
        assertEquals(ActionResult.Ack, e.host(HostCmd.SetOption("minutes", 45)))
        e.host(HostCmd.SetOption("minutes", 500))
        assertEquals(120, e.tvState().settings["minutes"])
        assertEquals(2, e.tvState().settings["turfMode"])
        assertIs<ActionResult.Rejected>(e.host(HostCmd.SetOption("bogus", 1)))
        assertEquals(b, e.tvState().players[1].id)
    }
}
