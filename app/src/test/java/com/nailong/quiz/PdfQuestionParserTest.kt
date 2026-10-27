package com.nailong.quiz

import com.nailong.quiz.data.PdfQuestionParser
import com.nailong.quiz.data.PdfTextPage
import org.junit.Assert.*
import org.junit.Test

class PdfQuestionParserTest {
    private fun parse(text: String) = PdfQuestionParser.parse(listOf(PdfTextPage(1, text.trimIndent())), "测试.pdf")

    @Test fun `inline options fullwidth punctuation and embedded answers`() {
        val result = parse("""
            一、单项选择题
            １．太阳从哪边升起？（Ａ）
            Ａ．东方 Ｂ．西方 Ｃ．南方 Ｄ．北方
            2、哪一种是水果？
            A. 苹果
            B. 石头
            【答案】A
            【解析】苹果是水果。
        """)
        assertEquals(2, result.questions.size)
        result.questions.forEach { assertTrue(PdfQuestionParser.issues(it).toString(), PdfQuestionParser.issues(it).isEmpty()) }
        assertEquals(listOf("A", "A"), result.questions.map { it.answer })
        assertFalse(result.questions.first().stem.contains("(A)"))
        assertFalse(result.questions.last().answer.contains("解析"))
    }

    @Test fun `back answer keys infer multiple selection without a heading`() {
        val q = parse("""
            1. 请选择符合要求的两项。
            A. 第一项 B. 第二项 C. 第三项
            参考答案
            1. AC
        """).questions.single()
        assertEquals("MULTI", q.type)
        assertEquals("AC", q.answer)
        assertTrue(PdfQuestionParser.issues(q).isEmpty())
    }

    @Test fun `typed answer sections bind restarted numbers correctly`() {
        val questions = parse("""
            一、单项选择题
            1. 选择一项。
            A. 甲 B. 乙
            二、多项选择题
            1. 选择多项。
            A. 甲 B. 乙 C. 丙
            参考答案
            一、单项选择题
            1. B
            二、多项选择题
            1. AC
        """).questions
        assertEquals(listOf("B", "AC"), questions.map { it.answer })
        assertTrue(questions.all { PdfQuestionParser.issues(it).isEmpty() })
    }

    @Test fun `ambiguous number only keys never guess a section`() {
        val result = parse("""
            一、单项选择题
            1. 选择一项。
            A. 甲 B. 乙
            二、多项选择题
            1. 选择多项。
            A. 甲 B. 乙 C. 丙
            参考答案
            1. B
        """)
        assertTrue(result.questions.all { it.answer.isBlank() })
        assertTrue(result.warnings.any { it.contains("重复题号") })
    }

    @Test fun `same type duplicate numbers cannot share a typed key`() {
        val result = parse("""
            一、单项选择题
            1. 第一章的问题。
            A. 甲 B. 乙
            二、单项选择题
            1. 第二章的问题。
            A. 丙 B. 丁
            单项选择题参考答案
            1. A
        """)
        assertEquals(2, result.questions.size)
        assertTrue(result.questions.all { it.answer.isBlank() })
    }

    @Test fun `missing answers and incomplete options block automatic import`() {
        val questions = parse("""
            1. 没有给出答案。
            A. 第一项 B. 第二项
            2. 少了一个选项。
            A. 第一项 C. 第三项
            答案：A
        """).questions
        assertEquals(2, questions.size)
        assertTrue(questions.all { PdfQuestionParser.issues(it).isNotEmpty() })
        assertEquals("", questions.first().answer)
    }

    @Test fun `judgment fill and essay have separate answer rules`() {
        val questions = parse("""
            一、判断题
            1. 地球是行星。（√）
            二、填空题
            1. 空一____，空二（ ）。
            答案：甲；乙
            三、简答题
            1. 请说明原因。
            参考答案：第一行原因。
            第二行原因。
        """).questions
        assertEquals(listOf("JUDGE", "FILL", "ESSAY"), questions.map { it.type })
        assertEquals("对", questions[0].answer)
        assertEquals(listOf("甲", "乙"), questions[1].fillAnswers)
        assertTrue(questions[2].answer.contains("第二行"))
        assertTrue(questions.all { PdfQuestionParser.issues(it).isEmpty() })
    }

    @Test fun `cross page question keeps its source and ignores page numbers`() {
        val result = PdfQuestionParser.parse(listOf(
            PdfTextPage(4, "1. 跨页的问题。\nA. 第一项\n第4页"),
            PdfTextPage(5, "B. 第二项\n答案：B", usedOcr = true)), "扫描件.pdf")
        val q = result.questions.single()
        assertEquals(2, q.options.size)
        assertEquals("B", q.answer)
        assertTrue(q.source.contains("第 4 页"))
        assertTrue(result.warnings.any { it.contains("图片文字识别") })
    }

    @Test fun `contradicting inline and back answers require manual correction`() {
        val q = parse("""
            1. 哪个答案正确？
            A. 甲 B. 乙
            答案：A
            参考答案
            1. B
        """).questions.single()
        assertTrue(q.conflict)
        assertTrue(PdfQuestionParser.issues(q).isNotEmpty())
    }

    @Test fun `range keys and compact keys work without interpreting prose as letters`() {
        val body = (1..5).joinToString("\n") { "$it. 测试问题$it。\nA. 甲 B. 乙 C. 丙" }
        val result = parse("$body\n参考答案\n1-3 ABC\n4.A 5.B")
        assertEquals(listOf("A", "B", "C", "A", "B"), result.questions.map { it.answer })
        val invalid = PdfQuestionParser.withAnswer(result.questions.first(), "According to the source, B")
        assertEquals("", invalid.answer)
    }

    @Test fun `fill answers must match the number of blanks`() {
        val q = parse("一、填空题\n1. 第一____，第二____。\n答案：只有一个答案").questions.single()
        assertTrue(PdfQuestionParser.issues(q).any { it.contains("空位不一致") })
    }

    @Test fun `no numbered questions produces a helpful empty preview`() {
        val result = parse("只有一些学习材料，并没有题号和标准答案。")
        assertTrue(result.questions.isEmpty())
        assertTrue(result.warnings.any { it.contains("未识别") })
    }
}
