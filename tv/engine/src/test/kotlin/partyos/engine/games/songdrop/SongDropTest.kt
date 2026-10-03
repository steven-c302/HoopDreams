package partyos.engine.games.songdrop

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.ActionResult
import partyos.engine.FakeClock
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SeededEntropy
import partyos.engine.SongCard
import partyos.engine.SongDropTv
import partyos.engine.add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongDropTest {
    private val clock = FakeClock(0)
    private lateinit var e: PartyEngine
    private var n = 0

    private fun songJson(i: Int, year: Int) =
        """{"id":"s$i","title":"Title $i","artist":"Artist $i","year":$year,"genre":"pop","videoId":"vid${i.toString().padStart(8, '0')}","startSec":30}"""

    private val packJson = """{"packId":"t","title":"T","game":"songdrop","version":1,"items":[${(1..24).joinToString(",") { songJson(it, 1960 + it * 2) }}]}"""
    private val songs = SongPack.parse(packJson).items

    /** Starts Song Drop with [names]; the tutorial is skipped. `rounds` 3 means 6 songs. */
    private fun start(names: List<String>, settings: Map<String, Int> = mapOf("rounds" to 3), setup: (PartyEngine) -> Unit = {}): List<PlayerId> {
        e = PartyEngine(clock, SeededEntropy(7), GameRegistry(listOf(SongDrop(SongPack.parse(packJson)))))
        val ids = names.map { e.add(it) }
        setup(e)
        assertEquals(ActionResult.Ack, e.host(HostCmd.StartGame("songdrop", settings)))
        e.host(HostCmd.SkipPhase)
        return ids
    }

    private val tv get() = e.tvState().stage!!.game as SongDropTv
    private val round get() = e.tvState().stage!!.phaseSeq
    private fun ready(seq: Int = tv.clipSeq) = e.host(HostCmd.GameAction("ready:$seq"))
    private fun bad(seq: Int = tv.clipSeq) = e.host(HostCmd.GameAction("bad:$seq"))
    private fun label(videoId: String) = songs.first { it.videoId == videoId }.let { "${it.title} – ${it.artist}" }
    private fun options(who: PlayerId) = assertIs<Screen.ChoiceList>(e.phoneState(who).screen).options
    private fun right(who: PlayerId) = options(who).first { it.text == label(tv.videoId) }.id
    private fun wrong(who: PlayerId) = options(who).first { it.text != label(tv.videoId) }.id
    private fun pick(who: PlayerId, letter: String) =
        e.action(who, "p${n++}", round, buildJsonObject { put("kind", JsonPrimitive("pick")); put("option", JsonPrimitive(letter)) })
    private fun score(id: PlayerId) = e.tvState().scores.single { it.id == id }.score
    private fun solve(ids: List<PlayerId>) { ready(); ids.forEach { pick(it, right(it)) } }
    private fun nextSong() { clock.advance(SongDrop.REVEAL_MS); e.tick() }

    // ---- the clip handshake ---------------------------------------------------------------------

    @Test fun aSongStartsLoadingWithFourOptionsAndNothingScoresUntilTheTvIsPlaying() {
        val (a) = start(listOf("A", "B", "C"))
        assertEquals("load", tv.phase)
        assertEquals(1, tv.song)
        assertEquals(6, tv.totalSongs)
        assertTrue(tv.videoId.isNotEmpty())
        assertIs<Screen.Waiting>(e.phoneState(a).screen)
        assertEquals(ActionResult.Rejected("NOT_NOW"), pick(a, "a"))
    }

    @Test fun readyStartsTheFirstStageAndItsClockRunsForTwoSeconds() {
        start(listOf("A", "B", "C"))
        assertEquals(ActionResult.Ack, ready())
        assertEquals("stage", tv.phase)
        assertEquals(2_000L, e.tvState().stage!!.remainingMs)
        assertEquals(2_000L, tv.clipMs)
    }

    @Test fun aStaleOrRepeatedReadyChangesNothing() {
        start(listOf("A", "B", "C"))
        assertEquals(ActionResult.Ack, ready(seq = tv.clipSeq + 41))
        assertEquals("load", tv.phase)
        ready()
        clock.advance(500)
        ready()
        assertEquals(1_500L, e.tvState().stage!!.remainingMs, "a second ready must not restart the clock")
    }

    @Test fun noReadyInTenSecondsSkipsTheSongAndRemembersItAsBad() {
        start(listOf("A", "B", "C"))
        val first = tv.videoId
        clock.advance(SongDrop.LOAD_MS); e.tick()
        assertEquals("load", tv.phase)
        assertEquals(1, tv.song, "the replacement keeps the song number")
        assertNotEquals(first, tv.videoId)
        assertTrue(e.snapshot().memory.getValue(SongDrop.BAD_KEY).isNotBlank())
    }

    @Test fun aBadReportFromTheTvSkipsAtOnce() {
        start(listOf("A", "B", "C"))
        val first = tv.videoId
        assertEquals(ActionResult.Ack, bad())
        assertNotEquals(first, tv.videoId)
        assertEquals("load", tv.phase)
    }

    @Test fun threeBadSongsInARowEndTheGameWithAMessage() {
        val (a) = start(listOf("A", "B", "C"))
        bad(); bad(); bad()
        assertEquals("dead", tv.phase)
        assertEquals("Song Drop stopped", assertIs<Screen.Waiting>(e.phoneState(a).screen).title)
        clock.advance(SongDrop.DEAD_MS); e.tick()
        assertNull(e.tvState().stage, "the game is over")
    }

    @Test fun aSongThatPlaysResetsTheFailCount() {
        start(listOf("A", "B", "C"))
        bad(); bad()
        ready()
        bad() // mid-clip error: counts as the first fail of a new streak
        bad()
        assertEquals("load", tv.phase)
    }

    @Test fun readyWhilePausedIsRefused() {
        start(listOf("A", "B", "C"))
        e.host(HostCmd.Pause)
        assertEquals(ActionResult.Rejected("PAUSED"), ready())
    }

    @Test fun aTvThatReloadedMidClipAsksForAReplay() {
        start(listOf("A", "B", "C"))
        ready()
        val seq = tv.clipSeq
        assertEquals(ActionResult.Ack, e.host(HostCmd.GameAction("replay:$seq")))
        assertEquals("load", tv.phase)
        assertEquals(seq + 1, tv.clipSeq)
    }

    @Test fun unknownHostActionsAreRefused() {
        start(listOf("A", "B", "C"))
        assertEquals(ActionResult.Rejected("UNSUPPORTED"), e.host(HostCmd.GameAction("shuffle")))
        assertEquals(ActionResult.Rejected("BAD_ACTION"), e.host(HostCmd.GameAction("ready:x")))
    }

    // ---- answering ------------------------------------------------------------------------------

    @Test fun aRightAnswerScoresTheStageAndTheSpeedBonus() {
        val (a, b, c) = start(listOf("A", "B", "C"))
        ready()
        clock.advance(500)
        assertEquals(ActionResult.Ack, pick(a, right(a)))
        assertEquals(1225, score(a))
        assertEquals(listOf(a), tv.solvers.map { it.id })
        assertEquals(0, score(b) + score(c))
    }

    @Test fun aWrongAnswerLocksThePlayerOutOfTheSong() {
        val (a) = start(listOf("A", "B", "C"))
        ready()
        assertEquals(ActionResult.Ack, pick(a, wrong(a)))
        assertEquals("Locked out", assertIs<Screen.Waiting>(e.phoneState(a).screen).title)
        assertEquals(ActionResult.Rejected("LOCKED"), pick(a, "a"))
        assertEquals(listOf(a), tv.lockedOut)
    }

    @Test fun answeringTwiceAfterBeingRightDoesNothing() {
        val (a) = start(listOf("A", "B", "C"))
        ready()
        pick(a, right(a))
        val before = score(a)
        assertEquals(ActionResult.Rejected("NOT_NOW"), pick(a, "b"))
        assertEquals(before, score(a))
    }

    @Test fun anUnknownOptionIsRefused() {
        val (a) = start(listOf("A", "B", "C"))
        ready()
        assertEquals(ActionResult.Rejected("BAD_OPTION"), pick(a, "z"))
        assertEquals(ActionResult.Rejected("BAD_OPTION"), pick(a, "e"))
    }

    @Test fun theSongRevealsAtOnceWhenEveryoneIsDone() {
        val (a, b, c) = start(listOf("A", "B", "C"))
        ready()
        pick(a, right(a)); pick(b, wrong(b)); pick(c, wrong(c))
        assertEquals("reveal", tv.phase)
        val card = assertIs<SongCard>(tv.card)
        assertEquals(label(tv.videoId), "${card.title} – ${card.artist}")
    }

    @Test fun everyoneWrongRevealsWithoutHangingOrGrowingTheClip() {
        val (a, b, c) = start(listOf("A", "B", "C"))
        ready()
        listOf(a, b, c).forEach { pick(it, wrong(it)) }
        assertEquals("reveal", tv.phase)
        assertEquals(0, score(a) + score(b) + score(c))
    }

    @Test fun aPlayerWhoDisconnectsIsNotWaitedOn() {
        // Three players, so the game keeps running (and doesn't auto-pause for too few players) with one gone.
        val (a, b, c) = start(listOf("A", "B", "C"))
        ready()
        e.setPresence(c, false)
        pick(a, wrong(a))
        pick(b, wrong(b))
        assertEquals("reveal", tv.phase)
    }

    @Test fun aPlayerWhoJoinsMidSongWatchesUntilTheNextOne() {
        start(listOf("A", "B", "C"))
        ready()
        val late = e.add("Late")
        assertEquals("You're in next song", assertIs<Screen.Waiting>(e.phoneState(late).screen).title)
        assertEquals(ActionResult.Rejected("NEXT_ROUND"), pick(late, "a"))
    }

    // ---- stages ---------------------------------------------------------------------------------

    @Test fun ifNobodyHasItTheClipGrowsAndTheNextStageStartsAfterReady() {
        start(listOf("A", "B", "C"))
        ready()
        val first = tv.clipSeq
        clock.advance(2_000); e.tick()
        assertEquals("load", tv.phase)
        assertEquals(1, tv.stage)
        assertEquals(first + 1, tv.clipSeq)
        ready()
        assertEquals("stage", tv.phase)
        assertEquals(4_000L, e.tvState().stage!!.remainingMs)
    }

    @Test fun aLaterStageScoresLessButStillWithTheSpeedBonus() {
        val (a) = start(listOf("A", "B", "C"))
        ready()
        clock.advance(2_000); e.tick(); ready()
        pick(a, right(a))
        assertEquals(1000, score(a))
    }

    @Test fun afterTheLastStageTheSongRevealsWithNoPoints() {
        val ids = start(listOf("A", "B", "C"))
        repeat(3) { ready(); clock.advance(SongRules.CLIP_MS[tv.stage]); e.tick() }
        ready(); clock.advance(15_000); e.tick()
        assertEquals("reveal", tv.phase)
        assertTrue(ids.all { score(it) == 0 })
        assertEquals(3, tv.drinks.size)
    }

    // ---- a whole game ---------------------------------------------------------------------------

    @Test fun theLastSongCountsDoubleAndThePodiumFollows() {
        val ids = start(listOf("A", "B", "C"))
        repeat(5) { solve(ids); nextSong() }
        assertEquals(6, tv.song)
        assertTrue(tv.finalSong)
        solve(ids)
        assertEquals(5 * 1300 + 2600, score(ids[0]))
        nextSong()
        assertEquals("podium", tv.phase)
        assertEquals(6, tv.gallery.size)
    }

    @Test fun noSongRepeatsWithinAGame() {
        val ids = start(listOf("A", "B", "C"))
        val seen = mutableListOf<String>()
        repeat(6) { seen += tv.videoId; solve(ids); nextSong() }
        assertEquals(6, seen.toSet().size)
    }

    @Test fun theEraSettingKeepsEverySongInThatEra() {
        // The test pack has five songs from the 90s (era 3), so play five: after that the game falls back to any era.
        val ids = start(listOf("A", "B", "C"), mapOf("rounds" to 3, "era" to 3))
        repeat(5) {
            val year = songs.first { s -> s.videoId == tv.videoId }.year
            assertEquals(3, SongRules.eraOf(year))
            solve(ids); nextSong()
        }
    }

    @Test fun drinkCallsFollowTheLobbySwitch() {
        val (a, b, c) = start(listOf("A", "B", "C"))
        ready()
        pick(a, right(a)); pick(b, wrong(b)); pick(c, wrong(c))
        assertEquals(setOf(b, c), tv.drinks.map { it.id }.toSet())
        assertEquals("Missed it. Drink 1 sip", tv.drinks.first().text)
        val off = start(listOf("A", "B", "C")) { it.host(HostCmd.SetOption("drinks", 0)) }
        ready()
        off.forEach { pick(it, wrong(it)) }
        assertTrue(tv.drinks.isEmpty())
    }

    // ---- phones ---------------------------------------------------------------------------------

    @Test fun phonesSeeFourShapeOptionsLabelledWithTitleAndArtist() {
        val (a) = start(listOf("A", "B", "C"))
        ready()
        val screen = assertIs<Screen.ChoiceList>(e.phoneState(a).screen)
        assertEquals("shapes", screen.style)
        assertEquals(listOf("a", "b", "c", "d"), screen.options.map { it.id })
        assertTrue(screen.options.any { it.text == label(tv.videoId) })
        assertTrue(screen.prompt.startsWith("Name that song"))
    }

    @Test fun theTvViewHidesTheSongUntilItsRevealedAndNeverShowsAVideoOutsideASong() {
        val ids = start(listOf("A", "B", "C"))
        ready()
        assertNull(tv.card)
        ids.forEach { pick(it, wrong(it)) }
        assertTrue(tv.card != null)
        repeat(5) { nextSong(); solve(ids) }
        nextSong()
        assertEquals("", tv.videoId)
    }
}
