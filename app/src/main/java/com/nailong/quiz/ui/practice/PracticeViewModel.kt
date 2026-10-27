package com.nailong.quiz.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.withShuffledOptions
import com.nailong.quiz.domain.Grader
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PracticeUiState(
    val loading: Boolean = true,
    val title: String = "",
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val selections: Map<Int, Set<String>> = emptyMap(),   // qid -> 展示label集合
    val fillInputs: Map<Int, List<String>> = emptyMap(),
    val submitted: Map<Int, Boolean> = emptyMap(),        // qid -> correct
    val favoriteIds: Set<Int> = emptySet(),
    val finished: Boolean = false,
    val newWrongIds: Set<Int> = emptySet(),               // 本次练习新增错题
    val masteredThreshold: Int = 2,
    val encouragement: Boolean = true,
    val immediate: Boolean = true,
    val error: String? = null
) {
    val current: Question? get() = questions.getOrNull(index)
    val answeredCount: Int get() = submitted.size
    val correctCount: Int get() = submitted.count { it.value }
}

class PracticeViewModel(private val app: NailongApp, private val config: PracticeConfig) : ViewModel() {

    private val _state = MutableStateFlow(PracticeUiState(title = config.title))
    val state = _state.asStateFlow()

    private var settings: AppSettings = AppSettings()
    private val bankId = app.repository.bank.id

