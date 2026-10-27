package com.nailong.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 直接校验打包进 APK 的真实题库数据完整性 */
class QuestionBankIntegrityTest {

    private val bank: JSONObject by lazy {
        val f = File("src/main/assets/questions.json")
        assertTrue("题库文件必须存在: ${f.absolutePath}", f.exists())
        JSONObject(f.readText(Charsets.UTF_8))
    }

    private val questions: JSONArray by lazy { bank.getJSONArray("questions") }

    @Test
    fun `题目总数正确`() {
        assertEquals(1038, bank.getInt("count"))
        assertEquals(1038, questions.length())
    }

    @Test
    fun `题型分布正确`() {
        val counts = mutableMapOf<String, Int>()
        for (i in 0 until questions.length()) {
            val t = questions.getJSONObject(i).getString("type")
            counts[t] = (counts[t] ?: 0) + 1
        }
        assertEquals(390, counts["SINGLE"])
        assertEquals(213, counts["MULTI"])
        assertEquals(270, counts["JUDGE"])
        assertEquals(136, counts["FILL"])
        assertEquals(29, counts["ESSAY"])
    }

    @Test
    fun `每道题答案非空且选择题答案有对应选项`() {
        for (i in 0 until questions.length()) {
            val q = questions.getJSONObject(i)
            val no = q.getInt("origNo")
            val ans = q.getString("answer")
            assertTrue("题 $no 答案不能为空", ans.isNotBlank())
            when (q.getString("type")) {
                "SINGLE", "MULTI" -> {
                    val labels = mutableSetOf<String>()
                    val opts = q.getJSONArray("options")
                    for (j in 0 until opts.length()) labels.add(opts.getJSONObject(j).getString("label"))
                    ans.filter { it.isUpperCase() }.forEach { c ->
                        assertTrue("题 $no 答案 $c 无对应选项", c.toString() in labels)
                    }
                }
                "JUDGE" -> assertTrue("题 $no 判断答案异常: $ans", ans in setOf("对", "错"))
                "FILL" -> assertTrue("题 $no 填空答案列表为空", q.getJSONArray("fillAnswers").length() > 0)
            }
        }
    }

    @Test
    fun `题目ID唯一且稳定`() {
        val ids = mutableSetOf<Int>()
        for (i in 0 until questions.length()) {
            val id = questions.getJSONObject(i).getInt("id")
            assertTrue("ID $id 重复", ids.add(id))
        }
    }
}
