package partyos.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import partyos.engine.games.trivia.McItem
import partyos.engine.games.trivia.TriviaFeed
import partyos.engine.games.trivia.TriviaPack
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live BRAIN DRAIN questions from Open Trivia DB (opentdb.com, CC BY-SA 4.0) for when the bundled multiple-choice
 * questions run out. A small buffer fills in the background so the engine never waits on the network:
 * - one request at a time, never faster than the API's one call per 5 seconds;
 * - a session token, so batches don't repeat;
 * - a pause after a failure, so an offline party doesn't keep trying.
 * Only easy and medium questions are kept, and anything that wouldn't fit the TV is dropped (the bundled pack's rules).
 */
class OpenTriviaFeed(
    private val scope: CoroutineScope,
    private val get: suspend (url: String) -> String = ::httpGet,
    private val now: () -> Long = System::currentTimeMillis,
    private val onError: (Throwable) -> Unit = {},
) : TriviaFeed {
    private val held = ArrayDeque<McItem>()
    /** Ids already handed to a show (guarded by [held]); never buffered again. */
    private val given = HashSet<String>()
    private val filling = AtomicBoolean(false)
    @Volatile private var token: String? = null
    @Volatile private var lastCall = -GAP_MS
    @Volatile private var retryAt = 0L

    /** Questions ready to hand over. */
    val ready: Int get() = synchronized(held) { held.size }

    override fun take(used: Set<String>, avoidCategory: String?): McItem? {
        val pick = synchronized(held) {
            held.removeAll { it.id in used }
            (held.firstOrNull { it.category != avoidCategory } ?: held.firstOrNull())?.also { held.remove(it); given += it.id }
        }
        warm()
        return pick
    }

    override fun warm() {
        if (ready >= LOW || now() < retryAt || !filling.compareAndSet(false, true)) return
        scope.launch {
            try {
                fill()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                retryAt = now() + BACKOFF_MS
                onError(e)
            } finally {
                filling.set(false)
            }
        }
    }

    /** One batch: a session token if there isn't one yet, then up to [BATCH] questions. */
    private suspend fun fill() {
        val tok = token ?: json.decodeFromString<TokenReply>(call("$BASE/api_token.php?command=request")).token?.also { token = it }
        val reply = json.decodeFromString<Reply>(call("$BASE/api.php?amount=$BATCH&type=multiple&encode=url3986" + (tok?.let { "&token=$it" } ?: "")))
        when (reply.code) {
            OK -> {
                val fresh = reply.results.mapNotNull(::toItem)
                synchronized(held) {
                    val have = held.mapTo(HashSet()) { it.id } + given
                    fresh.filterTo(held) { it.id !in have }
                }
            }
            TOKEN_NOT_FOUND -> token = null // expired after 6 idle hours: the next fill asks for a new one
            TOKEN_EMPTY -> tok?.let { call("$BASE/api_token.php?command=reset&token=$it") } // seen them all: start over
            else -> retryAt = now() + BACKOFF_MS // rate limited or nothing to give
        }
    }

    private suspend fun call(url: String): String {
        val wait = lastCall + GAP_MS - now()
        if (wait > 0) delay(wait)
        lastCall = now()
        return get(url)
    }

    private fun toItem(r: Question): McItem? {
        if (r.type != "multiple" || r.difficulty == "hard") return null
        val prompt = decode(r.question)
        return McItem(
            id = "tlive-" + sha1(prompt.lowercase()).take(12),
            category = decode(r.category).substringAfter(": "), // "Entertainment: Film" → "Film"
            prompt = prompt,
            answer = decode(r.correctAnswer),
            wrong = r.incorrectAnswers.map(::decode),
            source = SOURCE,
        ).takeIf { TriviaPack.mcProblem(it) == null }
    }

    @Serializable
    private class TokenReply(@SerialName("response_code") val code: Int, val token: String? = null)

    @Serializable
    private class Reply(@SerialName("response_code") val code: Int, val results: List<Question> = emptyList())

    @Serializable
    private class Question(
        val type: String,
        val difficulty: String,
        val category: String,
        val question: String,
        @SerialName("correct_answer") val correctAnswer: String,
        @SerialName("incorrect_answers") val incorrectAnswers: List<String>,
    )

    companion object {
        const val SOURCE = "Open Trivia DB"
        const val BASE = "https://opentdb.com"
        const val BATCH = 50
        /** Refill once fewer than this many are ready. */
        const val LOW = 12
        /** The API allows one call per 5 seconds per IP; keep a little slack. */
        const val GAP_MS = 5_500L
        const val BACKOFF_MS = 60_000L
        private const val OK = 0
        private const val TOKEN_NOT_FOUND = 3
        private const val TOKEN_EMPTY = 4
        private val json = Json { ignoreUnknownKeys = true }

        private fun decode(s: String) = URLDecoder.decode(s, "UTF-8").replace(Regex("\\s+"), " ").trim()

        private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 5_000
            c.readTimeout = 8_000
            c.setRequestProperty("User-Agent", "PartyOS")
            try {
                if (c.responseCode != 200) throw IOException("Open Trivia DB answered HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
    }
}
