package com.nailong.quiz

import com.nailong.quiz.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NailongApp::class)
class DocumentBankReaderTest {
    private val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private fun p(text: String, properties: String = "") = "<w:p>$properties<w:r><w:t>${escape(text)}</w:t></w:r></w:p>"
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun word(body: String, numbering: String? = null, styles: String? = null): File {
        val file = File.createTempFile("word-test-", ".docx", RuntimeEnvironment.getApplication().cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            val parts = mutableMapOf("[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                "word/document.xml" to "<w:document xmlns:w=\"$ns\"><w:body>$body</w:body></w:document>")
            numbering?.let { parts["word/numbering.xml"] = "<w:numbering xmlns:w=\"$ns\">$it</w:numbering>" }
            styles?.let { parts["word/styles.xml"] = "<w:styles xmlns:w=\"$ns\">$it</w:styles>" }
            parts.forEach { (name, xml) -> zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry() }
        }
        return file
    }
    private fun listProperties(num: Int, level: Int = 0) = "<w:pPr><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"$num\"/></w:numPr></w:pPr>"
    private val levels = """
        <w:abstractNum w:abstractNumId="0">
          <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl>
          <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="upperLetter"/><w:lvlText w:val="%2."/></w:lvl>
        </w:abstractNum>
        <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
    """.trimIndent()

    @Test fun `read fragmented Word text and inline answers without OCR`() = runBlocking {
        val file = word("<w:p><w:r><w:t>1. 中国的首</w:t></w:r><w:r><w:t>都是哪里？</w:t></w:r></w:p>" +
            p("A. 北京") + p("B. 上海") + p("答案：A"))
        val data = DocumentBankReader(RuntimeEnvironment.getApplication()).extract(file, "题库.docx")
        assertEquals(ImportFormat.DOCX, data.format)
        assertFalse(data.pages.single().usedOcr)
        val q = PdfQuestionParser.parse(data.pages, data.fileName).questions.single()
        assertEquals("中国的首都是哪里?", q.stem)
        assertEquals("A", q.answer)
        assertTrue(q.source.contains("Word 正文"))
        assertFalse(q.source.contains("第 1 页"))
        assertTrue(PdfQuestionParser.issues(q).isEmpty())
    }

    @Test fun `restore automatic numeric question labels and letter option labels with restarts`() = runBlocking {
        val body = p("第一题是什么？", listProperties(1)) + p("甲", listProperties(1, 1)) + p("乙", listProperties(1, 1)) + p("答案：A") +
            p("第二题是什么？", listProperties(1)) + p("丙", listProperties(1, 1)) + p("丁", listProperties(1, 1)) + p("答案：B")
        val data = DocumentBankReader(RuntimeEnvironment.getApplication()).extract(word(body, levels), "编号.docx")
        val questions = PdfQuestionParser.parse(data.pages, data.fileName).questions
        assertEquals(listOf(1, 2), questions.map { it.origNo })
        assertEquals(listOf("A", "B"), questions.map { it.answer })
        assertTrue(questions.all { it.options.map { o -> o.label } == listOf("A", "B") && PdfQuestionParser.issues(it).isEmpty() })
    }

    @Test fun `inherit numbering through paragraph styles and honor start overrides`() = runBlocking {
        val styles = "<w:style w:styleId=\"Base\"><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"2\"/></w:numPr></w:pPr></w:style>" +
            "<w:style w:styleId=\"Question\"><w:basedOn w:val=\"Base\"/></w:style>"
        val numbering = levels + "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"0\"/><w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"8\"/></w:lvlOverride></w:num>"
        val body = p("题目", "<w:pPr><w:pStyle w:val=\"Question\"/></w:pPr>") + p("A. 甲 B. 乙") + p("答案：B")
        val data = DocumentBankReader(RuntimeEnvironment.getApplication()).extract(word(body, numbering, styles), "样式.docx")
        assertEquals(8, PdfQuestionParser.parse(data.pages, data.fileName).questions.single().origNo)
    }

    @Test fun `restore structured question tables with choices and judgment rows`() = runBlocking {
        fun row(vararg cells: String) = "<w:tr>" + cells.joinToString("") { "<w:tc>${p(it)}</w:tc>" } + "</w:tr>"
        val body = "<w:tbl>" + row("题号", "题型", "题干", "A", "B", "答案") +
            row("1", "单选", "一个选择题？", "第一项", "第二项", "B") +
            row("2", "判断", "地球是行星。", "", "", "对") + "</w:tbl>"
        val data = DocumentBankReader(RuntimeEnvironment.getApplication()).extract(word(body), "表格.docx")
        val questions = PdfQuestionParser.parse(data.pages, data.fileName).questions
        assertEquals(listOf("SINGLE", "JUDGE"), questions.map { it.type })
        assertEquals(listOf("B", "对"), questions.map { it.answer })
        assertTrue(questions.all { PdfQuestionParser.issues(it).isEmpty() })
    }

