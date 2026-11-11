package app.englishreviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import androidx.room.withTransaction
import app.englishreviewer.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: LearningDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LearningDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun reviewSurvivesRepeatedCallsAndCascadesDeletion() = runBlocking {
        val date = LocalDate.of(2026, 11, 11)
        (1..6).forEach { db.dao().insert(Sentence(id = "$it", original = "Sentence $it")) }
        coroutineScope { repeat(5) { launch(Dispatchers.IO) { db.ensureReview(date) } } }
        val initial = db.dao().review(date.toString())
        assertEquals(3, initial.size)
        db.dao().insert(Sentence(id = "extra", original = "A new sentence"))
        db.ensureReview(date)
        assertEquals(initial.map { it.sentence.id }, db.dao().review(date.toString()).map { it.sentence.id })
        val id = initial.first().sentence.id
        db.dao().complete(date.toString(), id, true)
        assertTrue(db.dao().review(date.toString()).first().completed)
        db.dao().delete(id)
        assertEquals(2, db.dao().review(date.toString()).size)
    }
    @Test fun noSentencesDoesNotLockTodayIntoAnEmptyReview() = runBlocking {
        val date = LocalDate.of(2026, 11, 11)
        db.ensureReview(date)
        assertNull(db.dao().day(date.toString()))
        db.dao().insert(Sentence(original = "I enjoy cooking."))
        db.ensureReview(date)
        assertEquals(1, db.dao().review(date.toString()).size)
    }
    @Test fun duplicateNormalizationIsRejected() = runBlocking {
        db.dao().insert(Sentence(original = "I like cooking."))
        val result = runCatching { db.dao().insert(Sentence(original = "  I LIKE  cooking. ")) }
        assertTrue(result.isFailure)
    }
}
