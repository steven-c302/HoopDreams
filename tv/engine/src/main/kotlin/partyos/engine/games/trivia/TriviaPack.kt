package partyos.engine.games.trivia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class McItem(val id: String, val category: String, val prompt: String, val answer: String, val wrong: List<String>, val fact: String? = null)

@Serializable
data class BallparkItem(
    val id: String,
    val category: String,
    val prompt: String,
    val answer: Double,
    val unit: String? = null,
    val fact: String? = null,
    /** Show the answer as a year (no thousands separator). */
    val year: Boolean = false,
)

@Serializable
data class SidesItem(val text: String, val side: String)

@Serializable
data class SidesSet(val id: String, val prompt: String, val left: String, val right: String, val items: List<SidesItem>)

@Serializable
data class GauntletOption(val text: String, val fit: Boolean)

@Serializable
data class GauntletItem(val id: String, val prompt: String, val options: List<GauntletOption>)

/** Every BRAIN DRAIN question, one list per round format. Ids start with `t` so they never collide with other games. */
data class TriviaPack(
    val mc: List<McItem>,
    val ballpark: List<BallparkItem>,
    val sides: List<SidesSet>,
    val gauntlet: List<GauntletItem>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun <T> load(name: String, s: KSerializer<T>): List<T> {
            val stream = requireNotNull(TriviaPack::class.java.getResourceAsStream("/packs/$name")) { "$name missing" }
            return json.decodeFromString(ListSerializer(s), stream.bufferedReader().use { it.readText() })
        }

        fun core(): TriviaPack = validate(
            TriviaPack(
                load("trivia-mc.json", McItem.serializer()),
                load("trivia-ballpark.json", BallparkItem.serializer()),
                load("trivia-sides.json", SidesSet.serializer()),
                load("trivia-gauntlet.json", GauntletItem.serializer()),
            ),
        )

        fun validate(p: TriviaPack): TriviaPack {
            val ids = p.mc.map { it.id } + p.ballpark.map { it.id } + p.sides.map { it.id } + p.gauntlet.map { it.id }
            require(ids.toSet().size == ids.size) { "duplicate trivia ids: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}" }
            require(ids.all { it.startsWith("t") }) { "trivia ids must start with t" }
            for (q in p.mc) {
                require(q.prompt.isNotBlank() && q.prompt.length <= MAX_PROMPT) { "${q.id}: prompt blank or over $MAX_PROMPT" }
                require(q.wrong.size == 3) { "${q.id}: needs exactly 3 wrong answers" }
                val all = (q.wrong + q.answer).map { it.trim().lowercase() }
                require(all.toSet().size == 4 && all.none { it.isEmpty() }) { "${q.id}: answers must be 4 distinct, non-blank" }
                require((q.wrong + q.answer).all { it.length <= MAX_OPTION }) { "${q.id}: an answer is over $MAX_OPTION chars" }
            }
            for (q in p.ballpark) {
                require(q.prompt.isNotBlank() && q.prompt.length <= MAX_PROMPT) { "${q.id}: prompt blank or over $MAX_PROMPT" }
                require(q.answer.isFinite()) { "${q.id}: answer must be a number" }
            }
            for (s in p.sides) {
                require(s.items.size >= 5) { "${s.id}: needs at least 5 items" }
                require(s.items.all { it.side == LEFT || it.side == RIGHT }) { "${s.id}: side must be left or right" }
                require(s.items.any { it.side == LEFT } && s.items.any { it.side == RIGHT }) { "${s.id}: needs items on both sides" }
                require(s.items.map { it.text.lowercase() }.toSet().size == s.items.size) { "${s.id}: duplicate items" }
                require(s.left.length <= MAX_OPTION && s.right.length <= MAX_OPTION) { "${s.id}: side label too long" }
            }
            for (g in p.gauntlet) {
                require(g.options.size in 3..4) { "${g.id}: needs 3 or 4 options" }
                require(g.options.any { it.fit } && g.options.any { !it.fit }) { "${g.id}: needs a fit and a misfit" }
                require(g.options.all { it.text.length <= MAX_OPTION }) { "${g.id}: an option is over $MAX_OPTION chars" }
            }
            return p
        }

        const val LEFT = "left"
        const val RIGHT = "right"
        const val MAX_PROMPT = 140
        const val MAX_OPTION = 40
    }
}
