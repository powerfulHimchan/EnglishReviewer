package app.englishreviewer.server

import app.englishreviewer.core.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.web.server.ResponseStatusException
import kotlinx.serialization.encodeToString
import java.net.InetSocketAddress

class OpenAiAnalysisProviderTest {
    private val mapper = jacksonObjectMapper()
    private lateinit var server: HttpServer
    private var response = ""
    private var status = 200
    private var request = ""
    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat") { exchange ->
            request = exchange.requestBody.bufferedReader().readText()
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }
    @AfterEach fun stop() { server.stop(0) }
    private fun provider() = OpenAiAnalysisProvider(mapper, "test-key", "gpt-4.1-mini", "http://127.0.0.1:${server.address.port}/chat")
    @Test fun `request uses strict schema and response is validated`() {
        val analysis = Analysis("I enjoy cooking.", "요리를 즐겨요.", "자연스러운 문장이에요.",
            listOf(GrammarPoint("enjoy + 동명사", "활동을 즐긴다는 의미예요.")),
            listOf(Example("I enjoy reading.", "독서를 즐겨요."), Example("I enjoy walking.", "걷기를 즐겨요.")),
            listOf(SimilarExpression("I like cooking.", "요리를 좋아해요.", "취향을 나타내요."), SimilarExpression("I'm into cooking.", "요리에 관심이 많아요.", "구어체예요.")))
        response = mapper.writeValueAsString(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to learningJson.encodeToString(analysis))))))
        assertEquals(analysis, provider().analyze("I enjoy cooking."))
        val tree = mapper.readTree(request)
        assertTrue(tree.path("response_format").path("json_schema").path("strict").asBoolean())
        assertEquals("I enjoy cooking.", tree.path("messages").path(1).path("content").asText())
        assertFalse(tree.path("store").asBoolean(true))
    }
    @Test fun `refusal and invalid provider JSON are handled without exposing raw content`() {
        response = """{"choices":[{"message":{"refusal":"refused"}}]}"""
        assertEquals(422, assertThrows(ResponseStatusException::class.java) { provider().analyze("A sentence.") }.statusCode.value())
        response = """{"choices":[{"message":{"content":"invalid json"}}]}"""
        assertEquals(502, assertThrows(ResponseStatusException::class.java) { provider().analyze("A sentence.") }.statusCode.value())
        response = "provider error"; status = 500
        assertEquals(502, assertThrows(ResponseStatusException::class.java) { provider().analyze("A sentence.") }.statusCode.value())
    }
}
