package app.englishreviewer.core

import java.time.LocalDate
import kotlin.test.*

class LearningTest {
    private val day = LocalDate.of(2026, 11, 11)
    @Test fun `empty and small collections are supported`() {
        assertEquals(emptyList(), selectReview(emptyList(), day))
        assertEquals(setOf("a", "b"), selectReview(listOf(ReviewCandidate("a", null), ReviewCandidate("b", null)), day).toSet())
    }
    @Test fun `review is unique stable and rotates over all sentences`() {
        val state = (1..10).associate { "$it" to null as Long? }.toMutableMap()
        val seen = mutableSetOf<String>()
        repeat(4) { offset ->
            val date = day.plusDays(offset.toLong())
            val picked = selectReview(state.map { ReviewCandidate(it.key, it.value) }, date)
            assertEquals(3, picked.distinct().size)
            assertEquals(picked, selectReview(state.map { ReviewCandidate(it.key, it.value) }, date))
            seen.addAll(picked)
            picked.forEach { state[it] = date.toEpochDay() }
        }
        assertEquals(10, seen.size)
    }
    @Test fun `oldest and unseen sentences have priority`() {
        val picked = selectReview(listOf(ReviewCandidate("new", null), ReviewCandidate("old", 1), ReviewCandidate("mid", 2), ReviewCandidate("recent", 3)), day)
        assertEquals(listOf("new", "old", "mid"), picked)
    }
    @Test fun `normalization and validation preserve original text`() {
        assertEquals("i like apples.", normalizeSentence(" I  like\nAPPLES. "))
        assertEquals("I'm learning.", validateSentence(" I'm learning. "))
        assertFailsWith<IllegalArgumentException> { validateSentence(" ") }
        assertFailsWith<IllegalArgumentException> { validateSentence("한글만") }
        assertFailsWith<IllegalArgumentException> { validateSentence("a".repeat(2001)) }
    }
}
