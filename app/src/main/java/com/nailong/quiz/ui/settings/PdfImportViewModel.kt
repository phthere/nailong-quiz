package com.nailong.quiz.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.PdfBankReader
import com.nailong.quiz.data.PdfQuestionParser
import com.nailong.quiz.data.BankReadResult
import com.nailong.quiz.data.DocumentBankReader
import com.nailong.quiz.data.QuestionJson
import com.nailong.quiz.domain.QuestionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportDraft(val question: QuestionJson, val included: Boolean = true)
data class PdfImportState(
    val busy: Boolean = false,
    val progress: String = "",
    val title: String = "",
    val document: BankReadResult? = null,
    val drafts: List<ImportDraft> = emptyList(),
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val savedBankId: Int? = null
)

class PdfImportViewModel(private val app: NailongApp) : ViewModel() {
    private val mutableState = MutableStateFlow(PdfImportState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null

    fun read(uri: Uri, forceOcr: Boolean) {
        readSource { progress -> PdfBankReader(app).read(uri, forceOcr, progress) }
    }

    fun readDocument(uri: Uri) {
        readSource { progress -> DocumentBankReader(app).read(uri, progress) }
    }

    private fun readSource(reader: suspend ((String) -> Unit) -> BankReadResult) {
        if (state.value.busy) return
        work = viewModelScope.launch {
            mutableState.value = PdfImportState(busy = true, progress = "正在读取题库文件…")
            try {
                val doc = reader { text -> mutableState.update { it.copy(progress = text) } }
                check(app.bankCatalog.findDocument(doc.sha256) == null) { "这份文件已导入，请从顶部“切换”打开已有题库" }
                val parsed = withContext(Dispatchers.Default) { PdfQuestionParser.parse(doc.pages, doc.fileName) }
                mutableState.value = PdfImportState(title = doc.fileName.substringBeforeLast('.').take(80), document = doc,
                    drafts = parsed.questions.map { ImportDraft(it) }, warnings = doc.warnings + parsed.warnings)
            } catch (e: CancellationException) {
                mutableState.value = PdfImportState()
                throw e
            } catch (e: Exception) {
                mutableState.value = PdfImportState(error = e.message ?: "文件读取失败，请尝试其他文件")
            }
        }
    }

    fun cancel() { work?.cancel() }
    fun title(value: String) { mutableState.update { it.copy(title = value.take(80)) } }
    fun include(id: Int, value: Boolean) { mutableState.update { s -> s.copy(drafts = s.drafts.map { if (it.question.id == id) it.copy(included = value) else it }) } }
    fun update(question: QuestionJson) {
        mutableState.update { s -> s.copy(drafts = s.drafts.map { if (it.question.id == question.id) it.copy(question = question) else it }) }
    }
    fun excludeInvalid() {
        mutableState.update { s -> s.copy(drafts = s.drafts.map { if (PdfQuestionParser.issues(it.question).isNotEmpty()) it.copy(included = false) else it }) }
    }
    fun addQuestion(): QuestionJson {
        val id = (state.value.drafts.maxOfOrNull { it.question.id } ?: 0) + 1
        val question = QuestionJson(id, id, "ESSAY", QuestionType.ESSAY.zh, "", source = "手动补充")
        mutableState.update { s ->
            s.copy(drafts = s.drafts + ImportDraft(question))
        }
        return question
    }
    fun save() {
        val current = state.value
        val doc = current.document ?: return
        if (current.busy) return
        work = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, progress = "正在保存新题库…", error = null) }
            try {
                val bank = app.bankCatalog.create(current.title, doc.sha256, current.drafts.filter { it.included }.map { it.question })
                // Keep the current bank selected until the user leaves this screen; saving never changes its records.
                mutableState.update { it.copy(busy = false, savedBankId = bank.id) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutableState.update { it.copy(busy = false, error = e.message ?: "保存失败") } }
        }
    }
}

class PdfImportViewModelFactory(private val app: NailongApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PdfImportViewModel(app) as T
}
