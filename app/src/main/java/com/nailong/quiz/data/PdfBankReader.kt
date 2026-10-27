package com.nailong.quiz.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

data class PdfReadResult(val fileName: String, val sha256: String, val pages: List<PdfTextPage>)

class PdfBankReader(private val context: Context) {
    suspend fun read(uri: Uri, forceOcr: Boolean, progress: (String) -> Unit): PdfReadResult = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "新题库.pdf"
        val file = File.createTempFile("quiz-import-", ".pdf", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            progress("正在读取 PDF…")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 50L * 1024 * 1024) { "PDF 超过 50 MB，请压缩或拆分后导入" }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            } ?: error("无法读取所选文件，请重新选择")
            val header = file.inputStream().use { input ->
                val bytes = ByteArray(1024)
                val size = input.read(bytes).coerceAtLeast(0)
                String(bytes, 0, size, Charsets.ISO_8859_1)
            }
            require(header.contains("%PDF-")) { "请选择 PDF 文件，不能只修改文件后缀" }
            val pages = extract(file, forceOcr, progress)
            PdfReadResult(name, digest.digest().joinToString("") { "%02x".format(it) }, pages)
        } catch (e: InvalidPasswordException) {
            throw IllegalArgumentException("PDF 已加密，请使用你有权打开的无密码副本")
        } catch (e: SecurityException) {
            throw IllegalArgumentException("无法打开此 PDF，请检查文件访问权限或使用无密码副本")
        } finally {
            file.delete()
        }
    }

    internal suspend fun extract(file: File, forceOcr: Boolean, progress: (String) -> Unit): List<PdfTextPage> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)).use { doc ->
            require(doc.numberOfPages in 1..1000) { "单次支持 1–1000 页 PDF，请拆分后导入" }
            require(doc.currentAccessPermission.canExtractContent()) { "这份 PDF 限制了内容提取，请使用允许提取的副本" }
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val recognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
            var renderer: PdfRenderer? = null
            var ocrCreated = false
            var characters = 0
            try {
                (0 until doc.numberOfPages).map { i ->
                    currentCoroutineContext().ensureActive()
                    progress("正在读取第 ${i + 1} / ${doc.numberOfPages} 页…")
                    stripper.startPage = i + 1
                    stripper.endPage = i + 1
                    val embedded = if (forceOcr) "" else stripper.getText(doc)
                    val meaningful = embedded.count { !it.isWhitespace() && it != '\uFFFD' }
                    val needsOcr = forceOcr || meaningful < 20 || embedded.count { it == '\uFFFD' } > meaningful / 5
                    val text = if (!needsOcr) embedded else {
                        progress("正在识别第 ${i + 1} / ${doc.numberOfPages} 页图片文字…")
                        if (renderer == null) renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
                        renderer!!.openPage(i).use { page ->
                            val scale = 2200f / maxOf(page.width, page.height).coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1),
                                (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ocrCreated = true
                                // This runs on Dispatchers.IO. Wait for the image task before releasing its bitmap.
                                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                                result.textBlocks.flatMap { it.lines }.sortedWith(
                                    compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
                                ).joinToString("\n") { it.text }
                            } finally { bitmap.recycle() }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    characters += text.length
                    require(characters <= 2_000_000) { "PDF 文字过多，请拆分后导入" }
                    PdfTextPage(i + 1, text, needsOcr)
                }
            } finally {
                renderer?.close()
                if (ocrCreated) recognizer.close()
            }
        }
    }
}
