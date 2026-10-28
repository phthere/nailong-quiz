package com.nailong.quiz

import android.app.Application
import com.nailong.quiz.data.AppDatabase
import com.nailong.quiz.data.BuiltInBank
import com.nailong.quiz.data.BankCatalog
import com.nailong.quiz.data.QuizRepository
import com.nailong.quiz.data.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
        repository = QuizRepository(this, db, settingsRepo, BuiltInBank.DEFAULT, bankCatalog)
    }

    suspend fun deleteImportedBank(bank: com.nailong.quiz.data.ImportedBank) = withContext(NonCancellable) { bankMutex.withLock {
        if (repository.bank.id == bank.id) {
            val fallback = BuiltInBank.DEFAULT
            val nextDb = AppDatabase.get(this, fallback)
            val next = QuizRepository(this, nextDb, settingsRepo, fallback, bankCatalog)
            next.ensureImported()
            repository = next
            db = nextDb
            settingsRepo.update { it.copy(selectedBank = fallback.id) }
        }
        bankCatalog.delete(bank)
        settingsRepo.saveSeqIndex(0, bank.id)
    } }

    suspend fun activateBank(id: Int) = bankMutex.withLock {
        val bank = bankCatalog.find(id) ?: BuiltInBank.DEFAULT
        val nextDb = AppDatabase.get(this, bank)
        val nextRepository = QuizRepository(this, nextDb, settingsRepo, bank, bankCatalog)
        nextRepository.ensureImported()
        db = nextDb
        repository = nextRepository
        if (bank.id != id) settingsRepo.update { it.copy(selectedBank = bank.id) }
    }
}
