package com.nailong.quiz.domain

import kotlinx.serialization.Serializable

enum class QuestionType(val zh: String) {
    SINGLE("单选题"), MULTI("多选题"), JUDGE("判断题"), FILL("填空题"), ESSAY("简答题");

    companion object {
        fun from(s: String): QuestionType = entries.firstOrNull { it.name == s } ?: SINGLE
    }
}

@Serializable
data class Option(val label: String, val text: String)

/** 一道题的完整领域模型（options 为当前展示顺序，label 已按展示顺序重新标注） */
data class Question(
    val id: Int,
    val origNo: Int,
    val type: QuestionType,
    val typeZh: String,
    val stem: String,
    val options: List<Option>,          // 展示用选项（可能已打乱）
    val answer: String,                 // 原始正确答案：单选"A"/多选"BCD"/判断"对|错"/填空原文/简答原文
    val fillAnswers: List<String>,      // 填空各空标准答案
    val conflict: Boolean = false,
    val source: String = "",
    /** 展示 label -> 原始 label 的映射（未打乱时为恒等映射） */
    val labelMap: Map<String, String> = emptyMap()
) {
    /** Incomplete imported items stay in the bank and use explicit self assessment. */
    val needsReview: Boolean get() = conflict || stem.isBlank() || when (type) {
        QuestionType.SINGLE, QuestionType.MULTI -> {
            val labels = options.map { it.label }
            options.size !in 2..8 || labels.distinct().size != labels.size ||
                labels != ('A'..'H').take(labels.size).map { it.toString() } || options.any { it.text.isBlank() } ||
                answer.isBlank() || answer.any { it.toString() !in labels } ||
                (type == QuestionType.SINGLE && answer.length != 1) || (type == QuestionType.MULTI && answer.length < 2)
        }
        QuestionType.JUDGE -> answer !in setOf("对", "错")
        QuestionType.FILL -> fillAnswers.isEmpty() || fillAnswers.any { it.isBlank() }
        QuestionType.ESSAY -> answer.isBlank()
    }
    val usesSelfAssessment: Boolean get() = type == QuestionType.ESSAY || needsReview
    val canAutoGrade: Boolean get() = !usesSelfAssessment

    /** 判断题视为两个固定选项 */
    val effectiveOptions: List<Option>
        get() = when (type) {
            QuestionType.JUDGE -> listOf(Option("对", "正确"), Option("错", "错误"))
            else -> options
        }

    val blankCount: Int get() = stem.blankSlots()

    fun originalLabel(displayLabel: String): String = labelMap[displayLabel] ?: displayLabel
}

/** 数出独立下划线空位数（把连续 _ 视为一个空） */
fun String.blankSlots(): Int {
    var count = 0
    var inBlank = false
    for (c in this) {
        if (c == '_') {
            if (!inBlank) { count++; inBlank = true }
        } else inBlank = false
    }
    return count
}
