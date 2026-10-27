package com.nailong.quiz.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportTime: Long = System.currentTimeMillis(),
    val records: List<AnswerRecordEntity>,
    val wrongs: List<WrongQuestionEntity>,
    val favorites: List<FavoriteEntity>,
    val exams: List<ExamRecordEntity>,
    val examAnswers: List<ExamAnswerEntity>
)

class BackupManager(private val context: Context, private val db: AppDatabase) {

    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val data = BackupData(
                records = db.recordDao().all(),
                wrongs = db.wrongDao().all(),
                favorites = db.favoriteDao().all(),
                exams = db.examDao().exams(),
                examAnswers = db.examDao().exams().flatMap { db.examDao().answers(it.id) }
            )
            val json = Jsons.json.encodeToString(BackupData.serializer(), data)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: error("无法写入文件")
            data.records.size
        }
    }

    suspend fun importFrom(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取文件")
            val data = Jsons.json.decodeFromString(BackupData.serializer(), text)
            db.recordDao().insertAll(data.records)
            db.wrongDao().insertAll(data.wrongs)
            db.favoriteDao().insertAll(data.favorites)
            db.examDao().insertExamsAll(data.exams)
            db.examDao().insertAnswersAll(data.examAnswers)
            "恢复成功：${data.records.size} 条答题记录、${data.wrongs.size} 道错题、${data.favorites.size} 条收藏、${data.exams.size} 次考试"
        }
    }
}
