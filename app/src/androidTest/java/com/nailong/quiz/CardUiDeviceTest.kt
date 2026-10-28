package com.nailong.quiz

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nailong.quiz.data.BuiltInBank
import com.nailong.quiz.data.DarkMode
import com.nailong.quiz.data.ThemeBg
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CardUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val app = compose.activity.application as NailongApp
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val file = File(app.getExternalFilesDir(null), "ui-$name.png")
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }; screenshot.recycle()
        val copy = instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/nailong-ui-$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(copy).use { it.readBytes() }
    }
    @Test fun homeCardsNavigationAndLargeFontsRemainUsable() {
        val app = compose.activity.application as NailongApp
        runBlocking { app.settingsRepo.update { it.copy(selectedBank = BuiltInBank.DEFAULT.id, darkMode = DarkMode.DARK, themeBg = ThemeBg.CLOUD, fontScale = 1f) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("home_hero").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home_hero").assertIsDisplayed()
        compose.onNodeWithTag("home_progress").assertExists()
        assertEquals("奶龙刷题", app.applicationInfo.loadLabel(app.packageManager).toString())
        capture("home")
        listOf("sequential", "random", "exam", "sprint", "wrong", "favorites").forEach { tag ->
            compose.onNodeWithTag("home_action_$tag").performScrollTo().assertIsDisplayed()
        }
        capture("home-actions")
        compose.onNodeWithTag("home_action_wrong").performScrollTo().performClick()
        compose.onNodeWithTag("wrong_page_header").assertIsDisplayed()
        compose.onNodeWithText("题库", useUnmergedTree = false).performClick()
        compose.onNodeWithTag("bank_page_header").assertIsDisplayed()
        val firstId = runBlocking { app.repository.allQuestions().first().id }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("bank_question_$firstId").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bank_search").assertIsDisplayed()
        capture("bank")
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithTag("stats_page_header").assertIsDisplayed()
        capture("stats")
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithTag("home_hero").performScrollTo()
        runBlocking { app.settingsRepo.update { it.copy(fontScale = 1.3f) } }
        compose.onNodeWithTag("home_hero").performScrollTo().assertIsDisplayed()
        capture("home-large")
        compose.onNodeWithTag("home_action_sequential").performScrollTo().assertIsDisplayed()
        runBlocking { app.settingsRepo.update { it.copy(fontScale = 1f, darkMode = DarkMode.SYSTEM) } }
    }
}
