package com.nailong.quiz

import androidx.room.Room
import com.nailong.quiz.data.*
import com.nailong.quiz.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NailongApp::class)
class DirectImportTest {
    private fun choice(no: Int, answer: String = "A") = QuestionJson(no, no, "SINGLE", "单选题", "问题 $no", listOf(Option("A", "甲"), Option("B", "乙")), answer)

    @Test fun `all 742 selected questions can be created with 20 incomplete and an empty title`() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NailongApp
        val questions = (1..742).map { choice(it, if (it > 722) "" else "A") }
        val bank = app.bankCatalog.create("", "full-742", questions)
        assertEquals("新题库", bank.title)
        app.activateBank(bank.id)
        val stored = app.repository.allQuestions()
        assertEquals(742, stored.size)
        assertEquals(20, stored.count { it.needsReview })
        assertEquals((1..742).toList(), stored.map { it.origNo })
        assertEquals(722, stored.count { it.canAutoGrade })
        assertEquals(bank, BankCatalog(app).find(bank.id))
    }

    @Test fun `editing an imported question keeps question IDs favorites records and sequential progress`() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NailongApp
        val bank = app.bankCatalog.create("后续完善", "edit-bank", listOf(choice(77, ""), choice(78)))
        app.settingsRepo.update { it.copy(selectedBank = bank.id) }
        app.activateBank(bank.id)
        val repo = app.repository
        val before = repo.allQuestions()
        repo.recordAnswer(before[1], true, "A", "SEQUENTIAL", 2)
        repo.toggleFavorite(before[1].id)
        app.settingsRepo.saveSeqIndex(42, bank.id)
        val result = repo.updateQuestion(before[0].toQuestionJson().copy(origNo = 999, answer = "B"))
        assertEquals(77, result.origNo)
        assertFalse(result.conflict)
        assertEquals(before.map { it.id }, repo.allQuestions().map { it.id })
        assertEquals("B", repo.allQuestions()[0].answer)
        assertFalse(repo.allQuestions()[0].needsReview)
        assertEquals(1, repo.totalAnswers())
        assertEquals(listOf(before[1].id), repo.favoriteIds())
        assertEquals(42, app.settingsRepo.seqIndex.first())
        val json = Jsons.json.decodeFromString(QuestionBankJson.serializer(), BankCatalog.questionFile(app, bank.id).readText())
        assertEquals("B", json.questions[0].answer)
        assertEquals(2, json.count)
    }

    @Test fun `missing or malformed answers use self assessment and never enter automatic grading`() {
        listOf(choice(1, ""), choice(2).copy(options = listOf(Option("A", "甲"), Option("C", "丙"))), choice(3).copy(conflict = true))
            .forEach { raw ->
                val q = raw.markForReview().toEntity().toDomain()
                assertTrue(q.needsReview)
                assertTrue(q.usesSelfAssessment)
                assertFalse(q.canAutoGrade)
                assertFalse(Grader.isCorrect(q, setOf("A"), null))
                assertEquals(q, q.withShuffledOptions(true))
            }
        assertTrue(choice(4).toEntity().toDomain().canAutoGrade)
    }

    @Test fun `consecutive study days advance by calendar dates rather than milliseconds`() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NailongApp
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        try {
            val now = System.currentTimeMillis()
            fun daysAgo(days: Int) = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
            db.recordDao().insert(AnswerRecordEntity(questionId = 1, sessionType = "TEST", correct = true, userAnswer = "A", timestamp = now))
            db.recordDao().insert(AnswerRecordEntity(questionId = 1, sessionType = "TEST", correct = true, userAnswer = "A", timestamp = daysAgo(1)))
            db.recordDao().insert(AnswerRecordEntity(questionId = 1, sessionType = "TEST", correct = true, userAnswer = "A", timestamp = daysAgo(2)))
            val repo = QuizRepository(app, db, app.settingsRepo, BuiltInBank.DEFAULT)
            assertEquals(3, repo.streakDays())
        } finally { db.close() }
    }
}
