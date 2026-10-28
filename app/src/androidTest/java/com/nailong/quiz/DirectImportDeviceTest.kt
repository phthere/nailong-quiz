package com.nailong.quiz

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nailong.quiz.data.*
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.practice.*
import com.nailong.quiz.ui.settings.*
import com.nailong.quiz.ui.theme.NailongTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DirectImportDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun incompleteQuestionsCanBeCreatedImmediatelyAndStudiedWithoutAutomaticWrongAnswers() {
        val activity = compose.activity
        val app = activity.application as NailongApp
        val oldId = runBlocking { app.settingsRepo.settings.first().selectedBank }
        val file = File(app.cacheDir, "无需校验的题库.txt").apply {
            writeText("1. 第一题有答案。\nA. 甲 B. 乙\n答案：A\n2. 第二题缺少答案。\nA. 甲 B. 乙\n3. 第三题选项待完善。\nA. 甲 C. 丙\n答案：A")
        }
        val vm = ViewModelProvider(activity, PdfImportViewModelFactory(app))[PdfImportViewModel::class.java]
        compose.runOnUiThread { activity.setContent { NailongTheme(darkTheme = true) {
            PdfImportScreen(app, AppSettings(), rememberNavController(), documentMode = true)
        } } }
        compose.runOnIdle { vm.readDocument(Uri.fromFile(file)) }
        compose.waitUntil(60_000) { !vm.state.value.busy && vm.state.value.document != null }
        assertEquals(3, vm.state.value.drafts.size)
        compose.runOnIdle { vm.title("") }
        compose.onNodeWithText("新建题库（3 题）").assertIsEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("识别 3 题 · 已选 3 题 · 待完善 2 题"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val proof = File(app.getExternalFilesDir(null), "direct-import.png")
        proof.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }; screenshot.recycle()
        val copy = instrumentation.uiAutomation.executeShellCommand("cp ${proof.absolutePath} /data/local/tmp/nailong-direct-import.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(copy).use { it.readBytes() }
        compose.onNodeWithText("新建题库（3 题）").performClick()
        compose.waitUntil(30_000) { vm.state.value.savedBankId != null }
        compose.onNodeWithText("题库已保存").assertIsDisplayed()
        val id = checkNotNull(vm.state.value.savedBankId)
        runBlocking { app.activateBank(id) }
        val repo = app.repository
        val questions = runBlocking { repo.allQuestions() }
        assertEquals(3, questions.size)
        assertEquals(2, questions.count { it.needsReview })
        assertEquals("奶龙刷题", app.applicationInfo.loadLabel(app.packageManager).toString())
        val config = PracticeConfig(PracticeMode.SEQUENTIAL, "待完善题目自评", resumeIndex = 1)
        PracticeBus.config = config
        val practice = ViewModelProvider(activity, PracticeViewModelFactory(app, config))
            .get(config.title + config.mode, PracticeViewModel::class.java)
        compose.runOnUiThread { activity.setContent { NailongTheme(darkTheme = true) {
            PracticeScreen(app, AppSettings(), rememberNavController())
        } } }
        compose.waitUntil(30_000) { !practice.state.value.loading }
        compose.onNodeWithText("待完善 · 自评").assertIsDisplayed()
        compose.runOnIdle { practice.selectOption("A"); practice.submit() }
        assertTrue(practice.state.value.submitted.isEmpty())
        assertEquals(0, runBlocking { repo.totalAnswers() })
        compose.onNodeWithText("查看参考答案").performScrollTo().performClick()
        compose.onNodeWithText("已掌握").performScrollTo().performClick()
        compose.waitUntil(30_000) { runBlocking { repo.totalAnswers() == 1 } }
        assertTrue(runBlocking { repo.db.recordDao().all().single().userAnswer.startsWith("自评") })
        assertTrue(runBlocking { repo.db.wrongDao().all().isEmpty() })
        runBlocking { app.activateBank(oldId) }
        PracticeBus.config = null
    }
}
