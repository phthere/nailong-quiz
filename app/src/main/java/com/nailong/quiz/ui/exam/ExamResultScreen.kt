package com.nailong.quiz.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.ExamAnswerEntity
import com.nailong.quiz.data.ExamRecordEntity
import com.nailong.quiz.domain.Grader
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.nav.Routes
import com.nailong.quiz.ui.practice.PracticeConfig
import com.nailong.quiz.ui.practice.PracticeMode
import com.nailong.quiz.ui.theme.CorrectGreen
import com.nailong.quiz.ui.theme.WrongRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultScreen(app: NailongApp, settings: AppSettings, nav: NavHostController, examId: Long) {
    var exam by remember { mutableStateOf<ExamRecordEntity?>(null) }
    var answers by remember { mutableStateOf<List<ExamAnswerEntity>>(emptyList()) }
    var questions by remember { mutableStateOf<Map<Int, Question>>(emptyMap()) }
    var showReview by remember { mutableStateOf(false) }

    LaunchedEffect(examId) {
        val list = app.repository.db.examDao().exams()
        exam = list.firstOrNull { it.id == examId }
        answers = app.repository.db.examDao().answers(examId)
        questions = app.repository.questionsByIds(answers.map { it.questionId }).associateBy { it.id }
    }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text("考试结果") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            val e = exam
            if (e == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val unanswered = answers.count { it.userAnswer.isBlank() }
                val wrongIds = answers.filter { !it.correct }.map { it.questionId }
                val usedSec = ((e.endTime - e.startTime) / 1000).toInt()
                // 各题型正确率
                val byType = answers.groupBy { questions[it.questionId]?.typeZh ?: "其他" }
                    .mapValues { (_, v) -> v.count { it.correct } to v.size }

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${e.score}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = if (e.score >= 60) CorrectGreen else WrongRed)
                            Text("考试得分（满分 100）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                ResultStat("正确率", "${(e.correctCount * 100 / e.totalCount.coerceAtLeast(1))}%")
                                ResultStat("答对", "${e.correctCount}")
                                ResultStat("答错", "${e.totalCount - e.correctCount - unanswered}")
                                ResultStat("未答", "$unanswered")
                            }
                            Text("用时 ${usedSec / 60} 分 ${usedSec % 60} 秒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("各题型正确率", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            byType.forEach { (t, stat) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(t, style = MaterialTheme.typography.bodyMedium)
                                    Text("${stat.first}/${stat.second}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Button(onClick = { showReview = !showReview }, modifier = Modifier.fillMaxWidth()) { Text(if (showReview) "收起试卷" else "查看试卷与答案") }
                    if (wrongIds.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "本次考试错题", questionIds = wrongIds, shuffleOptions = settings.shuffleOptions)
                            nav.navigate(Routes.PRACTICE)
                        }, modifier = Modifier.fillMaxWidth()) { Text("重练本次错题（${wrongIds.size}）") }
                    }
                    OutlinedButton(onClick = { nav.navigate(Routes.EXAM_SETUP) { popUpTo(Routes.HOME) } }, modifier = Modifier.fillMaxWidth()) { Text("再考一次") }

                    if (showReview) {
                        answers.forEachIndexed { i, a ->
                            val q = questions[a.questionId] ?: return@forEachIndexed
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${i + 1}. ${q.typeZh}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Text(if (a.correct) "✓ 正确" else if (a.userAnswer.isBlank()) "○ 未答" else "✗ 错误", style = MaterialTheme.typography.labelMedium, color = if (a.correct) CorrectGreen else WrongRed)
                                    }
                                    Text(q.stem, style = MaterialTheme.typography.bodyMedium)
                                    if (q.type == QuestionType.SINGLE || q.type == QuestionType.MULTI) {
                                        // 恢复当时的选项展示顺序（labelMap: 展示label -> 原始label）
                                        val labelMap: Map<String, String> = runCatching {
                                            com.nailong.quiz.data.Jsons.json.decodeFromString<Map<String, String>>(a.labelMapJson)
                                        }.getOrDefault(emptyMap())
                                        val displayOptions = if (labelMap.isEmpty()) {
                                            q.options.map { it.label to it.text }
                                        } else {
                                            labelMap.entries.sortedBy { it.key }.map { (dl, ol) ->
                                                dl to (q.options.firstOrNull { it.label == ol }?.text ?: "")
                                            }
                                        }
                                        displayOptions.forEach { (dl, text) ->
                                            val origLabel = labelMap[dl] ?: dl
                                            val isAns = origLabel in Grader.answerLetters(q.answer)
                                            Text(
                                                "$dl. $text",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isAns) CorrectGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isAns) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                    Text("你的答案：${a.userAnswer.ifBlank { "（未作答）" }}", style = MaterialTheme.typography.bodySmall, color = if (a.correct) CorrectGreen else WrongRed)
                                    val std = when (q.type) {
                                        QuestionType.FILL -> q.fillAnswers.joinToString("；")
                                        QuestionType.ESSAY -> q.answer
                                        else -> q.answer
                                    }
                                    Text("正确答案：$std", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamHistoryScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    val exams by app.repository.db.examDao().examsFlow().collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text("历史成绩") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            if (exams.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有考试记录，去考一场吧！") }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    exams.forEach { e ->
                        GlassCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.examResult(e.id)) }) {
                            Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(fmt.format(Date(e.startTime)), style = MaterialTheme.typography.bodyMedium)
                                    Text("${e.totalCount} 题 · 答对 ${e.correctCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${e.score} 分", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (e.score >= 60) CorrectGreen else WrongRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