    init {
        viewModelScope.launch {
            settings = app.settingsRepo.settings.first()
            app.repository.ensureImported()
            val repo = app.repository
            val raw: List<Question> = when (config.mode) {
                PracticeMode.SEQUENTIAL -> repo.allQuestions()
                PracticeMode.RANDOM -> repo.pickRandom(config.scope, config.typeFilter, config.count)
                PracticeMode.SPRINT -> repo.pickSprint(if (config.count == Int.MAX_VALUE) 50 else config.count)
                PracticeMode.WRONG -> {
                    val ids = repo.db.wrongDao().all().map { it.questionId }
                    repo.questionsByIds(ids)
                }
                PracticeMode.FAVORITE -> {
                    val ids = repo.db.favoriteDao().ids()
                    repo.questionsByIds(ids)
                }
                PracticeMode.COLLECTION -> repo.questionsByIds(config.questionIds)
            }
            val questions = raw.map { it.withShuffledOptions(config.shuffleOptions) }
            _state.update {
                it.copy(
                    loading = false,
                    questions = questions,
                    index = config.resumeIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0)),
                    masteredThreshold = settings.masteryThreshold,
                    encouragement = settings.encouragement,
                    immediate = settings.immediateFeedback,
                    error = if (questions.isEmpty()) "没有符合条件的题目，请调整范围或数量" else null
                )
            }
            app.repository.favoriteIdsFlow().collect { ids ->
                _state.update { s -> s.copy(favoriteIds = ids.toSet()) }
            }
        }
    }

    fun selectOption(label: String) {
        val q = _state.value.current ?: return
        if (q.id in _state.value.submitted) return
        when (q.type) {
            QuestionType.SINGLE, QuestionType.JUDGE -> {
                _state.update { s -> s.copy(selections = s.selections + (q.id to setOf(label))) }
                if (_state.value.immediate) submit()
            }
            QuestionType.MULTI -> {
                _state.update { s ->
                    val cur = s.selections[q.id] ?: emptySet()
                    val next = if (label in cur) cur - label else cur + label
                    s.copy(selections = s.selections + (q.id to next))
                }
            }
            else -> {}
        }
    }

    fun updateFill(blankIdx: Int, text: String) {
        val q = _state.value.current ?: return
        if (q.id in _state.value.submitted) return
        _state.update { s ->
            val cur = s.fillInputs[q.id] ?: List(q.blankCount.coerceAtLeast(1)) { "" }
            val next = cur.toMutableList().also { list -> if (blankIdx < list.size) list[blankIdx] = text }
            s.copy(fillInputs = s.fillInputs + (q.id to next))
        }
    }

    /** 提交当前题（多选/填空/非即时模式） */
    fun submit() {
        val s = _state.value
        val q = s.current ?: return
        if (q.id in s.submitted) return
        val selection = s.selections[q.id]
        val fills = s.fillInputs[q.id]
        if (q.type != QuestionType.ESSAY) {
            val hasAnswer = when (q.type) {
                QuestionType.FILL -> !fills.isNullOrEmpty() && fills.any { it.isNotBlank() }
                else -> !selection.isNullOrEmpty()
            }
            if (!hasAnswer) return
        }
        val correct = when (q.type) {
            QuestionType.ESSAY -> true // 自评时由 selfGrade 处理
            else -> Grader.isCorrect(q, selection, fills)
        }
        finalizeAnswer(q, correct)
    }

    /** 简答题自评 */
    fun selfGrade(known: Boolean) {
        val q = _state.value.current ?: return
        if (q.id in _state.value.submitted) return
        finalizeAnswer(q, known)
    }

    private fun finalizeAnswer(q: Question, correct: Boolean) {
        val s = _state.value
        val userAnswer = when (q.type) {
            QuestionType.FILL -> (s.fillInputs[q.id] ?: emptyList()).joinToString("；")
            QuestionType.ESSAY -> if (correct) "自评已掌握" else "自评未掌握"
            else -> (s.selections[q.id] ?: emptySet()).sorted().joinToString("")
        }
        _state.update {
            it.copy(
                submitted = it.submitted + (q.id to correct),
                newWrongIds = if (correct) it.newWrongIds else it.newWrongIds + q.id
            )
        }
        viewModelScope.launch {
            app.repository.recordAnswer(
                q, correct, userAnswer,
                sessionType = config.mode.name,
                masteryThreshold = settings.masteryThreshold
            )
        }
    }

    fun next() {
        val s = _state.value
        if (s.index >= s.questions.size - 1) {
            _state.update { it.copy(finished = true) }
        } else {
            _state.update { it.copy(index = it.index + 1) }
            persistSeqIndex()
        }
    }

    fun prev() {
        if (_state.value.index > 0) {
            _state.update { it.copy(index = it.index - 1) }
            persistSeqIndex()
        }
    }

    fun jumpTo(i: Int) {
        if (i in _state.value.questions.indices) {
            _state.update { it.copy(index = i) }
            persistSeqIndex()
        }
    }

    private fun persistSeqIndex() {
        if (config.mode == PracticeMode.SEQUENTIAL) {
            viewModelScope.launch { app.settingsRepo.saveSeqIndex(_state.value.index, bankId) }
        }
    }

    fun toggleFavorite() {
        val q = _state.value.current ?: return
        viewModelScope.launch { app.repository.toggleFavorite(q.id) }
    }

    /** 冲刺/练习总结建议（基于本次真实数据生成） */
    fun summaryAdvice(): String {
        val s = _state.value
        if (s.answeredCount == 0) return "本次还没有作答记录，先完成一些题目吧。"
        val acc = s.correctCount.toFloat() / s.answeredCount
        val wrongByType = s.questions.filter { it.id in s.newWrongIds }.groupingBy { it.typeZh }.eachCount()
        val sb = StringBuilder()
        sb.append(
            when {
                acc >= 0.9f -> "太棒了，掌握得很扎实！保持这个状态，考前再快速过一遍错题即可。"
                acc >= 0.7f -> "整体不错，但仍有提升空间。建议重点回顾本次答错的题目。"
                else -> "正确率偏低，建议放慢节奏，结合答案把每个知识点弄懂再继续。"
            }
        )
        if (wrongByType.isNotEmpty()) {
            val worst = wrongByType.maxByOrNull { it.value }!!
            sb.append("本次「${worst.key}」错得最多（${worst.value} 题），建议到错题集中针对性重练。")
        }
        if (s.newWrongIds.isNotEmpty()) {
            sb.append("本次新增 ${s.newWrongIds.size} 道错题，已自动加入错题集。")
        }
        return sb.toString()
    }
}

class PracticeViewModelFactory(private val app: NailongApp, private val config: PracticeConfig) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PracticeViewModel(app, config) as T
    }
}
