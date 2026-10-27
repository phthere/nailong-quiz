package com.nailong.quiz

import com.nailong.quiz.domain.Grader
import com.nailong.quiz.domain.Option
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.domain.blankSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GraderTest {

    private fun singleQ() = Question(
        id = 1, origNo = 1, type = QuestionType.SINGLE, typeZh = "单选题",
        stem = "测试题干",
        options = listOf(Option("A", "甲"), Option("B", "乙"), Option("C", "丙"), Option("D", "丁")),
        answer = "C", fillAnswers = emptyList()
    )

    private fun multiQ() = Question(
        id = 2, origNo = 2, type = QuestionType.MULTI, typeZh = "多选题",
        stem = "多选题干",
        options = listOf(Option("A", "甲"), Option("B", "乙"), Option("C", "丙"), Option("D", "丁"), Option("E", "戊")),
        answer = "BCD", fillAnswers = emptyList()
    )

    @Test
    fun `单选判分正确`() {
        val q = singleQ()
        assertTrue(Grader.gradeSingle("C", q))
        assertFalse(Grader.gradeSingle("A", q))
    }

    @Test
    fun `选项打乱后单选答案映射同步更新`() {
        val q = singleQ()
        repeat(50) {
            val (opts, map) = Grader.shuffleOptions(q.options, Random(it))
            val shuffled = q.copy(options = opts, labelMap = map)
            // 正确答案原 label 为 C，打乱后应映射到新的展示 label
            val newCorrectLabel = map.entries.first { e -> e.value == "C" }.key
            assertTrue(Grader.gradeSingle(newCorrectLabel, shuffled))
            // 其它 label 均为错误
            map.keys.filter { l -> l != newCorrectLabel }.forEach { l ->
                assertFalse("label $l 不应判对", Grader.gradeSingle(l, shuffled))
            }
            // correctDisplayLabels 应指向新 label
            assertEquals(setOf(newCorrectLabel), Grader.correctDisplayLabels(shuffled))
        }
    }

    @Test
    fun `多选集合判定不受顺序影响`() {
        val q = multiQ()
        assertTrue(Grader.gradeMulti(setOf("D", "B", "C"), q)) // 乱序选择仍正确
        assertFalse(Grader.gradeMulti(setOf("B", "C"), q))     // 少选
        assertFalse(Grader.gradeMulti(setOf("B", "C", "D", "A"), q)) // 多选
        assertFalse(Grader.gradeMulti(setOf("A", "B", "C"), q)) // 错选
    }

    @Test
    fun `选项打乱后多选答案映射正确`() {
        val q = multiQ()
        repeat(50) {
            val (opts, map) = Grader.shuffleOptions(q.options, Random(1000 + it))
            val shuffled = q.copy(options = opts, labelMap = map)
            val newCorrect = map.entries.filter { e -> e.value in setOf("B", "C", "D") }.map { e -> e.key }.toSet()
            assertTrue(Grader.gradeMulti(newCorrect, shuffled))
            assertEquals(3, newCorrect.size)
            // 打乱后选项数量不变、label 重排为 A..E
            assertEquals(5, opts.size)
            assertEquals(listOf("A", "B", "C", "D", "E"), opts.map { o -> o.label })
            // 移除一个正确选项 -> 判定错误
            if (newCorrect.size > 1) assertFalse(Grader.gradeMulti(newCorrect - newCorrect.first(), shuffled))
            // correctDisplayLabels 返回新 label 集合
            assertEquals(newCorrect, Grader.correctDisplayLabels(shuffled))
        }
    }

    @Test
    fun `判断题判定`() {
        val q = Question(3, 3, QuestionType.JUDGE, "判断题", "题干", emptyList(), "对", emptyList())
        assertTrue(Grader.gradeJudge("对", q))
        assertFalse(Grader.gradeJudge("错", q))
    }

    @Test
    fun `填空判定处理空格与全半角`() {
        val q = Question(4, 4, QuestionType.FILL, "填空题", "____是首要任务。", emptyList(), "高质量发展", listOf("高质量发展"))
        assertTrue(Grader.gradeFill(listOf(" 高质量发展 "), q))
        assertTrue(Grader.gradeFill(listOf("高质 量发展"), q))       // 内部空格容忍
        assertFalse(Grader.gradeFill(listOf("高速发展"), q))
        // 全角字母数字转半角
        val q2 = Question(5, 5, QuestionType.FILL, "填空题", "____。", emptyList(), "abc123", listOf("abc123"))
        assertTrue(Grader.gradeFill(listOf("ａｂｃ１２３"), q2))
    }

    @Test
    fun `多空填空逐空判定`() {
        val q = Question(6, 6, QuestionType.FILL, "填空题", "____、____是支撑。", emptyList(), "教育；科技", listOf("教育", "科技"))
        assertTrue(Grader.gradeFill(listOf("教育", "科技"), q))
        assertFalse(Grader.gradeFill(listOf("教育", "人才"), q))
        assertFalse(Grader.gradeFill(listOf("科技", "教育"), q)) // 顺序敏感
        assertEquals(listOf(true, false), Grader.gradeFillPerBlank(listOf("教育", "人才"), q))
    }

    @Test
    fun `空位数统计`() {
        assertEquals(1, "____是当代中国爱国主义的鲜明主题。".blankSlots())
        assertEquals(3, "____、____、____是支撑。".blankSlots())
        assertEquals(2, "以《____》为____。".blankSlots())
        assertEquals(0, "没有空位".blankSlots())
    }

    @Test
    fun `统一判分入口`() {
        val q = singleQ()
        assertTrue(Grader.isCorrect(q, setOf("C"), null))
        assertFalse(Grader.isCorrect(q, setOf("A"), null))
        assertFalse(Grader.isCorrect(q, null, null))
    }
}
