package partyos.engine.games.imposter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import partyos.engine.games.bluff.normalise

@Serializable
data class ImposterCategory(val id: String, val category: String, val words: List<String>)

/** One playable word. [id] is the played-content id `<category id>:<word>`. */
data class ImposterWord(val id: String, val categoryId: String, val category: String, val word: String)

/** Word-pack file format, the same envelope as the Bluff pack so the later pack editor can cover it. */
@Serializable
data class ImposterPack(
    val packId: String,
    val title: String,
    val game: String,
    val version: Int,
    val items: List<ImposterCategory>,
) {
    fun words(): List<ImposterWord> = items.flatMap { c -> c.words.map { ImposterWord("${c.id}:$it", c.id, c.category, it) } }

    companion object {
        const val MIN_WORDS = 8
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ImposterPack = validate(json.decodeFromString(serializer(), text))

        fun core(): ImposterPack {
            val stream = requireNotNull(ImposterPack::class.java.getResourceAsStream("/packs/imposter-core.json")) { "core pack missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun validate(p: ImposterPack): ImposterPack {
            require(p.game == "imposter") { "pack ${p.packId} is for ${p.game}, not imposter" }
            require(p.items.isNotEmpty()) { "pack ${p.packId} is empty" }
            require(p.items.map { it.id }.toSet().size == p.items.size) { "pack ${p.packId} has duplicate category ids" }
            for (c in p.items) {
                require(c.category.isNotBlank()) { "${c.id}: blank category name" }
                require(c.words.none { it.isBlank() }) { "${c.id}: blank word" }
                val distinct = c.words.map(::normalise).toSet()
                require(distinct.size == c.words.size) { "${c.id}: duplicate words" }
                require(distinct.size >= MIN_WORDS) { "${c.id}: needs at least $MIN_WORDS words" }
            }
            return p
        }
    }
}
