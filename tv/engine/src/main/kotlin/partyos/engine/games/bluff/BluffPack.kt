package partyos.engine.games.bluff

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BluffQuestion(
    val id: String,
    val prompt: String,
    val answer: String,
    val alsoAccept: List<String> = emptyList(),
    val decoys: List<String>,
) {
    /** Every normalised form that counts as "the truth". */
    fun truths(): Set<String> = (alsoAccept + answer).map(::normalise).toSet()
}

/** Question-pack file format; the same schema the pack editor will import and export. */
@Serializable
data class BluffPack(
    val packId: String,
    val title: String,
    val game: String,
    val version: Int,
    val items: List<BluffQuestion>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): BluffPack = validate(json.decodeFromString(serializer(), text))

        fun core(): BluffPack {
            val stream = requireNotNull(BluffPack::class.java.getResourceAsStream("/packs/bluff-core.json")) { "core pack missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun validate(p: BluffPack): BluffPack {
            require(p.game == "bluff") { "pack ${p.packId} is for ${p.game}, not bluff" }
            require(p.items.isNotEmpty()) { "pack ${p.packId} is empty" }
            require(p.items.map { it.id }.toSet().size == p.items.size) { "pack ${p.packId} has duplicate ids" }
            for (q in p.items) {
                require(q.prompt.isNotBlank() && q.answer.isNotBlank()) { "${q.id}: blank prompt/answer" }
                val decoys = q.decoys.map(::normalise).filter { it.isNotEmpty() }.toSet()
                require(decoys.size >= 2) { "${q.id}: needs at least 2 distinct decoys" }
                require(decoys.none { it in q.truths() }) { "${q.id}: a decoy matches the answer" }
            }
            return p
        }
    }
}
