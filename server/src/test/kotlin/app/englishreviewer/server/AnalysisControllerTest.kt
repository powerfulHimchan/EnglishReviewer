package app.englishreviewer.server

import app.englishreviewer.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.http.MediaType

@WebMvcTest(AnalysisController::class, properties = ["reviewer.token=abcdefghijklmnopqrstuvwxyz123456"])
@Import(AnalysisControllerTest.Providers::class)
class AnalysisControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var provider: FakeProvider
    @TestConfiguration
    class Providers { @Bean fun analysisProvider() = FakeProvider() }
    class FakeProvider : AnalysisProvider {
        var calls = 0
        lateinit var result: Analysis
        override fun analyze(sentence: String): Analysis { calls++; return result }
    }
    @BeforeEach fun reset() { provider.calls = 0 }
    private val auth = "Bearer abcdefghijklmnopqrstuvwxyz123456"
    @Test fun `missing or incorrect token is rejected before calling provider`() {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content("""{"sentence":"I like cooking."}""")).andExpect(status().isUnauthorized)
        mvc.perform(post("/api/analyze").header("Authorization", "Bearer wrong").contentType(MediaType.APPLICATION_JSON).content("""{"sentence":"I like cooking."}""")).andExpect(status().isUnauthorized)
        assertEquals(0, provider.calls)
    }
    @Test fun `blank and oversized sentences are rejected`() {
        listOf(" ", "a".repeat(2001)).forEach { value ->
            mvc.perform(post("/api/analyze").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""{"sentence":"$value"}""")).andExpect(status().isBadRequest)
        }
        assertEquals(0, provider.calls)
    }
    @Test fun `authorized analysis preserves structured fields`() {
        val result = Analysis("I like cooking.", "요리하는 것을 좋아해요.", "자연스러운 문장이에요.",
            listOf(GrammarPoint("like + 동명사", "좋아하는 활동을 나타내요.")),
            listOf(Example("I like reading.", "독서를 좋아해요."), Example("I like walking.", "걷기를 좋아해요.")),
            listOf(SimilarExpression("I enjoy cooking.", "요리를 즐겨요.", "즐거움을 강조해요."), SimilarExpression("I'm into cooking.", "요리에 관심이 많아요.", "구어체예요.")))
        provider.result = result
        mvc.perform(post("/api/analyze").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""{"sentence":"I like cooking."}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.grammar[0].title").value("like + 동명사"))
            .andExpect(jsonPath("$.examples.length()").value(2)).andExpect(jsonPath("$.similar.length()").value(2))
    }
    @Test fun `health endpoint does not require a token`() { mvc.perform(get("/health")).andExpect(status().isOk) }
}
