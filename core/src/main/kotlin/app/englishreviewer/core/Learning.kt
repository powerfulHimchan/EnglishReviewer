package app.englishreviewer.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.random.Random

val learningJson = Json { ignoreUnknownKeys = true }

@Serializable
data class Example(val english: String, val korean: String)
@Serializable
data class SimilarExpression(val english: String, val korean: String, val nuance: String)
@Serializable
data class GrammarPoint(val title: String, val explanation: String)
@Serializable
data class Analysis(
    val corrected: String,
    val translation: String,
    val correctionNote: String,
    val grammar: List<GrammarPoint>,
    val examples: List<Example>,
    val similar: List<SimilarExpression>,
) {
    fun validate(): Analysis {
        require(corrected.isNotBlank() && translation.isNotBlank() && correctionNote.isNotBlank())
        require(grammar.size in 1..6 && grammar.all { it.title.isNotBlank() && it.explanation.isNotBlank() })
        require(examples.size in 2..3 && examples.all { it.english.isNotBlank() && it.korean.isNotBlank() })
        require(similar.size in 2..3 && similar.all { it.english.isNotBlank() && it.korean.isNotBlank() && it.nuance.isNotBlank() })
        return this
    }
}
@Serializable
data class AnalyzeRequest(val sentence: String)

fun normalizeSentence(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
fun validateSentence(value: String): String {
    val trimmed = value.trim()
    require(trimmed.isNotEmpty()) { "영어 문장을 입력해 주세요." }
    require(trimmed.length <= 2000) { "문장은 2,000자까지 입력할 수 있어요." }
    require(trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }) { "영어가 포함된 문장을 입력해 주세요." }
    return trimmed
}

data class ReviewCandidate(val id: String, val lastSelectedDay: Long?)
/** Least recently selected first; shuffled ties keep initial reviews varied. */
fun selectReview(candidates: List<ReviewCandidate>, day: LocalDate): List<String> =
    candidates.distinctBy { it.id }.shuffled(Random(day.toEpochDay()))
        .sortedBy { it.lastSelectedDay ?: Long.MIN_VALUE }.take(3).map { it.id }
