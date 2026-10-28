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
import com.nailong.quiz.ui.settings.PdfImportScreen
import com.nailong.quiz.ui.settings.PdfImportViewModel
import com.nailong.quiz.ui.settings.PdfImportViewModelFactory
import com.nailong.quiz.ui.theme.NailongTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocumentDeviceImportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun wordAutomaticNumberingAndQuestionTableCanBeReviewedSavedAndSelected() {
        val activity = compose.activity
        val app = activity.application as NailongApp
        compose.waitUntil(30_000) { compose.onAllNodesWithText("我的").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("从文档新建题库（Word/TXT）").performScrollTo().performClick()
        compose.onNodeWithText("从文档新建题库").assertIsDisplayed()
        compose.onNodeWithText("选择文档文件").assertIsDisplayed()

        val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        fun p(text: String, level: Int? = null): String = "<w:p>" + (level?.let {
            "<w:pPr><w:numPr><w:ilvl w:val=\"$it\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr>"
        } ?: "") + "<w:r><w:t>$text</w:t></w:r></w:p>"
        fun row(vararg cells: String) = "<w:tr>" + cells.joinToString("") { "<w:tc>${p(it)}</w:tc>" } + "</w:tr>"
        val body = p("中国的首都是哪里？", 0) + p("北京", 1) + p("上海", 1) + p("答案：A") +
            "<w:tbl>" + row("题号", "题型", "题干", "答案") + row("2", "判断", "地球是行星。", "对") + "</w:tbl>"
        val numbering = "<w:numbering xmlns:w=\"$ns\"><w:abstractNum w:abstractNumId=\"0\">" +
            "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/></w:lvl>" +
            "<w:lvl w:ilvl=\"1\"><w:start w:val=\"1\"/><w:numFmt w:val=\"upperLetter\"/><w:lvlText w:val=\"%2.\"/></w:lvl>" +
            "</w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>"
        val file = File(app.cacheDir, "Word题库设备测试.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            val parts = mapOf(
                "[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/></Types>",
                "_rels/.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
                "word/_rels/document.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/></Relationships>",
                "word/document.xml" to "<w:document xmlns:w=\"$ns\"><w:body>$body</w:body></w:document>",
                "word/numbering.xml" to numbering)
            parts.forEach { (name, xml) -> zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry() }
        }
        val vm = ViewModelProvider(activity, PdfImportViewModelFactory(app))[PdfImportViewModel::class.java]
        compose.runOnUiThread {
            activity.setContent { NailongTheme(darkTheme = false, fontScale = 1f) {
                PdfImportScreen(app, AppSettings(), rememberNavController(), documentMode = true)
            } }
        }
        compose.runOnIdle { vm.readDocument(Uri.fromFile(file)) }
        compose.waitUntil(60_000) { !vm.state.value.busy && (vm.state.value.document != null || vm.state.value.error != null) }
        assertNull(vm.state.value.error)
        val result = checkNotNull(vm.state.value.document)
        assertEquals(ImportFormat.DOCX, result.format)
        assertFalse(result.pages.single().usedOcr)
        assertEquals(result.pages.single().text, 2, vm.state.value.drafts.size)
        assertEquals(listOf("A", "对"), vm.state.value.drafts.map { it.question.answer })
        assertTrue(vm.state.value.drafts.all { PdfQuestionParser.issues(it.question).isEmpty() })
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("识别 2 题 · 已选 2 题 · 待修正 0 题"))
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val screenshotFile = File(app.getExternalFilesDir(null), "document-import-preview.png")
        screenshotFile.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        val copy = instrumentation.uiAutomation.executeShellCommand("cp ${screenshotFile.absolutePath} /data/local/tmp/nailong-document-import-preview.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(copy).use { it.readBytes() }
        compose.onNodeWithText("确认新建题库（2 题）").assertIsEnabled().performClick()
        compose.waitUntil(30_000) { vm.state.value.savedBankId != null || vm.state.value.error != null }
        assertNull(vm.state.value.error)
        compose.onNodeWithText("题库已保存").assertIsDisplayed()
        runBlocking {
            val oldId = app.settingsRepo.settings.first().selectedBank
            val oldRepo = app.repository
            val oldCount = oldRepo.totalAnswers()
            val id = checkNotNull(vm.state.value.savedBankId)
            app.settingsRepo.update { it.copy(selectedBank = id) }
            app.activateBank(id)
            assertEquals(2, app.repository.questionCount())
            assertEquals(listOf("A", "对"), app.repository.allQuestions().map { it.answer })
            assertEquals(oldCount, oldRepo.totalAnswers())
            app.settingsRepo.update { it.copy(selectedBank = oldId) }
            app.activateBank(oldId)
        }
    }
}
