package com.nailong.quiz

import com.nailong.quiz.data.BuiltInBank
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = NailongApp::class)
class BankSwitchingTest {
    @Test
    fun `switching banks keeps question pools records favorites and position independent`() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as NailongApp
        suspend fun select(bank: BuiltInBank) {
            app.settingsRepo.update { it.copy(selectedBank = bank.id) }
            app.activateBank(bank.id)
        }

        select(BuiltInBank.DANGZHANG)
        assertEquals(357, app.repository.questionCount())
        val first = app.repository.allQuestions().first()
        app.repository.recordAnswer(first, false, "错误测试答案", "SEQUENTIAL", 2)
        app.repository.toggleFavorite(first.id)
        app.settingsRepo.saveSeqIndex(12, BuiltInBank.DANGZHANG.id)
        assertEquals(1, app.repository.totalAnswers())

        select(BuiltInBank.DANGSHI)
        assertEquals(728, app.repository.questionCount())
        assertEquals(0, app.repository.totalAnswers())
        assertTrue(app.repository.favoriteIds().isEmpty())
        assertTrue(app.db.wrongDao().all().isEmpty())
        assertEquals(0, app.settingsRepo.seqIndex.first())
        app.settingsRepo.saveSeqIndex(25, BuiltInBank.DANGSHI.id)

        select(BuiltInBank.DANGZHANG)
        assertEquals(357, app.repository.questionCount())
        assertEquals(1, app.repository.totalAnswers())
        assertEquals(listOf(first.id), app.repository.favoriteIds())
        assertEquals(first.id, app.db.wrongDao().all().single().questionId)
        assertEquals(12, app.settingsRepo.seqIndex.first())

        select(BuiltInBank.DANGSHI)
        assertEquals(25, app.settingsRepo.seqIndex.first())
        select(BuiltInBank.ORIGINAL)
        assertEquals(1038, app.repository.questionCount())
        assertEquals(0, app.repository.totalAnswers())
        assertEquals(BuiltInBank.ORIGINAL.id, app.settingsRepo.settings.first().selectedBank)
    }
}
