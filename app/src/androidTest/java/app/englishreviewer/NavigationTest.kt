package app.englishreviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.englishreviewer.data.*
import app.englishreviewer.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import java.io.FileInputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetDatabase() {
        runBlocking(Dispatchers.IO) { LearningDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext).clearAllTables() }
        compose.activityRule.scenario.recreate()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // AGP uninstalls the target app after the test suite; shell-owned files survive cleanup.
        listOf("mkdir -p /data/local/tmp/english-reviewer-screenshots",
            "screencap -p /data/local/tmp/english-reviewer-screenshots/$name.png").forEach { command ->
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
    }
    @Test fun studiesSavedAnalysisAndCompletesReview() {
        val analysis = Analysis("I enjoy cooking.", "요리를 즐겨요.", "자연스러운 문장이에요.",
            listOf(GrammarPoint("enjoy + 동명사", "enjoy 뒤에 활동을 나타내는 동명사를 사용해요.")),
            listOf(Example("I enjoy reading.", "독서를 즐겨요."), Example("I enjoy walking.", "걷기를 즐겨요.")),
            listOf(SimilarExpression("I like cooking.", "요리를 좋아해요.", "취향을 나타내요."), SimilarExpression("I'm into cooking.", "요리에 관심이 많아요.", "구어체 표현이에요.")))
        runBlocking(Dispatchers.IO) {
            LearningDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext).dao().insert(
                Sentence(original = "I enjoy cooking.", analysisJson = learningJson.encodeToString(analysis), searchText = "I enjoy cooking. 요리를 즐겨요.", status = "ready"))
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("I enjoy cooking.").fetchSemanticsNodes().isNotEmpty() }
        screenshot("today-review")
        compose.onNodeWithText("I enjoy cooking.").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("문법 포인트"))
        compose.onNodeWithText("문법 포인트").assertIsDisplayed()
        screenshot("grammar-detail")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("오늘의 복습 완료"))
        compose.onNodeWithText("오늘의 복습 완료").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("복습 완료 · 다시 학습하기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("복습 완료 · 다시 학습하기").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("보관함").performClick()
        compose.onNodeWithText("영어 문장이나 한국어 뜻 검색").performTextInput("요리")
        compose.onNodeWithText("I enjoy cooking.").assertIsDisplayed()
        screenshot("sentence-library")
    }
    @Test fun savesSearchesAndDeletesSentence() {
        compose.onNodeWithText("보관함").performClick()
        compose.onNodeWithContentDescription("문장 등록").performClick()
        compose.onNodeWithText("영어 문장").performTextInput("I enjoy cooking at home.")
        compose.onNodeWithText("저장하고 분석하기").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("내가 등록한 문장").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("I enjoy cooking at home.").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("영어 문장이나 한국어 뜻 검색").performTextInput("COOKING")
        compose.onNodeWithText("I enjoy cooking at home.").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("문장 삭제"))
        compose.onNodeWithText("문장 삭제").performClick()
        compose.onNodeWithText("삭제").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("0개의 문장").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun opensRegistrationLibraryAndSettings() {
        compose.onNodeWithText("보관함").performClick()
        compose.onNodeWithText("내 문장 보관함").assertIsDisplayed()
        compose.onNodeWithContentDescription("문장 등록").performClick()
        compose.onNodeWithText("어떤 표현을 기억할까요?").assertIsDisplayed()
        compose.onNodeWithText("저장하고 분석하기").assertIsNotEnabled()
        compose.onNodeWithText("영어 문장").performTextInput("I enjoy cooking.")
        compose.onNodeWithText("저장하고 분석하기").assertIsEnabled()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("나의 학습 설정").assertIsDisplayed()
        compose.onNodeWithText("복습 알림").assertIsDisplayed()
    }
}
