package com.nailong.quiz.data

import com.nailong.quiz.domain.Option
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.domain.blankSlots
import java.text.Normalizer

data class PdfTextPage(val number: Int, val text: String, val usedOcr: Boolean = false, val locationLabel: String? = null)
data class PdfParseResult(val questions: List<QuestionJson>, val warnings: List<String>)

/** Conservative local parser: missing or ambiguous answers remain empty for review. */
object PdfQuestionParser {
    private val numberStart = Regex("^(?:第\\s*(\\d{1,5})\\s*题\\s*[:：、.]?|[（(]?(\\d{1,5})[）).、]\\s*)(.*)$")
    private val optionStart = Regex("(?:^|[\\s　])([A-H])\\s*[.、:：)）]\\s*")
    private val answerMarker = Regex("(?:【\\s*(?:参考|正确|标准)?答案\\s*】|(?:参考|正确|标准)?答案\\s*[:：])\\s*")
    private val explanation = Regex("(?:【(?:解析|说明)】|(?:答案)?解析\\s*[:：])")
    private val inlineAnswer = Regex("[（(]\\s*([A-H](?:[A-H\\s,、]*[A-H])?|正确|错误|对|错|√|×|✓|✗)\\s*[）)]")
    private val pageNumber = Regex("^(?:[-—]\\s*)?(?:第\\s*)?\\d{1,4}\\s*(?:页)?(?:\\s*[/共]\\s*\\d+\\s*页?)?(?:\\s*[-—])?$")
    private data class Block(val no: Int, val type: QuestionType?, val page: Int, val lines: MutableList<String>, val locationLabel: String?)
    private data class AnswerKey(val no: Int, val type: QuestionType?, val answer: String)

    fun parse(pages: List<PdfTextPage>, source: String): PdfParseResult {
        val normalized = pages.map { page -> page.copy(text = normalize(page.text)) }
        val edgeCounts = mutableMapOf<String, Int>()
        if (pages.size >= 3) normalized.forEach { page ->
            val lines = page.text.lines().map { it.trim() }.filter { it.isNotBlank() }
            (lines.take(1) + lines.takeLast(1)).distinct().forEach { edgeCounts[it] = (edgeCounts[it] ?: 0) + 1 }
        }
        val repeatedEdges = edgeCounts.filter { (text, count) -> count >= 3 &&
            numberStart.matchEntire(text) == null && sectionType(text) == null && !answerMarker.containsMatchIn(text)
        }.keys
        val blocks = mutableListOf<Block>()
        val keys = mutableListOf<AnswerKey>()
        var current: Block? = null
        var type: QuestionType? = null
        var keyMode = false
        var unusedKeyLines = 0
        fun finish() { current?.let { blocks.add(it) }; current = null }
        normalized.forEach { page ->
            page.text.lines().forEach lineLoop@ { raw ->
                val line = raw.trim()
                if (line.isBlank() || pageNumber.matches(line) || line in repeatedEdges) return@lineLoop
                if (isAnswerHeading(line)) {
                    finish()
                    keyMode = true
                    type = sectionType(line)
                    return@lineLoop
                }
                val section = sectionType(line)
                if (section != null && isSectionHeading(line)) {
                    finish()
                    type = section
                    return@lineLoop
                }
                if (keyMode) {
                    val found = parseKeyLine(line, type)
                    keys.addAll(found)
                    if (found.isEmpty()) unusedKeyLines++
                    return@lineLoop
                }
                val match = numberStart.matchEntire(line)
                if (match != null) {
                    finish()
                    val no = (match.groupValues[1].ifBlank { match.groupValues[2] }).toInt()
                    current = Block(no, type, page.number, mutableListOf(match.groupValues[3]), page.locationLabel)
                } else current?.lines?.add(line)
            }
        }
        finish()
        require(blocks.size <= 5000) { "识别到超过 5000 道题，请拆分文件后导入" }
        val preliminaryQuestions = blocks.mapIndexed { index, block -> parseBlock(block, index + 1, source) }
        val questions = blocks.mapIndexed { index, block ->
            val preliminary = preliminaryQuestions[index]
            val inferredChoice = block.type == null && preliminary.options.isNotEmpty() &&
                !block.lines.first().trimStart().startsWith("【") && !block.lines.first().trimStart().startsWith("[")
            val candidates = keys.filter { it.no == block.no && (it.type == null || it.type.name == preliminary.type ||
                (inferredChoice && it.type in setOf(QuestionType.SINGLE, QuestionType.MULTI))) }
            // A number-only key cannot safely address several sections that restart numbering.
            val uniqueNo = blocks.count { it.no == block.no } == 1
            val safeKeys = candidates.filter { key -> if (key.type == null || inferredChoice) uniqueNo else
                preliminaryQuestions.count { it.origNo == key.no && it.type == key.type.name } == 1
            }.map { it.answer }.distinct()
            if (safeKeys.size == 1) {
                val keyed = withAnswer(if (inferredChoice && letters(safeKeys.single()).length > 1)
                    preliminary.copy(type = "MULTI", typeZh = QuestionType.MULTI.zh) else preliminary, safeKeys.single())
                if (preliminary.answer.isBlank()) keyed
                else if (preliminary.answer != keyed.answer) preliminary.copy(conflict = true)
                else preliminary
            } else if (safeKeys.size > 1) preliminary.copy(conflict = true) else preliminary
        }
        val warnings = mutableListOf<String>()
        if (pages.any { it.usedOcr }) warnings += "有 ${pages.count { it.usedOcr }} 页使用图片文字识别，请特别核对题号、选项字母和答案。"
        if (questions.isEmpty()) warnings += "未识别到带数字题号的题目。支持“1. 题目”“1、题目”或“第1题”。" +
            if (pages.any { it.locationLabel == null }) "PDF 可尝试按图片重新识别。" else "请检查原文题号格式，或手动补充。"
        if (questions.any { issues(it).isNotEmpty() }) warnings += "部分题目待完善，可直接新建，之后在题库页编辑。"
        if (keys.any { key -> key.type == null && blocks.count { it.no == key.no } > 1 }) warnings += "不同题型使用了重复题号，未自动关联无题型标识的答案表。"
        if (keys.any { key -> key.type != null && preliminaryQuestions.count { it.origNo == key.no && it.type == key.type.name } > 1 }) warnings += "同一题型有重复题号，未自动关联对应答案表，请手动核对。"
        if (unusedKeyLines > 0) warnings += "答案区有 $unusedKeyLines 行未能关联，请对照 PDF 核对。"
        warnings += "请对照原文件核对总题数；复杂排版、公式、配图和多级编号可能无法完整识别。配图不会导入。"
        return PdfParseResult(questions, warnings)
    }

