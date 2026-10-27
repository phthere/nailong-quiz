package com.nailong.quiz

import android.app.Application
import com.nailong.quiz.data.AppDatabase
import com.nailong.quiz.data.BuiltInBank
import com.nailong.quiz.data.BankCatalog
import com.nailong.quiz.data.QuizRepository
import com.nailong.quiz.data.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NailongApp : Application() {
    private val bankMutex = Mutex()
    lateinit var db: AppDatabase
        private set
    lateinit var settingsRepo: SettingsRepository
        private set
    lateinit var repository: QuizRepository
        private set
    lateinit var bankCatalog: BankCatalog
        private set

    override fun onCreate() {
        super.onCreate()
        bankCatalog = BankCatalog(this)
        settingsRepo = SettingsRepository(this)
        db = AppDatabase.get(this, BuiltInBank.DEFAULT)
        repository = QuizRepository(this, db, settingsRepo, BuiltInBank.DEFAULT)
    }

    suspend fun activateBank(id: Int) = bankMutex.withLock {
        val bank = bankCatalog.find(id) ?: BuiltInBank.DEFAULT
        val nextDb = AppDatabase.get(this, bank)
        val nextRepository = QuizRepository(this, nextDb, settingsRepo, bank)
        nextRepository.ensureImported()
        db = nextDb
        repository = nextRepository
        if (bank.id != id) settingsRepo.update { it.copy(selectedBank = bank.id) }
    }
}
