package partyos.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import partyos.engine.games.trivia.TriviaPack
import java.io.IOException
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OpenTriviaFeedTest {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun q(question: String, answer: String, wrong: List<String>, difficulty: String = "easy", type: String = "multiple", category: String = "Entertainment: Film") =
        """{"type":"$type","difficulty":"$difficulty","category":"${enc(category)}","question":"${enc(question)}",""" +
            """"correct_answer":"${enc(answer)}","incorrect_answers":[${wrong.joinToString { "\"${enc(it)}\"" }}]}"""

    private fun reply(code: Int, vararg qs: String) = """{"response_code":$code,"results":[${qs.joinToString()}]}"""
    private val tokenReply = """{"response_code":0,"response_message":"Token Generated Successfully!","token":"abc"}"""
    private val jaws = q("Who directed  Jaws?", "Steven Spielberg ", listOf("George Lucas", "James Cameron", "Ridley Scott"))
    private val lakes = q("Which is the largest Great Lake?", "Superior", listOf("Erie", "Huron", "Ontario"), category = "Geography")

    /** A feed on virtual time whose "server" answers from [answer]; every URL it asks for lands in [calls] with the time. */
    private class Harness(scope: TestScope, answer: (String) -> String) {
        val calls = mutableListOf<Pair<String, Long>>()
        val errors = mutableListOf<Throwable>()
        val feed = OpenTriviaFeed(
            scope, // not backgroundScope: advanceUntilIdle() skips background work, and every fill here finishes
            get = { url -> calls += url to scope.testScheduler.currentTime; answer(url) },
            now = { scope.testScheduler.currentTime },
            onError = { errors += it },
        )
        val urls get() = calls.map { it.first }
    }

    @Test fun keepsOnlyQuestionsThatFitTheShowAndRespectsTheRateLimit() = runTest {
        val h = Harness(this) { url ->
            if ("api_token" in url) tokenReply else reply(
                0,
                jaws,
                q("How many?", "A", listOf("B", "C", "D"), difficulty = "hard"),
                q("The moon is made of cheese.", "False", listOf("True"), type = "boolean"),
                q("Too long?", "An answer far too long to fit on a TV answer tile", listOf("B", "C", "D")),
                q("Duplicate answers?", "Same", listOf("same", "Other", "Another")),
                lakes,
            )
        }
        h.feed.warm()
        advanceUntilIdle()
        assertEquals(
            listOf("https://opentdb.com/api_token.php?command=request", "https://opentdb.com/api.php?amount=50&type=multiple&encode=url3986&token=abc"),
            h.urls,
        )
        assertTrue(h.calls[1].second - h.calls[0].second >= 5_000, "one call per 5 seconds")
        assertEquals(2, h.feed.ready)

        val first = assertNotNull(h.feed.take(emptySet(), null))
        assertEquals("Who directed Jaws?", first.prompt) // whitespace tidied
        assertEquals("Film", first.category) // "Entertainment: " dropped
        assertEquals("Steven Spielberg", first.answer)
        assertEquals(3, first.wrong.size)
        assertEquals(OpenTriviaFeed.SOURCE, first.source)
        assertTrue(first.id.startsWith("tlive-"))
        assertEquals("Geography", assertNotNull(h.feed.take(emptySet(), null)).category)
        assertNull(h.feed.take(emptySet(), null))
    }

    @Test fun prefersANewTopicSkipsUsedQuestionsAndNeverRepeatsOne() = runTest {
        val h = Harness(this) { url -> if ("api_token" in url) tokenReply else reply(0, jaws, lakes) }
        h.feed.warm()
        advanceUntilIdle()
        val lake = assertNotNull(h.feed.take(emptySet(), avoidCategory = "Film"))
        assertEquals("Geography", lake.category)
        // That take started a refill. The API sends both again, but a question already handed out is never buffered again.
        advanceUntilIdle()
        assertEquals(3, h.calls.size)
        assertEquals(1, h.feed.ready)

        // A fresh feed (say, after a restart) still skips whatever the party has already used.
        val other = Harness(this) { url -> if ("api_token" in url) tokenReply else reply(0, jaws, lakes) }
        other.feed.warm()
        advanceUntilIdle()
        assertEquals("Film", assertNotNull(other.feed.take(setOf(lake.id), avoidCategory = "Film")).category)
        assertNull(other.feed.take(setOf(lake.id), null))
    }

    @Test fun anExpiredTokenIsReplacedAndAnExhaustedOneIsReset() = runTest {
        var questionsReply = reply(3) // token not found
        val h = Harness(this) { url ->
            when {
                "command=request" in url -> tokenReply
                "command=reset" in url -> """{"response_code":0,"token":"abc"}"""
                else -> questionsReply
            }
        }
        h.feed.warm()
        advanceUntilIdle()
        questionsReply = reply(4) // token empty
        h.feed.warm()
        advanceUntilIdle()
        questionsReply = reply(0, jaws)
        h.feed.warm()
        advanceUntilIdle()
        assertEquals(
            listOf("command=request", "api.php", "command=request", "api.php", "command=reset", "api.php"),
            h.urls.map { u -> listOf("command=request", "command=reset", "api.php").first { it in u } },
        )
        assertEquals(1, h.feed.ready)
    }

    /** The real API (opt-in: `./gradlew :server:test -PliveTrivia --tests '*OpenTriviaFeedTest*'`). */
    @Test fun fetchesRealQuestionsFromOpenTriviaDb() {
        assumeTrue(System.getProperty("liveTrivia") == "true", "live API test skipped (pass -PliveTrivia)")
        runBlocking {
            val errors = mutableListOf<Throwable>()
            val feed = OpenTriviaFeed(CoroutineScope(Dispatchers.IO), onError = { errors += it })
            feed.warm()
            withTimeout(30_000) { while (feed.ready == 0 && errors.isEmpty()) delay(250) }
            assertEquals(emptyList(), errors.map { it.toString() })
            println("Open Trivia DB: ${feed.ready} questions ready")
            repeat(3) {
                val item = assertNotNull(feed.take(emptySet(), null))
                println("  [${item.category}] ${item.prompt} -> ${item.answer} (not ${item.wrong.joinToString(" / ")})")
                assertNull(TriviaPack.mcProblem(item))
            }
        }
    }

    @Test fun offlineItBacksOffInsteadOfHammering() = runTest {
        var online = false
        val h = Harness(this) { url -> if (!online) throw IOException("no network") else if ("api_token" in url) tokenReply else reply(0, jaws) }
        h.feed.warm()
        advanceUntilIdle()
        assertEquals(1, h.errors.size)
        assertNull(h.feed.take(emptySet(), null)) // nothing ready, and no retry during the back-off
        h.feed.warm()
        advanceUntilIdle()
        assertEquals(1, h.calls.size)

        online = true
        advanceTimeBy(OpenTriviaFeed.BACKOFF_MS + 1)
        h.feed.warm()
        advanceUntilIdle()
        assertEquals(1, h.feed.ready)
    }
}
