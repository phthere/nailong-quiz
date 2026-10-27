package com.nailong.quiz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nailong.quiz.data.*
import com.nailong.quiz.ui.settings.PdfImportScreen
import com.nailong.quiz.ui.settings.PdfImportViewModel
import com.nailong.quiz.ui.settings.PdfImportViewModelFactory
import com.nailong.quiz.ui.theme.NailongTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfDeviceImportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun scannedChinesePdfCanBeReviewedAndSavedWithoutNetworkPermission() {
        val activity = compose.activity
        val app = activity.application as NailongApp
        val scan = File(app.cacheDir, "scanned-device-test.pdf")
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 46f }
            listOf("1. 中国的首都是哪里？", "A. 北京", "B. 上海", "答案：A").forEachIndexed { i, line ->
                drawText(line, 90f, 200f + i * 110f, paint)
            }
        }
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
            page.canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, 600, 800), null)
            doc.finishPage(page)
            scan.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
        bitmap.recycle()
        val vm = ViewModelProvider(activity, PdfImportViewModelFactory(app))[PdfImportViewModel::class.java]
        compose.runOnUiThread {
            activity.setContent {
                NailongTheme(darkTheme = false, fontScale = 1f) {
                    PdfImportScreen(app, AppSettings(), rememberNavController())
                }
            }
        }
        compose.onNodeWithText("选择 PDF 文件").assertIsDisplayed()
        compose.runOnIdle { vm.read(Uri.fromFile(scan), false) }
        compose.waitUntil(120_000) { !vm.state.value.busy && (vm.state.value.document != null || vm.state.value.error != null) }
        val preview = vm.state.value
        assertNull(preview.error)
        val document = checkNotNull(preview.document)
        assertEquals(1, document.pages.size)
        assertTrue(document.pages.single().usedOcr)
        assertEquals(document.pages.single().text, 1, preview.drafts.size)
        val question = preview.drafts.single().question
        assertTrue(question.stem, question.stem.contains("首都"))
        assertEquals("A", question.answer)
        assertEquals(listOf("A", "B"), question.options.map { it.label })
        assertTrue(PdfQuestionParser.issues(question).toString(), PdfQuestionParser.issues(question).isEmpty())
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("识别 1 题 · 已选 1 题 · 待修正 0 题"))
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val screenshotFile = File(app.getExternalFilesDir(null), "pdf-import-preview.png")
        screenshotFile.outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        // connectedDebugAndroidTest may uninstall the APK; keep the proof outside its data directory.
        val copy = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${screenshotFile.absolutePath} /data/local/tmp/nailong-pdf-import-preview.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(copy).use { it.readBytes() }
        compose.onNodeWithText("确认新建题库（1 题）").assertIsEnabled().performClick()
        compose.waitUntil(30_000) { vm.state.value.savedBankId != null || vm.state.value.error != null }
        assertNull(vm.state.value.error)
        compose.onNodeWithText("题库已保存").assertIsDisplayed()
        val bank = app.bankCatalog.find(vm.state.value.savedBankId!!)!!
        runBlocking {
            val repository = QuizRepository(app, AppDatabase.get(app, bank), app.settingsRepo, bank)
            repository.ensureImported()
            assertEquals(1, repository.questionCount())
            assertEquals("A", repository.allQuestions().single().answer)
        }
        val permissions = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertTrue(scan.delete())
    }
}
