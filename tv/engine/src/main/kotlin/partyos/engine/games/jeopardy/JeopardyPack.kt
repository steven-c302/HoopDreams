package partyos.engine.games.jeopardy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import partyos.engine.games.bluff.normalise

/** One clue. Its value comes from its position in the category (cheapest first), not from the file. */
@Serializable
data class JeopardyClue(val id: String, val clue: String, val answer: String)

@Serializable
data class JeopardyCategory(val id: String, val name: String, val clues: List<JeopardyClue>)

@Serializable
data class JeopardyFinal(val id: String, val category: String, val clue: String, val answer: String)

/**
 * Question-pack file format (version 2): a pool of categories to draw boards from, and Final Jeopardy clues. Each show
 * deals five categories per board at random, so nights differ.
 */
@Serializable
data class JeopardyPack(
    val packId: String,
    val title: String,
    val game: String,
    val version: Int,
    val categories: List<JeopardyCategory>,
    val finals: List<JeopardyFinal>,
) {
    companion object {
        const val MIN_CATEGORIES = 12
        const val MIN_FINALS = 4
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): JeopardyPack = validate(json.decodeFromString(serializer(), text))

        fun core(): JeopardyPack {
            val stream = requireNotNull(JeopardyPack::class.java.getResourceAsStream("/packs/jeopardy-core.json")) { "core pack missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun validate(p: JeopardyPack): JeopardyPack {
            require(p.game == "jeopardy") { "pack ${p.packId} is for ${p.game}, not jeopardy" }
            require(p.version == 2) { "pack ${p.packId} is version ${p.version}; Answer & Question needs version 2" }
            require(p.categories.size >= MIN_CATEGORIES) { "pack ${p.packId} needs at least $MIN_CATEGORIES categories" }
            require(p.finals.size >= MIN_FINALS) { "pack ${p.packId} needs at least $MIN_FINALS Final Jeopardy clues" }
            val ids = p.categories.flatMap { c -> c.clues.map { it.id } } + p.categories.map { it.id } + p.finals.map { it.id }
            require(ids.toSet().size == ids.size) { "pack ${p.packId} has duplicate ids" }
            for (c in p.categories) {
                require(c.name.isNotBlank()) { "category ${c.id} has no name" }
                require(c.clues.size == JeopardyRules.CLUES_PER_CATEGORY) { "category ${c.id} needs exactly ${JeopardyRules.CLUES_PER_CATEGORY} clues" }
                for (clue in c.clues) require(clue.clue.isNotBlank() && clue.answer.isNotBlank()) { "${clue.id}: blank clue or answer" }
                val answers = c.clues.map { normalise(it.answer) }
                require(answers.toSet().size == answers.size) { "category ${c.id} repeats an answer" }
            }
            for (f in p.finals) {
                require(f.category.isNotBlank() && f.clue.isNotBlank() && f.answer.isNotBlank()) { "${f.id}: blank field" }
            }
            return p
        }
    }
}
