package com.nailong.quiz.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

internal data class WordNode(val name: String, val namespace: String, val attributes: Map<String, String>,
    val children: MutableList<WordNode> = mutableListOf(), var text: StringBuilder? = null) {
    val isWord: Boolean get() = namespace == "http://schemas.openxmlformats.org/wordprocessingml/2006/main" ||
        namespace == "http://purl.oclc.org/ooxml/wordprocessingml/main"
    fun child(name: String) = children.firstOrNull { it.isWord && it.name == name }
    fun all(name: String) = children.filter { it.isWord && it.name == name }
    fun value(name: String) = child(name)?.attributes?.get("val")
}

internal data class DocumentText(val text: String, val warnings: List<String>)

internal object DocxTextExtractor {
    fun extract(file: File, checkCancellation: () -> Unit = {}): DocumentText = ZipFile(file).use { zip ->
        require(zip.getEntry("word/document.xml") != null) { "文件不是有效的 Word .docx，请在 Word/WPS 中另存为 .docx 后重试" }
        val warnings = linkedSetOf<String>()
        val entries = zip.entries()
        var entryCount = 0
        var expandedBytes = 0L
        while (entries.hasMoreElements()) {
            checkCancellation()
            val entry = entries.nextElement()
            val size = entry.size.coerceAtLeast(0)
            require(++entryCount <= 10_000 && size <= 150L * 1024 * 1024 - expandedBytes) {
                "文档解压内容过大，请精简图片或拆分后导入"
            }
            expandedBytes += size
        }
        fun readPart(name: String): WordNode? {
            val entry = zip.getEntry(name) ?: return null
            require(entry.size <= 20L * 1024 * 1024) { "文档正文或样式过大，请拆分后导入" }
            val bytes = zip.getInputStream(entry).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    checkCancellation()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 20 * 1024 * 1024) { "文档正文或样式过大，请拆分后导入" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            // DOCX parts use UTF-8/UTF-16. Reject DTDs before parsing either encoding.
            require(!String(bytes, Charsets.ISO_8859_1).replace("\u0000", "").contains("<!DOCTYPE", ignoreCase = true)) { "文档包含不支持的 XML 声明" }
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(bytes), null)
            val stack = mutableListOf<WordNode>()
            var root: WordNode? = null
            var count = 0
            var tokens = 0
            var textCharacters = 0
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (++tokens % 512 == 0) checkCancellation()
                when (parser.eventType) {
                    XmlPullParser.DOCDECL -> error("文档包含不支持的 XML 声明")
                    XmlPullParser.START_TAG -> {
                        require(++count <= 250_000 && stack.size < 128) { "文档结构过大，请拆分后导入" }
                        val node = WordNode(parser.name, parser.namespace.orEmpty(), (0 until parser.attributeCount)
                            .associate { parser.getAttributeName(it) to parser.getAttributeValue(it) })
                        if (stack.isEmpty()) root = node else stack.last().children.add(node)
                        stack.add(node)
                    }
                    XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                        require(parser.text != null) { "文档包含无法解析的 XML 实体" }
                        stack.lastOrNull()?.let { node -> if (node.name in setOf("t", "delText", "instrText")) {
                            textCharacters += parser.text.length
                            require(textCharacters <= 2_000_000) { "文档文字超过 200 万字符，请拆分后导入" }
                            val text = node.text ?: StringBuilder().also { node.text = it }
                            text.append(parser.text)
                        } }
                    }
                }
                parser.nextToken()
            }
            return root
        }
        val document = checkNotNull(readPart("word/document.xml"))
        require(document.isWord && document.name == "document") { "不支持的 Word 正文格式" }
        val numbering = WordNumbering(readPart("word/numbering.xml"), readPart("word/styles.xml"), warnings)
        val body = document.child("body") ?: error("Word 文档缺少正文")
        val output = StringBuilder()
        fun append(text: String) {
            if (text.isBlank()) return
            require(output.length + text.length <= 2_000_000) { "文档文字超过 200 万字符，请拆分后导入" }
            output.append(text.trim()).append('\n')
        }
        fun runText(node: WordNode): String {
            if (node.isWord && node.name in setOf("del", "delText", "moveFrom")) {
                warnings += "文档含修订内容，已略过删除项并保留插入项，请按最终正文核对。"
                return ""
            }
            if ((node.isWord && node.name in setOf("drawing", "pict", "object", "txbxContent", "altChunk")) ||
                node.namespace.contains("/math")) {
                warnings += "文档中的图片、公式、文本框或嵌入对象未导入，请补充对应题目；图片型 Word 可另存 PDF 后扫描识别。"
                return ""
            }
            val hidden = node.child("rPr")?.child("vanish")
            if (node.isWord && node.name == "r" && hidden != null && hidden.attributes["val"] !in listOf("0", "false", "off")) return ""
            if (node.isWord && node.name == "t") return node.text?.toString().orEmpty()
            if (node.isWord && node.name in setOf("tab", "br", "cr")) return if (node.name == "tab") "\t" else "\n"
            return node.children.joinToString("") { runText(it) }
        }
        fun paragraph(node: WordNode): String {
            val text = runText(node).trim()
            val label = numbering.label(node)
            val alreadyNumbered = Regex("^(?:[（(]?\\d+[）).、]|第\\d+题|[A-H][.、)]|[一二三四五六七八九十]+[、.])").containsMatchIn(text)
            return if (label.isBlank() || alreadyNumbered || text.isBlank()) text else "$label $text"
        }
        fun cellText(node: WordNode): String = node.children.joinToString("\n") { child ->
            if (child.isWord && child.name == "p") paragraph(child) else runText(child)
        }.trim()
        fun table(node: WordNode) {
            val rows = node.all("tr").map { row -> row.all("tc").map { cellText(it) } }
            val header = rows.firstOrNull()?.map { it.replace(Regex("\\s+"), "").trim('：', ':') }.orEmpty()
            val stemIndex = header.indexOfFirst { it in setOf("题目", "题干", "试题", "问题") }
            val answerIndex = header.indexOfFirst { it in setOf("答案", "正确答案", "参考答案", "标准答案") }
            if (stemIndex >= 0 && answerIndex >= 0) {
                val numberIndex = header.indexOfFirst { it in setOf("题号", "序号", "编号") }
                val typeIndex = header.indexOf("题型")
                val optionColumns = header.mapIndexedNotNull { index, name ->
                    Regex("^(?:选项)?([A-H])(?:选项)?$").matchEntire(name)?.let { it.groupValues[1] to index }
                }
                rows.drop(1).forEachIndexed { index, row ->
                    if (row.all { it.isBlank() } || row.map { it.replace(Regex("\\s+"), "") } == header) return@forEachIndexed
                    if (row.size != header.size) {
                        warnings += "题库表格存在合并或缺失单元格，部分行按正文读取，请核对总题数和选项。"
                        append(row.joinToString("\n"))
                        return@forEachIndexed
                    }
                    val number = row.getOrNull(numberIndex)?.let { Regex("\\d{1,5}").find(it)?.value } ?: (index + 1).toString()
                    val type = row.getOrNull(typeIndex).orEmpty().let { value -> when {
                        value.contains("多") -> "多选题"
                        value.contains("单") -> "单选题"
                        value.contains("判") -> "判断题"
                        value.contains("填") -> "填空题"
                        value.contains("简") || value.contains("问") || value.contains("论述") -> "简答题"
                        else -> ""
                    } }
                    val stem = row[stemIndex].replace(Regex("^(?:[（(]?\\d+[）).、]|第\\d+题)\\s*"), "")
                    append("$number. ${if (type.isNotBlank()) "【$type】" else ""}$stem")
                    if (type !in setOf("判断题", "填空题", "简答题")) optionColumns.forEach { (label, column) ->
                        if (row[column].isNotBlank()) append("$label. ${row[column].replace(Regex("^$label[.、)]\\s*"), "")}")
                    }
                    if (row[answerIndex].isNotBlank()) append("答案：${row[answerIndex]}")
                }
            } else {
                rows.forEach { row ->
                    // Preserve a separate number cell followed by its question text.
                    if (row.size >= 2 && Regex("[（(]?\\d+[）).、]?").matches(row.first()) && row[1].isNotBlank()) {
                        append(row.first().filter { it.isDigit() } + ". " + row[1])
                        row.drop(2).forEach { append(it) }
                    } else row.forEach { append(it) }
                }
            }
        }
        fun block(node: WordNode) {
            checkCancellation()
            if (node.isWord && node.name == "p") append(paragraph(node))
            else if (node.isWord && node.name == "tbl") table(node)
            else if (node.isWord && node.name in setOf("sdt", "sdtContent", "customXml", "ins", "moveTo")) node.children.forEach { block(it) }
            else if (node.isWord && node.name == "altChunk") warnings += "文档含嵌入内容，未作为正文导入，请核对遗漏题目。"
        }
        body.children.forEach { block(it) }
        DocumentText(output.toString(), warnings.toList())
    }
}
