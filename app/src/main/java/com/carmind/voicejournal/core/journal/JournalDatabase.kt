package com.carmind.voicejournal.core.journal

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    suspend fun getAllEntries(): List<JournalEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)

    @Query("SELECT * FROM entries WHERE syncedToPi = 0")
    suspend fun getPendingSync(): List<JournalEntry>

    @Query("SELECT * FROM entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<JournalEntry>

    // Summary queries
    @Query("SELECT * FROM summaries ORDER BY timestamp DESC")
    fun observeSummaries(): Flow<List<TimeSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: TimeSummary)

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun deleteSummaryById(id: String)

    @Query("SELECT * FROM summaries WHERE period = :period ORDER BY endDate DESC LIMIT 1")
    suspend fun getLatestSummary(period: SummaryPeriod): TimeSummary?

    // Memory queries
    @Query("SELECT * FROM user_memory WHERE id = 'singleton_memory' LIMIT 1")
    suspend fun getMemory(): UserProfileMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserProfileMemory)

    // Coach Session queries
    @Query("SELECT * FROM coach_sessions ORDER BY timestamp DESC")
    fun observeCoachSessions(): Flow<List<CoachSession>>

    @Query("SELECT * FROM coach_sessions ORDER BY timestamp DESC")
    suspend fun getAllCoachSessions(): List<CoachSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoachSession(session: CoachSession)
}

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromSummaryPeriod(value: SummaryPeriod): String = value.name

    @TypeConverter
    fun toSummaryPeriod(value: String): SummaryPeriod = SummaryPeriod.valueOf(value)
}

@Database(
    entities = [JournalEntry::class, TimeSummary::class, UserProfileMemory::class, CoachSession::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun dao(): JournalDao
}
