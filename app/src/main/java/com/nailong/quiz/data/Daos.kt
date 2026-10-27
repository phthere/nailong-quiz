package com.nailong.quiz.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<QuestionEntity>)

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM questions")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM questions ORDER BY id")
    fun allFlow(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions ORDER BY id")
    suspend fun all(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun byId(id: Int): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Int>): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE type = :type ORDER BY id")
    suspend fun byType(type: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE stem LIKE '%' || :kw || '%' OR optionsJson LIKE '%' || :kw || '%' ORDER BY id LIMIT 200")
    suspend fun search(kw: String): List<QuestionEntity>
}

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: AnswerRecordEntity): Long

    @Query("SELECT COUNT(*) FROM answer_records")
    suspend fun totalAnswers(): Int

    @Query("SELECT COUNT(DISTINCT questionId) FROM answer_records")
    suspend fun practicedCount(): Int

    @Query("SELECT COUNT(*) FROM answer_records WHERE correct = 1")
    suspend fun correctCount(): Int

    @Query("SELECT COUNT(*) FROM answer_records WHERE timestamp >= :since")
    suspend fun answersSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM answer_records WHERE correct = 1 AND timestamp >= :since")
    suspend fun correctSince(since: Long): Int

    @Query("SELECT DISTINCT questionId FROM answer_records")
    suspend fun practicedIds(): List<Int>

    @Query("SELECT DISTINCT questionId FROM answer_records WHERE sessionType = :sessionType")
    suspend fun practicedIdsBySession(sessionType: String): List<Int>

    @Query("SELECT questionId, SUM(CASE WHEN correct THEN 1 ELSE 0 END) AS c, COUNT(*) AS t FROM answer_records GROUP BY questionId")
    suspend fun perQuestionStats(): List<QuestionStat>

    @Query("SELECT timestamp FROM answer_records ORDER BY timestamp DESC")
    suspend fun allTimestamps(): List<Long>

    @Query("SELECT * FROM answer_records WHERE timestamp >= :since ORDER BY timestamp")
    suspend fun recordsSince(since: Long): List<AnswerRecordEntity>

    @Query("SELECT COUNT(*) FROM answer_records WHERE questionId = :qid AND correct = 0")
    suspend fun wrongTimes(qid: Int): Int

    @Query("DELETE FROM answer_records")
    suspend fun clear()

    @Query("SELECT * FROM answer_records ORDER BY id")
    suspend fun all(): List<AnswerRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<AnswerRecordEntity>)
}

data class QuestionStat(val questionId: Int, val c: Int, val t: Int)

@Dao
interface WrongDao {
    @Query("SELECT * FROM wrong_questions WHERE questionId = :qid")
    suspend fun byQuestion(qid: Int): WrongQuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WrongQuestionEntity)

    @Query("SELECT * FROM wrong_questions ORDER BY lastWrongTime DESC")
    fun allFlow(): Flow<List<WrongQuestionEntity>>

    @Query("SELECT * FROM wrong_questions")
    suspend fun all(): List<WrongQuestionEntity>

    @Query("SELECT COUNT(*) FROM wrong_questions")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM wrong_questions WHERE mastered = 1")
    fun masteredCountFlow(): Flow<Int>

    @Query("SELECT * FROM wrong_questions WHERE mastered = 0")
    suspend fun unmastered(): List<WrongQuestionEntity>

    @Query("DELETE FROM wrong_questions WHERE questionId = :qid")
    suspend fun removeByQuestion(qid: Int)

    @Query("DELETE FROM wrong_questions")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<WrongQuestionEntity>)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE questionId = :qid")
    suspend fun remove(qid: Int)

    @Query("SELECT questionId FROM favorites")
    fun idsFlow(): Flow<List<Int>>

    @Query("SELECT questionId FROM favorites")
    suspend fun ids(): List<Int>

    @Query("SELECT COUNT(*) FROM favorites")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun allFlow(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun all(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<FavoriteEntity>)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(session: PracticeSessionEntity): Long

    @Query("SELECT * FROM practice_sessions WHERE mode = :mode AND finished = 0 ORDER BY updateTime DESC LIMIT 1")
    suspend fun activeByMode(mode: String): PracticeSessionEntity?

    @Query("UPDATE practice_sessions SET finished = 1 WHERE mode = :mode")
    suspend fun finishByMode(mode: String)

    @Query("DELETE FROM practice_sessions")
    suspend fun clear()
}

@Dao
interface ExamDao {
    @Insert
    suspend fun insertExam(exam: ExamRecordEntity): Long

    @Insert
    suspend fun insertAnswers(list: List<ExamAnswerEntity>)

    @Query("SELECT * FROM exam_records ORDER BY startTime DESC")
    fun examsFlow(): Flow<List<ExamRecordEntity>>

    @Query("SELECT * FROM exam_records ORDER BY startTime DESC")
    suspend fun exams(): List<ExamRecordEntity>

    @Query("SELECT * FROM exam_answers WHERE examId = :examId")
    suspend fun answers(examId: Long): List<ExamAnswerEntity>

    @Query("DELETE FROM exam_records")
    suspend fun clearExams()

    @Query("DELETE FROM exam_answers")
    suspend fun clearAnswers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamsAll(list: List<ExamRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswersAll(list: List<ExamAnswerEntity>)
}
