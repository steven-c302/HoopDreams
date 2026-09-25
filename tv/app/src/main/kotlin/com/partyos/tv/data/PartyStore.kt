package com.partyos.tv.data

import androidx.room.withTransaction
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import partyos.engine.GameResult
import partyos.engine.PartySnapshot
import partyos.engine.ScoreRow

/** Persists the live party snapshot and appends finished games to history. */
class PartyStore(private val db: PartyDb) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dao = db.dao()

    suspend fun loadActive(): Pair<Long, PartySnapshot>? {
        val p = dao.active() ?: return null
        val snap = runCatching { json.decodeFromString(PartySnapshot.serializer(), p.snapshotJson) }.getOrNull() ?: return null
        return p.id to snap
    }

    suspend fun create(s: PartySnapshot): Long =
        dao.insert(PartyEntity(roomCode = s.roomCode, createdAt = s.createdAt, snapshotJson = encode(s), schemaVersion = SCHEMA))

    suspend fun save(id: Long, s: PartySnapshot) = db.withTransaction {
        dao.updateSnapshot(id, encode(s))
        val have = dao.resultCount(id)
        s.results.drop(have).forEach { dao.insertResult(it.toEntity(id)) }
    }

    suspend fun end(id: Long) = dao.end(id, System.currentTimeMillis())

    suspend fun results(partyId: Long): List<GameResult> = dao.results(partyId).map {
        GameResult(
            gameId = it.gameId,
            title = it.gameId,
            finishedAt = it.finishedAt,
            standings = json.decodeFromString(ListSerializer(ScoreRow.serializer()), it.standingsJson),
            highlights = json.decodeFromString(ListSerializer(String.serializer()), it.highlightsJson),
        )
    }

    private fun encode(s: PartySnapshot) = json.encodeToString(PartySnapshot.serializer(), s)

    private fun GameResult.toEntity(partyId: Long) = GameResultEntity(
        partyId = partyId,
        gameId = gameId,
        finishedAt = finishedAt,
        standingsJson = json.encodeToString(ListSerializer(ScoreRow.serializer()), standings),
        highlightsJson = json.encodeToString(ListSerializer(String.serializer()), highlights),
    )

    companion object {
        const val SCHEMA = 1
    }
}
