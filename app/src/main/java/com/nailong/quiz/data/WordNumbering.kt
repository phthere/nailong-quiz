package com.nailong.quiz.data

/** Restore visible list labels, which Word stores separately from paragraph text. */
internal class WordNumbering(root: WordNode?, stylesRoot: WordNode?, private val warnings: MutableSet<String>) {
    private data class Level(val format: String, val pattern: String, val start: Int, val restart: Int?)
    private data class Instance(val abstractId: String, val overrides: Map<Int, WordNode>)
    private data class Style(val parent: String?, val numberId: String?, val level: Int?)
    private val abstracts = root?.all("abstractNum")?.associate { node ->
        node.attributes["abstractNumId"].orEmpty() to node.all("lvl").associateBy { it.attributes["ilvl"]?.toIntOrNull() ?: 0 }
    }.orEmpty()
    private val instances = root?.all("num")?.associate { node ->
        node.attributes["numId"].orEmpty() to Instance(node.value("abstractNumId").orEmpty(),
            node.all("lvlOverride").associateBy { it.attributes["ilvl"]?.toIntOrNull() ?: 0 })
    }.orEmpty()
    private val styles = stylesRoot?.all("style")?.associate { node ->
        val num = node.child("pPr")?.child("numPr")
        node.attributes["styleId"].orEmpty() to Style(node.value("basedOn"), num?.value("numId"), num?.value("ilvl")?.toIntOrNull())
    }.orEmpty()
    private val counters = mutableMapOf<String, IntArray>()

    fun label(paragraph: WordNode): String {
        val properties = paragraph.child("pPr")
        val direct = properties?.child("numPr")
        var styleId = properties?.value("pStyle")
        var styleNumber: String? = null
        var styleLevel: Int? = null
        val visited = mutableSetOf<String>()
        while (styleId != null && visited.add(styleId) && visited.size <= 20) {
            val style = styles[styleId] ?: break
            if (styleNumber == null) styleNumber = style.numberId
            if (styleLevel == null) styleLevel = style.level
            styleId = style.parent
        }
        val id = direct?.value("numId") ?: styleNumber ?: return ""
        if (id == "0") return ""
        val index = direct?.value("ilvl")?.toIntOrNull() ?: styleLevel ?: 0
        if (index !in 0..8) { warnings += "文档含不支持的编号层级，请核对题号。"; return "" }
        val instance = instances[id]
        if (instance == null) { warnings += "部分自动编号定义缺失，请核对题号或改用明确数字题号的 TXT。"; return "" }
        fun level(at: Int): Level? {
            val override = instance.overrides[at]
            val base = override?.child("lvl") ?: abstracts[instance.abstractId]?.get(at) ?: return null
            return Level(base.value("numFmt") ?: "decimal", base.value("lvlText") ?: "%${at + 1}.",
                override?.value("startOverride")?.toIntOrNull() ?: base.value("start")?.toIntOrNull() ?: 1,
                base.value("lvlRestart")?.toIntOrNull())
        }
        val current = level(index) ?: run { warnings += "部分自动编号格式缺失，请核对题号。"; return "" }
        val values = counters.getOrPut(id) { IntArray(9) { Int.MIN_VALUE } }
        values[index] = if (values[index] == Int.MIN_VALUE) current.start else values[index] + 1
        for (deeper in index + 1..8) {
            val restart = level(deeper)?.restart ?: deeper
            if (restart != 0 && index <= restart - 1) values[deeper] = Int.MIN_VALUE
        }
        if (current.format == "bullet") { warnings += "项目符号不是数字题号，请核对题目是否遗漏。"; return current.pattern }
        if (Regex("%[1-9]").findAll(current.pattern).count() > 1) warnings += "文档含多级组合编号，请核对题号与答案的对应关系。"
        val rendered = Regex("%([1-9])").replace(current.pattern) { match ->
            val at = match.groupValues[1].toInt() - 1
            val definition = level(at)
            val value = values[at].takeUnless { it == Int.MIN_VALUE } ?: definition?.start ?: 1
            format(value, definition?.format ?: "decimal")
        }
        return if (Regex("\\d+").matches(rendered)) "$rendered." else rendered
    }

    private fun format(number: Int, kind: String): String = when (kind) {
        "decimal" -> number.toString()
        "decimalZero" -> number.toString().padStart(2, '0')
        "upperLetter", "lowerLetter" -> {
            var value = number.coerceAtLeast(1)
            var result = ""
            while (value > 0) { value--; result = ('A' + value % 26) + result; value /= 26 }
            result // Canonical option labels are uppercase in the quiz bank.
        }
        "upperRoman", "lowerRoman" -> {
            var value = number.coerceIn(1, 3999)
            val result = StringBuilder()
            listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I").forEach { (n, label) ->
                while (value >= n) { result.append(label); value -= n }
            }
            if (kind == "lowerRoman") result.toString().lowercase() else result.toString()
        }
        "chineseCounting", "chineseCountingThousand", "ideographTraditional" -> {
            if (number in 1..99) {
                val digits = "零一二三四五六七八九"
                if (number < 10) digits[number].toString() else (if (number / 10 == 1) "" else digits[number / 10].toString()) + "十" + if (number % 10 == 0) "" else digits[number % 10]
            } else number.toString()
        }
        else -> { warnings += "编号格式 $kind 使用数字替代，请核对原文题号。"; number.toString() }
    }
}
