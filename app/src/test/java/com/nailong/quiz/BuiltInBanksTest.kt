package com.nailong.quiz

import com.nailong.quiz.data.BuiltInBank
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BuiltInBanksTest {
    @Test
    fun `three banks have independent databases and complete assets`() {
        val allIds = mutableSetOf<Int>()
        val databases = mutableSetOf<String>()
        var missingAnswers = 0
        for (bank in BuiltInBank.entries) {
            assertTrue(databases.add(bank.databaseName))
            val data = JSONObject(File("src/main/assets/${bank.assetFile}").readText(Charsets.UTF_8))
            val questions = data.getJSONArray("questions")
            assertEquals(bank.count, data.getInt("count"))
            assertEquals(bank.count, questions.length())
            for (i in 0 until questions.length()) {
                val q = questions.getJSONObject(i)
                assertTrue("Duplicate question ID", allIds.add(q.getInt("id")))
                assertTrue(q.getString("stem").isNotBlank())
                val answer = q.getString("answer")
                when (q.getString("type")) {
                    "SINGLE", "MULTI" -> {
                        val opts = q.getJSONArray("options")
                        val labels = (0 until opts.length()).map { opts.getJSONObject(it).getString("label") }.toSet()
                        assertTrue(answer.isNotBlank())
                        assertTrue(answer.all { it.toString() in labels })
                        assertTrue(if (q.getString("type") == "SINGLE") answer.length == 1 else answer.length >= 2)
                    }
                    "JUDGE" -> assertTrue(answer in setOf("对", "错"))
                    "FILL" -> {
                        val fills = q.getJSONArray("fillAnswers")
                        assertTrue(fills.length() > 0)
                        if (bank != BuiltInBank.ORIGINAL) {
                            assertEquals(Regex("_+").findAll(q.getString("stem")).count(), fills.length())
                        }
                    }
                    "ESSAY" -> if (answer.isBlank()) {
                        assertTrue(q.getString("source").contains("原资料未提供答案"))
                        missingAnswers++
                    }
                    else -> fail("Unknown question type")
                }
            }
        }
        assertEquals(2123, allIds.size)
        assertEquals(4, missingAnswers)
    }

    @Test
    fun `default bank and unknown selection are deterministic`() {
        assertEquals(BuiltInBank.DANGZHANG, BuiltInBank.DEFAULT)
        assertEquals(BuiltInBank.DEFAULT, BuiltInBank.fromId(-1))
        for (bank in BuiltInBank.entries) assertEquals(bank, BuiltInBank.fromId(bank.id))
    }
}
