package com.nailong.quiz.data

import android.content.Context
import android.util.AtomicFile
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class ImportedBank(
    override val id: Int,
    override val title: String,
    override val count: Int,
    val documentSha256: String,
    val createdAt: Long = System.currentTimeMillis()
) : QuizBank {
    override val databaseName: String get() = "nailong_imported_bank_${id}.db"
}

/** Only new banks are written here. Existing databases and built-in IDs stay unchanged. */
class BankCatalog(private val context: Context) {
    private val mutex = Mutex()
    private val index = AtomicFile(File(context.filesDir, "imported_banks/index.json"))
    private val loaded = runCatching { readIndex() }
    val loadError: String? = loaded.exceptionOrNull()?.let { "导入题库目录读取失败，原文件已保留：${it.message}" }
    private val imported = loaded.getOrDefault(emptyList())
    private val mutableBanks = MutableStateFlow<List<QuizBank>>(BuiltInBank.entries + imported)
    val banks: StateFlow<List<QuizBank>> = mutableBanks

    fun find(id: Int): QuizBank? = banks.value.firstOrNull { it.id == id }
    fun findDocument(sha256: String): ImportedBank? = banks.value.filterIsInstance<ImportedBank>()
        .firstOrNull { it.documentSha256 == sha256 }

    private fun readIndex(): List<ImportedBank> {
        if (!index.baseFile.exists() && !File(index.baseFile.path + ".bak").exists()) return emptyList()
        // Fail visibly on corruption rather than silently losing the user's imported bank list.
        return index.openRead().bufferedReader().use {
            Jsons.json.decodeFromString<List<ImportedBank>>(it.readText())
        }.also { list ->
            require(list.all { it.id >= FIRST_IMPORTED_ID && it.count > 0 }) { "导入题库目录损坏" }
            require(list.map { it.id }.distinct().size == list.size) { "导入题库编号重复" }
        }
    }

    suspend fun create(title: String, documentSha256: String, questions: List<QuestionJson>): ImportedBank =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(loadError == null) { loadError.orEmpty() }
                require(questions.isNotEmpty()) { "至少需要一道题" }
                require(questions.size <= 5000) { "单个题库最多 5000 道题" }
                val current = banks.value.filterIsInstance<ImportedBank>()
                val id = (current.maxOfOrNull { it.id } ?: (FIRST_IMPORTED_ID - 1)) + 1
                val baseTitle = title.trim().ifBlank { "新题库" }.take(74)
                var bankTitle = baseTitle
                var suffix = 2
                while (banks.value.any { it.title == bankTitle }) bankTitle = "$baseTitle（${suffix++}）"
                val bank = ImportedBank(id, bankTitle, questions.size, documentSha256)
                val data = QuestionBankJson(1, questions.size, questions.mapIndexed { i, q -> q.markForReview().copy(id = i + 1) })
                val questionFile = questionFile(context, id)
                writeAtomic(AtomicFile(questionFile), Jsons.json.encodeToString(data))
                try {
                    writeAtomic(index, Jsons.json.encodeToString(current + bank))
                } catch (e: Exception) {
                    questionFile.delete()
                    throw e
                }
                mutableBanks.value = BuiltInBank.entries + current + bank
                bank
            }
        }

    /** Persist edits with stable question IDs and keep all learning records. */
    suspend fun updateQuestion(bank: ImportedBank, db: AppDatabase, question: QuestionJson): QuestionJson = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = questionFile(context, bank.id)
            val previous = file.readText()
            val data = Jsons.json.decodeFromString<QuestionBankJson>(previous)
            val original = data.questions.firstOrNull { it.id == question.id } ?: error("题目不存在")
            val updated = question.copy(id = original.id, origNo = original.origNo, conflict = false).markForReview()
            val next = data.copy(questions = data.questions.map { if (it.id == updated.id) updated else it })
            try {
                db.withTransaction {
                    db.questionDao().insertAll(listOf(updated.toEntity()))
                    writeAtomic(AtomicFile(file), Jsons.json.encodeToString(next))
                }
            } catch (e: Exception) {
                runCatching { writeAtomic(AtomicFile(file), previous) }.exceptionOrNull()?.let { e.addSuppressed(it) }
                throw e
            }
            updated
        }
    }

    private fun writeAtomic(file: AtomicFile, text: String) {
        check(file.baseFile.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "无法创建题库目录" }
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    companion object {
        private const val FIRST_IMPORTED_ID = 1000
        fun questionFile(context: Context, id: Int): File {
            require(id >= FIRST_IMPORTED_ID)
            return File(context.filesDir, "imported_banks/$id.json")
        }
    }
}
