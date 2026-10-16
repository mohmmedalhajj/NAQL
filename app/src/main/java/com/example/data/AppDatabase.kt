package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val peerName: String,
    val isIncoming: Boolean,
    val filesCount: Int,
    val totalBytes: Long,
    val status: String,
    val timestamp: Long,
    val filesSummary: String
)

@Entity(tableName = "received_files")
data class ReceivedFileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val sha256: String,
    val receivedFrom: String,
    val receivedAt: Long
)

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TransferHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: TransferHistoryEntity)

    @Query("DELETE FROM transfer_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM received_files ORDER BY receivedAt DESC")
    fun getAllReceivedFiles(): Flow<List<ReceivedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceivedFile(file: ReceivedFileEntity)

    @Query("DELETE FROM received_files WHERE id = :id")
    suspend fun deleteReceivedFile(id: String)
}

@Database(entities = [TransferHistoryEntity::class, ReceivedFileEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
}