    private fun parseBlock(block: Block, id: Int, source: String): QuestionJson {
        var body = block.lines.joinToString("\n").trim()
        val typeInText = sectionType(body.lineSequence().firstOrNull().orEmpty())
            .takeIf { body.startsWith("【") || body.startsWith("[") }
        body = body.replace(Regex("^(?:【|\\[)(?:单项选择题?|单选题?|多项选择题?|多选题?|判断题?|填空题?|简答题?|问答题?)(?:】|])\\s*"), "")
        val answerAt = answerMarker.find(body)
        var rawAnswer = ""
        if (answerAt != null) {
            rawAnswer = body.substring(answerAt.range.last + 1).split(explanation, limit = 2).first().trim()
            body = body.substring(0, answerAt.range.first).trim()
        }
        val markers = optionStart.findAll(body).toList()
        val options = markers.mapIndexed { i, m ->
            val end = markers.getOrNull(i + 1)?.range?.first ?: body.length
            Option(m.groupValues[1], body.substring(m.range.last + 1, end).trim())
        }
        var stem = if (markers.isEmpty()) body else body.substring(0, markers.first().range.first).trim()
        if (rawAnswer.isBlank()) inlineAnswer.find(stem)?.let { match ->
            rawAnswer = match.groupValues[1]
            stem = stem.replaceRange(match.range, "( )")
        }
        val declared = typeInText ?: block.type
        val type = declared ?: when {
            options.isNotEmpty() -> if (letters(rawAnswer).length > 1) QuestionType.MULTI else QuestionType.SINGLE
            judge(rawAnswer).isNotBlank() -> QuestionType.JUDGE
            stem.blankSlots() > 0 || Regex("[（(]\\s*[）)]").containsMatchIn(stem) -> QuestionType.FILL
            else -> QuestionType.ESSAY
        }
        if (type == QuestionType.FILL) stem = stem.replace(Regex("[（(]\\s*[）)]"), "____")
        val question = QuestionJson(id, block.no, type.name, type.zh, stem, options,
            source = "$source · ${block.locationLabel ?: "第 ${block.page} 页"}")
        return withAnswer(question, rawAnswer)
    }

    fun withAnswer(question: QuestionJson, raw: String): QuestionJson {
        val text = normalize(raw).trim().removeSuffix("。").trim()
        val answer = when (QuestionType.from(question.type)) {
            QuestionType.SINGLE, QuestionType.MULTI -> letters(text)
            QuestionType.JUDGE -> judge(text)
            else -> text
        }
        val fills = if (question.type == QuestionType.FILL.name && text.isNotBlank()) {
            val count = question.stem.blankSlots()
            if (count <= 1) listOf(text.replace(Regex("\\s+"), " ")) else text.split(Regex("[;；\\n]+"))
                .map { it.trim() }.filter { it.isNotBlank() }
        } else emptyList()
        return question.copy(answer = answer, fillAnswers = fills)
    }

