package com.nailong.quiz

import com.nailong.quiz.data.*
import com.nailong.quiz.domain.*
import org.junit.Assert.*
import org.junit.Test

class FillImportRegressionTest {
    private fun parsed(stem: String, answer: String) = PdfQuestionParser.withAnswer(
        QuestionJson(1, 310, "FILL", "填空题", stem), answer)
    @Test fun commaAnswersMatchVisibleBlanks() {
        for (separator in listOf(",", "，", "、", "；", "\n")) {
            val q = parsed("____、____、____是基础性、战略性支撑。", listOf("教育", "科技", "人才").joinToString(separator))
            assertTrue(PdfQuestionParser.issues(q).isEmpty())
            assertEquals(listOf("教育", "科技", "人才"), q.fillAnswers)
            val domain = q.toEntity().toDomain()
            assertTrue(Grader.isCorrect(domain, null, listOf("教育", "科技", "人才")))
            assertFalse(Grader.isCorrect(domain, null, listOf("科技", "教育", "人才")))
        }
    }
    @Test fun questionWithoutUnderlineAcceptsCompleteAnswer() {
        val q = parsed("中国共产党成立的时间是？", "1921年7月23日")
        assertTrue(PdfQuestionParser.issues(q).isEmpty())
        assertFalse(q.markForReview().conflict)
        assertTrue(Grader.isCorrect(q.toEntity().toDomain(), null, listOf("1921年7月23日")))
    }
    @Test fun punctuationInsideOneAnswerIsPreserved() {
        val q = parsed("请填写____", "教育,科技,人才")
        assertEquals(listOf("教育,科技,人才"), q.fillAnswers)
        assertTrue(PdfQuestionParser.issues(q).isEmpty())
    }
    @Test fun ambiguousMappingStillNeedsReviewAndEssayDoesNotNeedBlanks() {
        assertTrue(PdfQuestionParser.issues(parsed("____与____", "甲,乙,丙")).isNotEmpty())
        val essay = QuestionJson(1, 1, "ESSAY", "简答题", "请说明原因", answer = "第一点；第二点")
        assertTrue(PdfQuestionParser.issues(essay).isEmpty())
        assertFalse(essay.toEntity().toDomain().needsReview)
        assertTrue(essay.toEntity().toDomain().usesSelfAssessment)
    }
}
