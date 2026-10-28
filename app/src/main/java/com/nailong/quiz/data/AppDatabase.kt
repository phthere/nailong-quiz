package com.nailong.quiz.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestionEntity::class,
        AnswerRecordEntity::class,
        WrongQuestionEntity::class,
        FavoriteEntity::class,
        PracticeSessionEntity::class,
        ExamRecordEntity::class,
        ExamAnswerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun recordDao(): RecordDao
    abstract fun wrongDao(): WrongDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun sessionDao(): SessionDao
    abstract fun examDao(): ExamDao

    companion object {
        private val instances = mutableMapOf<String, AppDatabase>()

        fun delete(context: Context, bank: QuizBank) = synchronized(this) {
            instances.remove(context.applicationContext.getDatabasePath(bank.databaseName).absolutePath)?.close()
            context.deleteDatabase(bank.databaseName)
        }

        fun get(context: Context, bank: QuizBank): AppDatabase = synchronized(this) {
            instances.getOrPut(context.applicationContext.getDatabasePath(bank.databaseName).absolutePath) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, bank.databaseName)
                    .build()
            }
        }
    }
}