    fun issues(question: QuestionJson): List<String> = buildList {
        if (question.stem.isBlank()) add("缺少题干")
        if (question.conflict) add("题目或答案待完善，可先保存后核对")
        when (QuestionType.from(question.type)) {
            QuestionType.SINGLE, QuestionType.MULTI -> {
                val labels = question.options.map { it.label }
                if (labels.size !in 2..8 || labels.distinct().size != labels.size ||
                    labels != ('A'..'H').take(labels.size).map { it.toString() } || question.options.any { it.text.isBlank() }) add("选项应从 A 连续编号，至少两项，最多八项")
                if (question.answer.isBlank() || question.answer.any { it.toString() !in labels } ||
                    (question.type == "SINGLE" && question.answer.length != 1) ||
                    (question.type == "MULTI" && question.answer.length < 2)) add("缺少有效的选择题答案")
            }
            QuestionType.JUDGE -> if (question.answer !in setOf("对", "错")) add("判断题答案需为“对”或“错”")
            QuestionType.FILL -> {
                if (question.stem.blankSlots() == 0) add("请用 ____ 标出填空位置")
                if (question.fillAnswers.isEmpty() || question.fillAnswers.any { it.isBlank() } ||
                    question.fillAnswers.size != question.stem.blankSlots()) add("填空答案数量与空位不一致；每空答案占一行")
            }
            QuestionType.ESSAY -> if (question.answer.isBlank()) add("缺少参考答案")
        }
    }

    private fun parseKeyLine(line: String, type: QuestionType?): List<AnswerKey> {
        val range = Regex("^(\\d+)\\s*[-—~至]\\s*(\\d+)\\s*[:：.、]?\\s*([A-H\\s]+)$").matchEntire(line)
        if (range != null) {
            val start = range.groupValues[1].toInt()
            val end = range.groupValues[2].toInt()
            val answers = range.groupValues[3].filter { it in 'A'..'H' }
            if (end >= start && end - start + 1 == answers.length && answers.length <= 100) {
                return answers.mapIndexed { i, c -> AnswerKey(start + i, type, c.toString()) }
            }
        }
        val compact = Regex("(?:^|\\s)(\\d{1,5})\\s*[.、:：)\\-]?\\s*([A-H]+|正确|错误|对|错|√|×|✓|✗)(?=\\s|$)").findAll(line).toList()
        if (compact.isNotEmpty() && compact.joinToString("") { it.value }.filterNot { it.isWhitespace() } == line.filterNot { it.isWhitespace() }) {
            return compact.map { AnswerKey(it.groupValues[1].toInt(), type, it.groupValues[2]) }
        }
        val numbered = numberStart.matchEntire(line) ?: return emptyList()
        val no = (numbered.groupValues[1].ifBlank { numbered.groupValues[2] }).toInt()
        val answer = numbered.groupValues[3].replace(answerMarker, "").split(explanation, limit = 2).first().trim()
        return if (answer.isNotBlank()) listOf(AnswerKey(no, type, answer)) else emptyList()
    }

    private fun letters(text: String): String = if (Regex("[A-H\\s,，、;；]+").matches(text))
        text.filter { it in 'A'..'H' }.toSortedSet().joinToString("") else ""

    private fun judge(text: String): String = when (text.trim()) {
        "对", "正确", "√", "✓", "T", "TRUE", "True" -> "对"
        "错", "错误", "×", "✗", "F", "FALSE", "False" -> "错"
        else -> ""
    }

    private fun sectionType(text: String): QuestionType? = when {
        Regex("单[项选].*选?择?题|单选").containsMatchIn(text) -> QuestionType.SINGLE
        Regex("多[项选].*选?择?题|多选").containsMatchIn(text) -> QuestionType.MULTI
        text.contains("判断题") -> QuestionType.JUDGE
        text.contains("填空题") -> QuestionType.FILL
        text.contains("简答题") || text.contains("问答题") || text.contains("论述题") -> QuestionType.ESSAY
        else -> null
    }

    private fun isSectionHeading(text: String): Boolean = text.length < 65 &&
        Regex("^(?:[一二三四五六七八九十]+[、.\\s]|[（(]?[一二三四五六七八九十]+[）)]|[【\\[])?\\s*(?:单项选择|单选|多项选择|多选|判断|填空|简答|问答|论述)题").containsMatchIn(text)

    private fun isAnswerHeading(text: String): Boolean = text.length < 45 &&
        (Regex("^(?:[一二三四五六七八九十]+[、.\\s])?(?:参考答案|标准答案|正确答案|答案汇总|答案表|答案)(?:(?:及|与|和)?解析)?\\s*[:：]?$").matches(text) ||
            (isSectionHeading(text) && text.contains("答案")))

    private fun normalize(text: String): String = Normalizer.normalize(text.replace('\u00a0', ' '), Normalizer.Form.NFKC)
        .replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("[＿﹍﹎]{2,}"), "____")
}
