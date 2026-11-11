package app.englishreviewer.data

import android.content.Context
import androidx.room.*
import app.englishreviewer.core.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "sentences", indices = [Index(value = ["normalized"], unique = true)])
data class Sentence(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val original: String,
    val normalized: String = normalizeSentence(original),
    val createdAt: Long = System.currentTimeMillis(),
    val analysisJson: String? = null,
    val searchText: String = "",
    val status: String = "saved",
    val error: String? = null,
    val lastSelectedDay: Long? = null,
) {
    fun analysis(): Analysis? = analysisJson?.let { runCatching { learningJson.decodeFromString<Analysis>(it) }.getOrNull() }
}
@Entity(tableName = "review_days")
data class ReviewDay(@PrimaryKey val date: String)
@Entity(tableName = "review_items", primaryKeys = ["date", "sentenceId"], foreignKeys = [
    ForeignKey(entity = Sentence::class, parentColumns = ["id"], childColumns = ["sentenceId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("sentenceId")])
data class ReviewItem(val date: String, val sentenceId: String, val position: Int, val completed: Boolean = false)
data class ReviewEntry(@Embedded val sentence: Sentence, val completed: Boolean)

@Dao
interface LearningDao {
    @Query("SELECT * FROM sentences ORDER BY createdAt DESC") fun observeAll(): Flow<List<Sentence>>
    @Query("SELECT * FROM sentences ORDER BY createdAt DESC") suspend fun all(): List<Sentence>
    @Query("SELECT * FROM sentences WHERE id = :id") suspend fun find(id: String): Sentence?
    @Query("SELECT * FROM sentences WHERE normalized = :normalized LIMIT 1") suspend fun findNormalized(normalized: String): Sentence?
    @Insert suspend fun insert(sentence: Sentence)
    @Query("DELETE FROM sentences WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE sentences SET status = :status, error = :error WHERE id = :id") suspend fun status(id: String, status: String, error: String? = null)
    @Query("UPDATE sentences SET analysisJson = :json, searchText = :search, status = 'ready', error = NULL WHERE id = :id")
    suspend fun analyzed(id: String, json: String, search: String)
    @Query("SELECT * FROM review_days WHERE date = :date") suspend fun day(date: String): ReviewDay?
    @Insert suspend fun insertDay(day: ReviewDay)
    @Insert suspend fun insertReview(items: List<ReviewItem>)
    @Query("UPDATE sentences SET lastSelectedDay = :day WHERE id IN (:ids)") suspend fun selected(ids: List<String>, day: Long)
    @Query("SELECT sentences.*, review_items.completed FROM review_items INNER JOIN sentences ON sentences.id = review_items.sentenceId WHERE review_items.date = :date ORDER BY review_items.position")
    fun observeReview(date: String): Flow<List<ReviewEntry>>
    @Query("SELECT sentences.*, review_items.completed FROM review_items INNER JOIN sentences ON sentences.id = review_items.sentenceId WHERE review_items.date = :date ORDER BY review_items.position")
    suspend fun review(date: String): List<ReviewEntry>
    @Query("UPDATE review_items SET completed = :completed WHERE date = :date AND sentenceId = :id")
    suspend fun complete(date: String, id: String, completed: Boolean)
}
@Database(entities = [Sentence::class, ReviewDay::class, ReviewItem::class], version = 1, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun dao(): LearningDao
    suspend fun ensureReview(date: LocalDate = LocalDate.now()) = withTransaction {
        val key = date.toString()
        if (dao().day(key) == null) {
            val ids = selectReview(dao().all().map { ReviewCandidate(it.id, it.lastSelectedDay) }, date)
            if (ids.isNotEmpty()) {
                dao().insertDay(ReviewDay(key))
                dao().insertReview(ids.mapIndexed { index, id -> ReviewItem(key, id, index) })
                dao().selected(ids, date.toEpochDay())
            }
        }
    }
    companion object {
        @Volatile private var instance: LearningDatabase? = null
        fun get(context: Context): LearningDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LearningDatabase::class.java, "learning.db").build().also { instance = it }
        }
    }
}
