package com.nailong.quiz.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "questions", indices = [Index("type"), Index("origNo")])
data class QuestionEntity(
    @PrimaryKey val id: Int,
    val origNo: Int,
    val type: String,            // SINGLE/MULTI/JUDGE/FILL/ESSAY
    val typeZh: String,
    val stem: String,
    val optionsJson: String,     // List<Option> JSON
    val answer: String,          // 原始答案
    val fillAnswersJson: String, // List<String> JSON
    val conflict: Boolean = false,
    val source: String = ""
)

@Serializable
@Entity(tableName = "answer_records", indices = [Index("questionId"), Index("timestamp")])
data class AnswerRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Int,
    val sessionType: String,     // SEQUENTIAL/RANDOM/WRONG/FAVORITE/EXAM/SPRINT
    val correct: Boolean,
    val userAnswer: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "wrong_questions", indices = [Index("questionId", unique = true)])
data class WrongQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Int,
    val wrongCount: Int,
    val lastWrongTime: Long,
    val consecutiveCorrect: Int = 0,
    val mastered: Boolean = false,
    val lastUserAnswer: String = ""
)

@Serializable
@Entity(tableName = "favorites", indices = [Index("questionId", unique = true)])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "practice_sessions")
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,            // SEQUENTIAL/RANDOM/WRONG/FAVORITE/SPRINT
    val filterJson: String,      // 练习配置（范围/题型/数量）
    val questionIdsJson: String, // 本次题目顺序
    val currentIndex: Int = 0,
    val answeredJson: String = "{}", // questionId -> userAnswer 快照
    val correctCount: Int = 0,
    val finished: Boolean = false,
    val startTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "exam_records")
data class ExamRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationLimitSec: Int,
    val totalCount: Int,
    val correctCount: Int,
    val score: Double,
    val configJson: String
)

@Serializable
@Entity(tableName = "exam_answers", indices = [Index("examId")])
data class ExamAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val questionId: Int,
    val userAnswer: String,      // 展示label拼接 / 填空输入;分隔 / "" 未答
    val correct: Boolean,
    val marked: Boolean = false,
    val labelMapJson: String = "{}" // 选项打乱映射快照，供回看
)
