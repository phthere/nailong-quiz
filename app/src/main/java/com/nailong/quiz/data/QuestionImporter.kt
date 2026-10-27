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

class QuestionImporter(private val context: Context, private val db: AppDatabase, private val bank: BuiltInBank) {

    /** 若数据库为空则从 assets 导入题库，返回导入数量 */
    suspend fun importIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (db.questionDao().count() > 0) return@withContext db.questionDao().count()
        val text = context.assets.open(bank.assetFile).bufferedReader().use { it.readText() }
        val bank = Jsons.json.decodeFromString<QuestionBankJson>(text)
        db.questionDao().insertAll(bank.questions.map { it.toEntity() })
        bank.questions.size
    }

    suspend fun bankInfo(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val text = context.assets.open(bank.assetFile).bufferedReader().use { it.readText() }
        val bank = Jsons.json.decodeFromString<QuestionBankJson>(text)
        bank.version to bank.count
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
