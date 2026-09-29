package partyos.engine.games.doodle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import partyos.engine.games.bluff.normalise

@Serializable
data class DoodleCategory(val id: String, val category: String, val easy: List<String>, val medium: List<String>, val hard: List<String>)

/** One playable word. [id] is the played-content id `<category id>:<word>`; [difficulty] is 1 easy, 2 medium, 3 hard. */
data class DoodleWord(val id: String, val category: String, val word: String, val difficulty: Int)

/** Word-pack file: the same envelope as the other packs, with each category's words split by difficulty. */
@Serializable
data class DoodlePack(
    val packId: String,
    val title: String,
    val game: String,
    val version: Int,
    val items: List<DoodleCategory>,
) {
    fun words(): List<DoodleWord> = items.flatMap { c ->
        listOf(1 to c.easy, 2 to c.medium, 3 to c.hard).flatMap { (d, ws) -> ws.map { DoodleWord("${c.id}:$it", c.category, it, d) } }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val SHAPE = Regex("[A-Za-z]+( [A-Za-z]+){0,2}")
        const val MIN_LEN = 3
        const val MAX_LEN = 24

        fun parse(text: String): DoodlePack = validate(json.decodeFromString(serializer(), text))

        fun core(): DoodlePack {
            val stream = requireNotNull(DoodlePack::class.java.getResourceAsStream("/packs/doodle-core.json")) { "core pack missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun validate(p: DoodlePack): DoodlePack {
            require(p.game == "doodle") { "pack ${p.packId} is for ${p.game}, not doodle" }
            require(p.items.isNotEmpty()) { "pack ${p.packId} is empty" }
            require(p.items.map { it.id }.toSet().size == p.items.size) { "pack ${p.packId} has duplicate category ids" }
            val seen = HashSet<String>()
            for (c in p.items) {
                require(c.category.isNotBlank()) { "${c.id}: blank category name" }
                for (w in c.easy + c.medium + c.hard) {
                    require(w.isNotBlank()) { "${c.id}: blank word" }
                    require(w.length in MIN_LEN..MAX_LEN && SHAPE.matches(w)) { "${c.id}: '$w' must be 1-3 words of letters, $MIN_LEN-$MAX_LEN characters" }
                    require(seen.add(normalise(w))) { "${c.id}: '$w' appears twice in the pack" }
                }
            }
            return p
        }
    }
}