    @Test fun `ordinary tables preserve question order and separate number cells`() = runBlocking {
        val body = "<w:tbl><w:tr><w:tc>${p("1")}</w:tc><w:tc>${p("题目？")}${p("A. 甲 B. 乙")}${p("答案：A")}</w:tc></w:tr></w:tbl>"
        val data = DocumentBankReader(RuntimeEnvironment.getApplication()).extract(word(body), "表.docx")
        assertEquals("A", PdfQuestionParser.parse(data.pages, data.fileName).questions.single().answer)
    }

    @Test fun `images and math produce explicit warnings and do not invent questions`() = runBlocking {
        val body = "<w:p><w:r><w:drawing/></w:r></w:p>"
        val result = runCatching { DocumentBankReader(RuntimeEnvironment.getApplication()).extract(word(body), "图片.docx") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("图片型 Word"))
    }

    @Test fun `skip deleted tracked text and concatenate inserted text`() = runBlocking {
        val body = "<w:p><w:del><w:r><w:delText>9. 错误旧题</w:delText></w:r></w:del><w:ins><w:r><w:t>1. 正确新题</w:t></w:r></w:ins></w:p>" + p("A. 甲 B. 乙") + p("答案：A")
        val data = DocumentBankReader(RuntimeEnvironment.getApplication()).extract(word(body), "修订.docx")
        assertFalse(data.pages.single().text.contains("旧题"))
        assertTrue(data.warnings.any { it.contains("修订") })
        assertEquals(1, PdfQuestionParser.parse(data.pages, data.fileName).questions.single().origNo)
    }

    @Test fun `read UTF8 BOM UTF16 and GB18030 TXT without replacement characters`() {
        val text = "1. 中国的首都？\nA. 北京 B. 上海\n答案：A"
        listOf(Charsets.UTF_8, Charsets.UTF_16, Charset.forName("GB18030")).forEach { charset ->
            assertEquals(text, DocumentBankReader.decodeText(text.toByteArray(charset)))
        }
        assertEquals(text, DocumentBankReader.decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray()))
    }

    @Test fun `reject binary and oversized TXT`() {
        assertTrue(runCatching { DocumentBankReader.decodeText(byteArrayOf(0, 1, 2)) }.isFailure)
        assertTrue(runCatching { DocumentBankReader.decodeText(ByteArray(2_000_001) { 'A'.code.toByte() }) }.isFailure)
    }

    @Test fun `read actual TXT file and import a separate bank`() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NailongApp
        val file = File(app.cacheDir, "document-reader.txt").apply { writeText("1. 一个问题？\nA. 甲 B. 乙\n答案：A") }
        val data = DocumentBankReader(app).read(android.net.Uri.fromFile(file)) { }
        assertEquals(ImportFormat.TXT, data.format)
        val bank = app.bankCatalog.create("TXT 题库", data.sha256, PdfQuestionParser.parse(data.pages, data.fileName).questions)
        val repo = QuizRepository(app, AppDatabase.get(app, bank), app.settingsRepo, bank)
        repo.ensureImported()
        assertEquals(1, repo.questionCount())
        assertEquals("A", repo.allQuestions().single().answer)
    }

    @Test fun `reject old DOC wrong extensions broken zip and XML DTD`() = runBlocking {
        val reader = DocumentBankReader(RuntimeEnvironment.getApplication())
        val old = File(RuntimeEnvironment.getApplication().cacheDir, "old.doc").apply { writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())) }
        assertTrue(runCatching { reader.extract(old, "旧题库.doc") }.isFailure)
        assertTrue(runCatching { reader.extract(old, "伪装.docx") }.isFailure)
        val text = File(old.parentFile, "fake.docx").apply { writeText("不是Word文件") }
        assertTrue(runCatching { reader.extract(text, text.name) }.isFailure)
        val dtd = word(p("1. 题目"))
        val malicious = "<!DOCTYPE w:document [<!ENTITY xxe SYSTEM 'file:///nonexistent'>]><w:document xmlns:w=\"$ns\"><w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>"
        ZipOutputStream(dtd.outputStream()).use { it.putNextEntry(ZipEntry("word/document.xml")); it.write(malicious.toByteArray()); it.closeEntry() }
        assertTrue(runCatching { reader.extract(dtd, "DTD.docx") }.isFailure)
    }
}
