package com.nailong.quiz.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Read text directly; document images are never silently treated as recognized questions. */
class DocumentBankReader(private val context: Context) {
    suspend fun read(uri: Uri, progress: (String) -> Unit): BankReadResult = withContext(Dispatchers.IO) {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val column = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && c.moveToFirst()) c.getString(column) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "新题库"
        val file = File.createTempFile("document-import-", ".tmp", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            progress("正在读取文档…")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 50L * 1024 * 1024) { "文档超过 50 MB，请拆分后导入" }
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                    }
                }
            } ?: error("无法读取所选文档，请重新选择")
            val result = extract(file, name, progress)
            result.copy(sha256 = digest.digest().joinToString("") { "%02x".format(it) })
        } catch (e: java.util.zip.ZipException) {
            throw IllegalArgumentException("DOCX 文件损坏或并非有效 Word 文件，请在 Word/WPS 另存为 .docx 后重试")
        } catch (e: org.xmlpull.v1.XmlPullParserException) {
            throw IllegalArgumentException("Word 正文格式无法读取，请在 Word/WPS 另存为标准 .docx 后重试")
        } finally { file.delete() }
    }

    internal suspend fun extract(file: File, name: String, progress: (String) -> Unit = {}): BankReadResult = withContext(Dispatchers.IO) {
        val signature = ByteArray(8)
        val length = file.inputStream().use { it.read(signature) }
        require(length > 0) { "文档为空" }
        val extension = name.substringAfterLast('.', "").lowercase()
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
        require(!signature.contentEquals(ole) && extension != "doc") {
            "旧版 .doc 或加密 Word 暂不能直接导入，请用 Word/WPS 另存为无密码的 .docx 后重试；只改后缀无效"
        }
        val isZip = signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte()
        if (isZip) {
            require(extension !in setOf("docm", "odt", "xlsx", "pptx")) { "请选择 Word .docx 或纯文本 .txt 文件" }
            progress("正在读取 Word 段落、表格和自动编号…")
            val ctx = currentCoroutineContext()
            val extracted = DocxTextExtractor.extract(file) { ctx.ensureActive() }
            require(extracted.text.isNotBlank()) { "文档没有可读取的正文文字；图片型 Word 请另存为 PDF 后使用扫描识别" }
            BankReadResult(name, "", listOf(PdfTextPage(1, extracted.text, locationLabel = "Word 正文")),
                ImportFormat.DOCX, extracted.warnings)
        } else {
            require(extension !in setOf("docx", "docm", "rtf", "odt", "pdf", "xls", "xlsx", "ppt", "pptx")) {
                "文件格式不匹配，请选择有效的 .docx 或 .txt；PDF 请使用 PDF 导入入口"
            }
            require(file.length() <= 10L * 1024 * 1024) { "TXT 超过 10 MB，请拆分后导入" }
            progress("正在直接读取题库文字…")
            val text = decodeText(file.readBytes())
            BankReadResult(name, "", listOf(PdfTextPage(1, text, locationLabel = "文本正文")), ImportFormat.TXT)
        }
    }

    companion object {
        internal fun decodeText(bytes: ByteArray): String {
            fun decode(charset: Charset, offset: Int = 0): String = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
            fun starts(vararg values: Int) = bytes.size >= values.size && values.indices.all { (bytes[it].toInt() and 255) == values[it] }
            val text = when {
                starts(0xFF, 0xFE) -> decode(Charsets.UTF_16LE, 2)
                starts(0xFE, 0xFF) -> decode(Charsets.UTF_16BE, 2)
                starts(0xEF, 0xBB, 0xBF) -> decode(Charsets.UTF_8, 3)
                else -> runCatching { decode(Charsets.UTF_8) }.getOrElse {
                    runCatching { decode(Charset.forName("GB18030")) }.getOrElse {
                        error("无法确定 TXT 编码，请另存为 UTF-8 后导入")
                    }
                }
            }.removePrefix("\uFEFF")
            require(text.length <= 2_000_000) { "文档文字超过 200 万字符，请拆分后导入" }
            require(text.isNotBlank() && text.none { it == '\u0000' || (it.code < 32 && it !in "\t\r\n") }) {
                "这不是可读取的文本文件，请另存为 UTF-8 TXT 或 Word .docx 后导入"
            }
            return text.replace("\r\n", "\n").replace('\r', '\n')
        }
    }
}
