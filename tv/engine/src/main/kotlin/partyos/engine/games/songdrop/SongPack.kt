package partyos.engine.games.songdrop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One playable song. [id] is the played-content id; [startSec] is where its hook starts in the YouTube video. */
@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int,
    val genre: String,
    val videoId: String,
    val startSec: Int,
) {
    val era: Int get() = SongRules.eraOf(year)
}

@Serializable
data class SongPack(
    val packId: String,
    val title: String,
    val game: String,
    val version: Int,
    val items: List<Song>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        val GENRES = setOf("pop", "rock", "hiphop", "rnb", "dance", "country", "latin", "other")
        const val MIN_SONGS = 4

        fun parse(text: String): SongPack = validate(json.decodeFromString(serializer(), text))

        fun core(): SongPack {
            val stream = requireNotNull(SongPack::class.java.getResourceAsStream("/packs/songdrop-core.json")) { "core pack missing" }
            return parse(stream.bufferedReader().use { it.readText() })
        }

        fun validate(p: SongPack): SongPack {
            require(p.game == "songdrop") { "pack ${p.packId} is for ${p.game}, not songdrop" }
            require(p.items.size >= MIN_SONGS) { "pack ${p.packId} needs at least $MIN_SONGS songs" }
            require(p.items.map { it.id }.toSet().size == p.items.size) { "pack ${p.packId} has duplicate song ids" }
            require(p.items.map { it.videoId }.toSet().size == p.items.size) { "pack ${p.packId} uses a video twice" }
            for (s in p.items) {
                require(ID.matches(s.id)) { "${s.id}: ids are lowercase words joined by dashes" }
                require(s.title.isNotBlank() && s.artist.isNotBlank()) { "${s.id}: blank title or artist" }
                require(s.year in 1950..2030) { "${s.id}: year ${s.year} out of range" }
                require(s.genre in GENRES) { "${s.id}: unknown genre ${s.genre}" }
                require(VIDEO_ID.matches(s.videoId)) { "${s.id}: '${s.videoId}' is not a YouTube video id" }
                require(s.startSec in 0..900) { "${s.id}: start ${s.startSec} out of range" }
            }
            return p
        }
    }
}
