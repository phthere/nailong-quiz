package com.nailong.quiz.domain

import kotlin.random.Random

/**
 * 判题与选项打乱核心逻辑（纯函数，可单元测试）。
 * 保证：选项打乱后正确答案映射同步更新，多选集合判定不受顺序影响。
 */
object Grader {

    val LABELS = listOf("A", "B", "C", "D", "E", "F", "G", "H")

    /**
     * 打乱选项顺序。返回 (新选项列表, 展示label->原始label 映射)。
     * 原始 label 取自原选项的 label 字段；打乱后按展示位置重新标注 A/B/C...
     */
    fun shuffleOptions(options: List<Option>, random: Random = Random): Pair<List<Option>, Map<String, String>> {
        val shuffled = options.shuffled(random)
        val map = mutableMapOf<String, String>()
        val newOptions = shuffled.mapIndexed { idx, opt ->
            val displayLabel = LABELS[idx]
            map[displayLabel] = opt.label
            opt.copy(label = displayLabel)
        }
        return newOptions to map
    }

    /** 原始答案字母集合（如 "BCD" -> setOf("B","C","D")） */
    fun answerLetters(answer: String): Set<String> =
        answer.filter { it.isLetter() && it.isUpperCase() }.map { it.toString() }.toSet()

    /** 将用户选择的展示 label 集合翻译为原始 label 集合 */
    fun toOriginalSelection(displaySelection: Set<String>, labelMap: Map<String, String>): Set<String> =
        displaySelection.map { labelMap[it] ?: it }.toSet()

    /** 单选题判定：用户选择的展示 label -> 原始 label 与答案比较 */
    fun gradeSingle(displayLabel: String, question: Question): Boolean =
        question.originalLabel(displayLabel) == question.answer.trim()

    /** 多选题判定：集合完全一致，与顺序无关 */
    fun gradeMulti(displaySelection: Set<String>, question: Question): Boolean {
        val original = toOriginalSelection(displaySelection, question.labelMap)
        return original == answerLetters(question.answer)
    }

    /** 多选题正确答案对应的展示 label 集合（用于显示"正确答案"） */
    fun correctDisplayLabels(question: Question): Set<String> {
        val orig = answerLetters(question.answer)
        return question.options.filter { question.originalLabel(it.label) in orig }.map { it.label }.toSet()
    }

    /** 判断题判定：userAnswer 为 "对" 或 "错" */
    fun gradeJudge(userAnswer: String, question: Question): Boolean =
        userAnswer.trim() == question.answer.trim()

    /**
     * 填空题判定。
     * 规则：逐空比对；规范化处理：去首尾空格、全角转半角、去除内部所有空格。
     * 多空答案数量与空位数量一致时逐空比对；不一致时按题库原文整体比对（保守，不改判题标准）。
     */
    fun gradeFill(userInputs: List<String>, question: Question): Boolean {
        val standards = question.fillAnswers
        if (standards.isEmpty()) return false
        return if (standards.size == userInputs.size && standards.size > 1) {
            standards.zip(userInputs).all { (std, usr) -> normFill(std) == normFill(usr) }
        } else {
            // 单空或空数不一致：把用户所有输入合并后与合并标准答案比对
            val stdJoined = standards.joinToString("") { normFill(it) }
            val usrJoined = userInputs.joinToString("") { normFill(it) }
            stdJoined == usrJoined
        }
    }

    /** 逐空判定细节（用于 UI 标记每个空对错），空数不一致时返回空列表 */
    fun gradeFillPerBlank(userInputs: List<String>, question: Question): List<Boolean> {
        val standards = question.fillAnswers
        if (standards.size != userInputs.size || standards.size <= 1) return emptyList()
        return standards.zip(userInputs).map { (std, usr) -> normFill(std) == normFill(usr) }
    }

    fun normFill(s: String): String =
        s.trim()
            .map { c -> if (c in '！'..'～') c - 0xFEE0 else c }  // 全角->半角
            .joinToString("")
            .replace(" ", "")
            .replace("　", "")
            .lowercase()

    /** 统一判分入口。userAnswer: 单选/判断为单个展示label或"对/错"；多选为拼接的展示label串；填空为各空输入 */
    fun isCorrect(question: Question, userDisplaySelection: Set<String>?, fillInputs: List<String>?): Boolean =
        if (!question.canAutoGrade) false else when (question.type) {
            QuestionType.SINGLE -> userDisplaySelection?.singleOrNull()?.let { gradeSingle(it, question) } ?: false
            QuestionType.MULTI -> gradeMulti(userDisplaySelection ?: emptySet(), question)
            QuestionType.JUDGE -> userDisplaySelection?.singleOrNull()?.let { gradeJudge(it, question) } ?: false
            QuestionType.FILL -> gradeFill(fillInputs ?: emptyList(), question)
            QuestionType.ESSAY -> false // 简答题不自动判分，由用户自评
        }
}
