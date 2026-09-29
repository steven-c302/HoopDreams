package partyos.engine.games.jeopardy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class JeopardyClue(val id: String, val value: Int, val clue: String, val answer: String)

@Serializable
data class JeopardyCategory(val name: String, val clues: List<JeopardyClue>)

/** Question-pack file format, mirroring the other games' packs. One fixed board per pack, like a real episode. */
@Serializable
data class JeopardyPack(
    val packId: String,
    val title: String,
    val game: String,
    val version: Int,
    val categories: List<JeopardyCategory>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): JeopardyPack = validate(json.decodeFromString(serializer(), text))

        fun core(): JeopardyPack {
            val stream = requireNotNull(JeopardyPack::class.java.getResourceAsStream("/packs/jeopardy-core.json")) { "core pack missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun validate(p: JeopardyPack): JeopardyPack {
            require(p.game == "jeopardy") { "pack ${p.packId} is for ${p.game}, not jeopardy" }
            require(p.categories.isNotEmpty()) { "pack ${p.packId} has no categories" }
            val ids = p.categories.flatMap { it.clues }.map { it.id }
            require(ids.toSet().size == ids.size) { "pack ${p.packId} has duplicate clue ids" }
            for (c in p.categories) {
                require(c.clues.isNotEmpty()) { "category ${c.name} is empty" }
                for (clue in c.clues) {
                    require(clue.clue.isNotBlank() && clue.answer.isNotBlank()) { "${clue.id}: blank clue/answer" }
                    require(clue.value > 0) { "${clue.id}: value must be positive" }
                }
            }
            return p
        }
    }
}
