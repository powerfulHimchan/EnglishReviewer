package app.englishreviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
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
        compose.onNodeWithText("문장 삭제").performScrollTo().performClick()
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
