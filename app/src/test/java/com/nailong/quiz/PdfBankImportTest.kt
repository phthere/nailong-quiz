package com.nailong.quiz

import com.nailong.quiz.data.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NailongApp::class)
class PdfBankImportTest {
    @Test fun `new bank survives reload and switching keeps existing data`() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NailongApp
        app.activateBank(BuiltInBank.DANGZHANG.id)
        val existing = app.repository.allQuestions().first()
        app.repository.recordAnswer(existing, true, existing.answer, "SEQUENTIAL", 2)
        val questions = PdfQuestionParser.parse(listOf(PdfTextPage(1,
            "1. 新题库第一题。\nA. 正确选项 B. 错误选项\n答案：A")), "新增.pdf").questions
        val bank = app.bankCatalog.create("自建题库", "hash-test-document", questions)
        assertNotEquals(BuiltInBank.DANGZHANG.databaseName, bank.databaseName)
        assertEquals(bank, BankCatalog(app).find(bank.id))
        app.settingsRepo.update { it.copy(selectedBank = bank.id) }
        assertEquals(bank.id, app.settingsRepo.settings.first().selectedBank)
        app.activateBank(bank.id)
        assertEquals(1, app.repository.questionCount())
        assertEquals(0, app.repository.totalAnswers())
        assertEquals("A", app.repository.allQuestions().single().answer)
        app.settingsRepo.saveSeqIndex(7, bank.id)
        assertEquals(7, app.settingsRepo.seqIndex.first())
        app.activateBank(BuiltInBank.DANGZHANG.id)
        assertEquals(357, app.repository.questionCount())
        assertEquals(1, app.repository.totalAnswers())
    }

    @Test fun `duplicate PDF and invalid question cannot create a bank`() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NailongApp
        val questions = PdfQuestionParser.parse(listOf(PdfTextPage(1,
            "1. 一个问题。\nA. 第一项 B. 第二项\n答案：A")), "测试.pdf").questions
        app.bankCatalog.create("第一套", "same-pdf", questions)
        assertTrue(runCatching { app.bankCatalog.create("重复", "same-pdf", questions) }.isFailure)
        assertTrue(runCatching { app.bankCatalog.create("无答案", "other", questions.map { it.copy(answer = "") }) }.isFailure)
        assertEquals(4, app.bankCatalog.banks.value.size)
    }

    @Test fun `real text PDF is extracted page by page and parsed into questions`() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NailongApp
        PDFBoxResourceLoader.init(app)
        val file = File(app.cacheDir, "reader-fixture.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.setLeading(18f)
                stream.newLineAtOffset(50f, 700f)
                listOf("1. Which direction does the sun rise from?", "A. East", "B. West", "C. North", "D. South").forEach {
                    stream.showText(it)
                    stream.newLine()
                }
                stream.endText()
            }
            doc.save(file)
        }
        val pages = PdfBankReader(app).extract(file, false) { }
        assertEquals(1, pages.size)
        assertFalse(pages.single().usedOcr)
        assertTrue(pages.single().text.contains("Which direction"))
        val q = PdfQuestionParser.parse(pages, file.name).questions.single()
        assertEquals(4, q.options.size)
        assertEquals("East", q.options.first().text)
        assertTrue(PdfQuestionParser.issues(q).isNotEmpty()) // No answer is invented.
        file.delete()
    }
}
