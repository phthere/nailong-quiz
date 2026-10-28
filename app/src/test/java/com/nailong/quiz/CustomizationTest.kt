package com.nailong.quiz

import com.nailong.quiz.data.*
import com.nailong.quiz.domain.*
import com.nailong.quiz.ui.exam.*
import com.nailong.quiz.ui.home.suggestedDailyGoal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomizationTest {
    @Test fun deletionRemovesOnlySelectedImportedBank() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val catalog = BankCatalog(context)
        val question = QuestionJson(1, 1, "JUDGE", "判断题", "测试题", answer = "对")
        val a = catalog.create("删除测试", "delete-a", listOf(question))
        val b = catalog.create("保留测试", "delete-b", listOf(question))
        val db = AppDatabase.get(context, a)
        db.questionDao().insertAll(listOf(question.toEntity()))
        catalog.delete(a)
        assertNull(catalog.find(a.id))
        assertFalse(BankCatalog.questionFile(context, a.id).exists())
        assertFalse(context.getDatabasePath(a.databaseName).exists())
        assertNotNull(BankCatalog(context).find(b.id))
        assertNotNull(catalog.find(BuiltInBank.DEFAULT.id))
        catalog.delete(b)
    }
    @Test fun exactExamCountsRejectUnavailableQuestions() {
        val pool = (1..3).map { Question(it, it, QuestionType.JUDGE, "判断题", "测试", emptyList(), "对", emptyList()) }
        assertNull(ExamConfig(durationMin = 17, count = 3, typeCounts = mapOf(QuestionType.JUDGE to 3)).validationError(pool))
        assertNotNull(ExamConfig(count = 4).validationError(pool))
        assertNotNull(ExamConfig(count = 3, typeCounts = mapOf(QuestionType.JUDGE to 2)).validationError(pool))
        assertNotNull(ExamConfig(durationMin = 0, count = 1).validationError(pool))
        assertNotNull(ExamConfig(count = 1, typeCounts = mapOf(QuestionType.FILL to 1)).validationError(pool))
    }
    @Test fun dailySuggestionRoundsUpAndHandlesExamDay() {
        val today = LocalDate.of(2026, 10, 28)
        assertEquals(38, suggestedDailyGoal(379, today.plusDays(10), today))
        assertEquals(379, suggestedDailyGoal(379, today, today))
        assertEquals(1, suggestedDailyGoal(0, today, today))
    }
}
