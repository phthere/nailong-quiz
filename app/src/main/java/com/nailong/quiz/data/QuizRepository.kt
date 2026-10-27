package com.nailong.quiz.data

import android.content.Context
import com.nailong.quiz.domain.Grader
import com.nailong.quiz.domain.Option
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

class QuizRepository(
    private val context: Context,
    val db: AppDatabase,
    val settingsRepo: SettingsRepository,
    val bank: BuiltInBank
) {
    private val importer = QuestionImporter(context, db, bank)

    suspend fun ensureImported() = importer.importIfNeeded()

    suspend fun questionCount(): Int = db.questionDao().count()
    fun questionCountFlow(): Flow<Int> = db.questionDao().countFlow()

    suspend fun allQuestions(): List<Question> = withContext(Dispatchers.IO) {
        db.questionDao().all().map { it.toDomain() }
    }

    suspend fun questionsByIds(ids: List<Int>): List<Question> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyList()
        else db.questionDao().byIds(ids).map { it.toDomain() }.sortedBy { ids.indexOf(it.id) }
    }

    suspend fun byType(type: QuestionType): List<Question> = withContext(Dispatchers.IO) {
        db.questionDao().byType(type.name).map { it.toDomain() }
    }

    suspend fun search(kw: String): List<Question> = withContext(Dispatchers.IO) {
        if (kw.isBlank()) emptyList() else db.questionDao().search(kw.trim()).map { it.toDomain() }
    }

    // ---- 答题 ----

    /** 记录一次答题，返回是否正确；同时维护错题集 */
    suspend fun recordAnswer(
        question: Question,
        correct: Boolean,
        userAnswer: String,
        sessionType: String,
        masteryThreshold: Int
    ) = withContext(Dispatchers.IO) {
        db.recordDao().insert(AnswerRecordEntity(questionId = question.id, sessionType = sessionType, correct = correct, userAnswer = userAnswer))
        val w = db.wrongDao().byQuestion(question.id)
        if (!correct) {
            db.wrongDao().upsert(
                WrongQuestionEntity(
                    id = w?.id ?: 0,
                    questionId = question.id,
                    wrongCount = (w?.wrongCount ?: 0) + 1,
                    lastWrongTime = System.currentTimeMillis(),
                    consecutiveCorrect = 0,
                    mastered = false,
                    lastUserAnswer = userAnswer
                )
            )
        } else if (w != null && !w.mastered) {
            val cc = w.consecutiveCorrect + 1
            db.wrongDao().upsert(w.copy(consecutiveCorrect = cc, mastered = cc >= masteryThreshold))
        }
    }

    suspend fun toggleFavorite(qid: Int) = withContext(Dispatchers.IO) {
        if (qid in db.favoriteDao().ids()) db.favoriteDao().remove(qid) else db.favoriteDao().add(FavoriteEntity(questionId = qid))
    }

    fun favoriteIdsFlow(): Flow<List<Int>> = db.favoriteDao().idsFlow()
    suspend fun favoriteIds(): List<Int> = db.favoriteDao().ids()

    // ---- 随机抽题 ----

    suspend fun pickRandom(
        scope: RandomScope,
        typeFilter: Set<QuestionType>,
        count: Int
    ): List<Question> = withContext(Dispatchers.IO) {
        var pool: List<QuestionEntity> = when (scope) {
            RandomScope.ALL -> db.questionDao().all()
            RandomScope.UNPRACTICED -> {
                val practiced = db.recordDao().practicedIds().toSet()
                db.questionDao().all().filter { it.id !in practiced }
            }
            RandomScope.WRONG -> {
                val ids = db.wrongDao().all().map { it.questionId }
                db.questionDao().byIds(ids)
            }
            RandomScope.FAVORITE -> {
                val ids = db.favoriteDao().ids()
                db.questionDao().byIds(ids)
            }
        }
        if (typeFilter.isNotEmpty()) pool = pool.filter { it.type in typeFilter.map { t -> t.name } }
        pool.shuffled().take(count.coerceAtMost(pool.size)).map { it.toDomain() }
    }

    // ---- 错题 ----

    fun wrongFlow(): Flow<List<WrongQuestionEntity>> = db.wrongDao().allFlow()
    fun wrongCountFlow(): Flow<Int> = db.wrongDao().countFlow()
    fun masteredCountFlow(): Flow<Int> = db.wrongDao().masteredCountFlow()

    suspend fun markMastered(qid: Int, mastered: Boolean) = withContext(Dispatchers.IO) {
        db.wrongDao().byQuestion(qid)?.let { db.wrongDao().upsert(it.copy(mastered = mastered, consecutiveCorrect = if (mastered) 999 else 0)) }
    }

    suspend fun removeWrong(qid: Int) = db.wrongDao().removeByQuestion(qid)
    suspend fun clearWrong() = db.wrongDao().clear()

    // ---- 冲刺选题 ----

    suspend fun pickSprint(count: Int): List<Question> = withContext(Dispatchers.IO) {
        val wrong = db.wrongDao().unmastered().sortedByDescending { it.wrongCount }
        val practiced = db.recordDao().practicedIds().toSet()
        val favs = db.favoriteDao().ids()
        val all = db.questionDao().all()
        val unpracticed = all.filter { it.id !in practiced }
        // 高频错误统计
        val stats = db.recordDao().perQuestionStats().filter { it.t >= 2 && it.c * 1.0 / it.t < 0.6 }.map { it.questionId }

        val picked = LinkedHashSet<Int>()
        wrong.forEach { if (picked.size < count) picked.add(it.questionId) }
        stats.forEach { if (picked.size < count) picked.add(it) }
        unpracticed.shuffled().forEach { if (picked.size < count) picked.add(it.id) }
        favs.forEach { if (picked.size < count) picked.add(it) }
        all.shuffled().forEach { if (picked.size < count) picked.add(it.id) }

        db.questionDao().byIds(picked.toList()).map { it.toDomain() }.shuffled()
    }

    // ---- 统计 ----

    suspend fun practicedCount() = db.recordDao().practicedCount()
    suspend fun totalAnswers() = db.recordDao().totalAnswers()
    suspend fun correctCount() = db.recordDao().correctCount()

    suspend fun todayAnswers(): Int {
        val start = todayStart()
        return db.recordDao().answersSince(start)
    }

    suspend fun streakDays(): Int {
        val ts = db.recordDao().allTimestamps()
        if (ts.isEmpty()) return 0
        val days = ts.map { dayOf(it) }.toSortedSet(compareByDescending { it })
        var streak = 0
        var expect = dayOf(System.currentTimeMillis())
        // 今天没刷过题时从昨天开始算
        if (!days.contains(expect)) expect -= 1
        for (d in days) {
            if (d == expect) { streak++; expect -= 1 }
            else if (d < expect) break
        }
        return streak
    }

    suspend fun dailyCounts(days: Int): List<Pair<Long, Int>> {
        val start = todayStart() - (days - 1) * DAY_MS
        val records = db.recordDao().recordsSince(start)
        val map = records.groupBy { dayOf(it.timestamp) }.mapValues { it.value.size }
        return (0 until days).map { i ->
            val dayStart = start + i * DAY_MS
            dayStart to (map[dayOf(dayStart)] ?: 0)
        }
    }

    suspend fun accuracyByType(): Map<String, Pair<Int, Int>> {
        val records = db.recordDao().all()
        val qtypes = db.questionDao().all().associate { it.id to it.typeZh }
        val agg = mutableMapOf<String, Pair<Int, Int>>()
        records.forEach { r ->
            val t = qtypes[r.questionId] ?: return@forEach
            val cur = agg[t] ?: (0 to 0)
            agg[t] = (cur.first + if (r.correct) 1 else 0) to (cur.second + 1)
        }
        return agg
    }

    suspend fun resetProgress() {
        db.recordDao().clear()
        db.sessionDao().clear()
        settingsRepo.saveSeqIndex(0, bank.id)
    }

    private fun todayStart(): Long = dayOf(System.currentTimeMillis())

    private fun dayOf(time: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    companion object { const val DAY_MS = 24L * 3600 * 1000 }
}

enum class RandomScope(val zh: String) {
    ALL("全部题库"), UNPRACTICED("仅未做题"), WRONG("仅错题"), FAVORITE("仅收藏题")
}

/** 练习用题目装配：按需打乱选项 */
fun Question.withShuffledOptions(enabled: Boolean, random: kotlin.random.Random = kotlin.random.Random): Question {
    if (!enabled || type == QuestionType.JUDGE || type == QuestionType.FILL || type == QuestionType.ESSAY) return this
    val (opts, map) = Grader.shuffleOptions(options, random)
    return copy(options = opts, labelMap = map)
}

fun List<Option>.displayLabels(): String = joinToString("") { it.label }
