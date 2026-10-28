package com.nailong.quiz.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.ExamAnswerEntity
import com.nailong.quiz.data.ExamRecordEntity
import com.nailong.quiz.data.Jsons
import com.nailong.quiz.data.withShuffledOptions
import com.nailong.quiz.domain.Grader
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class ExamConfig(
    val durationMin: Int = 60,
    val count: Int = 50,
    val shuffleOptions: Boolean = false,
    val typeFilter: Set<QuestionType> = setOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE, QuestionType.FILL)
)

data class ExamUiState(
    val loading: Boolean = true,
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val answers: Map<Int, Set<String>> = emptyMap(),       // qid -> 展示label
    val fillAnswers: Map<Int, List<String>> = emptyMap(),
    val marked: Set<Int> = emptySet(),
    val remainingSec: Int = 0,
    val submitted: Boolean = false,
    val resultExamId: Long = 0L,
    val error: String? = null
) {
    val current: Question? get() = questions.getOrNull(index)
    val answeredCount: Int get() = answers.size + fillAnswers.count { it.value.any { s -> s.isNotBlank() } }
}

class ExamViewModel(private val app: NailongApp, private val config: ExamConfig) : ViewModel() {

    private val _state = MutableStateFlow(ExamUiState())
    val state = _state.asStateFlow()
    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            app.repository.ensureImported()
            var pool = app.repository.allQuestions().filter { it.type in config.typeFilter && it.canAutoGrade }
            pool = pool.shuffled().take(config.count.coerceAtMost(pool.size))
            if (pool.isEmpty()) {
                _state.update { it.copy(loading = false, error = "暂无可自动评分的题目，可先顺序学习并自评，或在题库中完善答案后再考试。") }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    questions = pool.map { q -> q.withShuffledOptions(config.shuffleOptions) },
                    remainingSec = config.durationMin * 60
                )
            }
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val remain = _state.value.remainingSec - 1
                _state.update { it.copy(remainingSec = remain) }
                if (remain <= 0) {
                    submitExam(auto = true)
                    break
                }
            }
        }
    }

    fun select(label: String) {
        val q = _state.value.current ?: return
        when (q.type) {
            QuestionType.SINGLE, QuestionType.JUDGE -> _state.update { it.copy(answers = it.answers + (q.id to setOf(label))) }
            QuestionType.MULTI -> _state.update {
                val cur = it.answers[q.id] ?: emptySet()
                it.copy(answers = it.answers + (q.id to if (label in cur) cur - label else cur + label))
            }
            else -> {}
        }
    }

    fun updateFill(i: Int, text: String) {
        val q = _state.value.current ?: return
        _state.update { s ->
            val cur = s.fillAnswers[q.id] ?: List(q.blankCount.coerceAtLeast(1)) { "" }
            val next = cur.toMutableList().also { if (i < it.size) it[i] = text }
            s.copy(fillAnswers = s.fillAnswers + (q.id to next))
        }
    }

    fun toggleMark() {
        val q = _state.value.current ?: return
        _state.update { it.copy(marked = if (q.id in it.marked) it.marked - q.id else it.marked + q.id) }
    }

    fun goTo(i: Int) { if (i in _state.value.questions.indices) _state.update { it.copy(index = i) } }
    fun next() = goTo(_state.value.index + 1)
    fun prev() = goTo(_state.value.index - 1)

    fun submitExam(auto: Boolean = false) {
        if (_state.value.submitted) return
        _state.update { it.copy(submitted = true) }
        timerJob?.cancel()
        viewModelScope.launch {
            val s = _state.value
            val settings = app.settingsRepo.settings.first()
            val answers = mutableListOf<ExamAnswerEntity>()
            var correctCount = 0
            s.questions.forEach { q ->
                val sel = s.answers[q.id]
                val fills = s.fillAnswers[q.id]
                val answered = when (q.type) {
                    QuestionType.FILL -> fills?.any { it.isNotBlank() } == true
                    else -> !sel.isNullOrEmpty()
                }
                val correct = answered && Grader.isCorrect(q, sel, fills)
                if (correct) correctCount++
                val userAnswer = when (q.type) {
                    QuestionType.FILL -> (fills ?: emptyList()).joinToString("；")
                    else -> (sel ?: emptySet()).sorted().joinToString("")
                }
                // 错题归档
                app.repository.recordAnswer(q, correct, userAnswer, "EXAM", settings.masteryThreshold)
                answers.add(
                    ExamAnswerEntity(
                        examId = 0,
                        questionId = q.id,
                        userAnswer = userAnswer,
                        correct = correct,
                        marked = q.id in s.marked,
                        labelMapJson = Jsons.json.encodeToString(q.labelMap)
                    )
                )
            }
            val total = s.questions.size
            val score = if (total == 0) 0.0 else Math.round(correctCount * 1000.0 / total) / 10.0
            val exam = ExamRecordEntity(
                startTime = System.currentTimeMillis() - (config.durationMin * 60 - s.remainingSec) * 1000L,
                endTime = System.currentTimeMillis(),
                durationLimitSec = config.durationMin * 60,
                totalCount = total,
                correctCount = correctCount,
                score = score,
                configJson = Jsons.json.encodeToString(config)
            )
            val examId = app.repository.db.examDao().insertExam(exam)
            app.repository.db.examDao().insertAnswers(answers.map { it.copy(examId = examId) })
            _state.update { it.copy(resultExamId = examId) }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
    }
}

class ExamViewModelFactory(private val app: NailongApp, private val config: ExamConfig) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ExamViewModel(app, config) as T
    }
}
