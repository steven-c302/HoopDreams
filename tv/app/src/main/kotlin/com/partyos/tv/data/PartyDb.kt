package com.partyos.tv.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "party")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomCode: String,
    val createdAt: Long,
    val endedAt: Long? = null,
    val snapshotJson: String,
    val schemaVersion: Int,
)

@Entity(tableName = "game_result")
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val gameId: String,
    val finishedAt: Long,
    val standingsJson: String,
    val highlightsJson: String,
)

@Dao
interface PartyDao {
    @Insert suspend fun insert(p: PartyEntity): Long
    @Query("UPDATE party SET snapshotJson = :json WHERE id = :id") suspend fun updateSnapshot(id: Long, json: String)
    @Query("UPDATE party SET endedAt = :at WHERE id = :id") suspend fun end(id: Long, at: Long)
    @Query("SELECT * FROM party WHERE endedAt IS NULL ORDER BY createdAt DESC LIMIT 1") suspend fun active(): PartyEntity?
    @Insert suspend fun insertResult(r: GameResultEntity)
    @Query("SELECT * FROM game_result WHERE partyId = :partyId ORDER BY finishedAt") suspend fun results(partyId: Long): List<GameResultEntity>
    @Query("SELECT COUNT(*) FROM game_result WHERE partyId = :partyId") suspend fun resultCount(partyId: Long): Int
}

@Database(entities = [PartyEntity::class, GameResultEntity::class], version = 1, exportSchema = true)
abstract class PartyDb : RoomDatabase() {
    abstract fun dao(): PartyDao
}
