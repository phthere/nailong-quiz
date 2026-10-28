package com.nailong.quiz.data

import android.content.Context
import com.nailong.quiz.domain.Option
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class QuestionJson(
    val id: Int,
    val origNo: Int,
    val type: String,
    val typeZh: String,
    val stem: String,
    val options: List<Option> = emptyList(),
    val answer: String = "",
    val fillAnswers: List<String> = emptyList(),
    val conflict: Boolean = false,
    val source: String = ""
)

@Serializable
data class QuestionBankJson(val version: Int, val count: Int, val questions: List<QuestionJson>)

object Jsons {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}

class QuestionImporter(private val context: Context, private val db: AppDatabase, private val bank: QuizBank) {

    private fun readBank(): QuestionBankJson {
        val text = when (val selected = bank) {
            is BuiltInBank -> context.assets.open(selected.assetFile).bufferedReader().use { it.readText() }
            is ImportedBank -> BankCatalog.questionFile(context, selected.id).readText()
            else -> error("不支持的题库")
        }
        return Jsons.json.decodeFromString<QuestionBankJson>(text)
    }

    /** 若数据库为空则从 assets 导入题库，返回导入数量 */
    suspend fun importIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (db.questionDao().count() > 0) return@withContext db.questionDao().count()
        val data = readBank()
        require(data.count == data.questions.size && data.count > 0) { "题库内容不完整" }
        db.questionDao().insertAll(data.questions.map { it.toEntity() })
        data.questions.size
    }

    suspend fun bankInfo(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val data = readBank()
        data.version to data.count
    }
}

fun QuestionJson.toEntity() = QuestionEntity(
    id = id,
    origNo = origNo,
    type = type,
    typeZh = typeZh,
    stem = stem,
    optionsJson = Jsons.json.encodeToString(options),
    answer = answer,
    fillAnswersJson = Jsons.json.encodeToString(fillAnswers),
    conflict = conflict,
    source = source
)

fun QuestionEntity.toDomain(): Question = Question(
    id = id,
    origNo = origNo,
    type = QuestionType.from(type),
    typeZh = typeZh,
    stem = stem,
    options = runCatching { Jsons.json.decodeFromString<List<Option>>(optionsJson) }.getOrDefault(emptyList()),
    answer = answer,
    fillAnswers = runCatching { Jsons.json.decodeFromString<List<String>>(fillAnswersJson) }.getOrDefault(emptyList()),
    conflict = conflict,
    source = source
)

fun Question.toQuestionJson() = QuestionJson(id, origNo, type.name, typeZh, stem, options, answer, fillAnswers, conflict, source)

fun QuestionJson.markForReview(): QuestionJson = copy(conflict = conflict || PdfQuestionParser.issues(this).isNotEmpty())
