package com.nailong.quiz.ui.wrong

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.*
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.nav.Routes
import com.nailong.quiz.ui.practice.PracticeConfig
import com.nailong.quiz.ui.practice.PracticeMode
import com.nailong.quiz.ui.theme.CorrectGreen
import com.nailong.quiz.ui.theme.WrongRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WrongBookScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    val repo = app.repository
    val wrongs by remember(repo) { repo.wrongFlow() }.collectAsState(emptyList())
    val questions by remember(repo) { repo.allQuestionsFlow() }.collectAsState(emptyList())
    val questionMap = remember(questions) { questions.associateBy { it.id } }
    var status by rememberSaveable { mutableStateOf("PENDING") }
    var type by rememberSaveable { mutableStateOf("ALL") }
    var sort by rememberSaveable { mutableStateOf("RECENT") }
    val scope = rememberCoroutineScope()
    val filtered = remember(wrongs, questions, status, type, sort) {
        wrongs.filter { wrong ->
            (status == "ALL" || (status == "PENDING" && !wrong.mastered) || (status == "MASTERED" && wrong.mastered)) &&
                (type == "ALL" || questionMap[wrong.questionId]?.type?.name == type)
        }.let { if (sort == "RECENT") it.sortedByDescending { w -> w.lastWrongTime } else it.sortedByDescending { w -> w.wrongCount } }
    }
    fun practice(shuffle: Boolean) {
        val ids = filtered.map { it.questionId }.let { if (shuffle) it.shuffled() else it }
        PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, if (shuffle) "错题随机重练" else "错题顺序重练",
            questionIds = ids, shuffleOptions = settings.shuffleOptions)
        nav.navigate(Routes.PRACTICE) { launchSingleTop = true }
    }
    AppBackground(settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.height(2.dp))
            PageHeader("错题集 · ${wrongs.size} 题", modifier = Modifier.testTag("wrong_page_header"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PENDING" to "待巩固", "MASTERED" to "已掌握", "ALL" to "全部错题").forEach { (value, label) ->
                    FilterChip(status == value, onClick = { status = value }, label = { Text(label) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("ALL" to "全部") + QuestionType.entries.map { it.name to it.zh.removeSuffix("题") }).forEach { (value, label) ->
                    FilterChip(type == value, onClick = { type = value }, label = { Text(label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sort == "RECENT", onClick = { sort = "RECENT" }, label = { Text("最近错误") })
                FilterChip(sort == "COUNT", onClick = { sort = "COUNT" }, label = { Text("错误次数") })
            }
            Text("连续答对 ${settings.masteryThreshold} 次标记掌握，再次答错会重新进入待巩固。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { practice(false) }, enabled = filtered.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("顺序重练") }
                OutlinedButton(onClick = { practice(true) }, enabled = filtered.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("随机重练") }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (filtered.isEmpty()) item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(if (wrongs.isEmpty()) "暂时没有错题" else "当前筛选下没有题目", style = MaterialTheme.typography.titleMedium)
                            Text("练习中需要巩固的题目会收录在这里", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(filtered, key = { it.questionId }) { wrong ->
                    val q = questionMap[wrong.questionId]
                    GlassCard(Modifier.fillMaxWidth(), onClick = {
                        PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "单题重练", questionIds = listOf(wrong.questionId), shuffleOptions = settings.shuffleOptions)
                        nav.navigate(Routes.PRACTICE) { launchSingleTop = true }
                    }) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("原题 ${q?.origNo ?: "—"} · ${q?.typeZh?.removeSuffix("题").orEmpty()}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (wrong.mastered) "已掌握" else "待巩固", style = MaterialTheme.typography.labelMedium,
                                    color = if (wrong.mastered) CorrectGreen else WrongRed)
                            }
                            Text(q?.stem?.ifBlank { "题干待补充" } ?: "加载中…", style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            val time = remember(wrong.lastWrongTime) { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(wrong.lastWrongTime)) }
                            Text("错误 ${wrong.wrongCount} 次 · 连续答对 ${wrong.consecutiveCorrect} 次 · $time", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (wrong.lastUserAnswer.isNotBlank()) Text("上次答案：${wrong.lastUserAnswer}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { scope.launch { repo.markMastered(wrong.questionId, !wrong.mastered) } }) {
                                    Text(if (wrong.mastered) "标为待巩固" else "标记已掌握")
                                }
                                TextButton(onClick = { scope.launch { repo.removeWrong(wrong.questionId) } }) { Text("移除", color = WrongRed) }
                            }
                        }
                    }
                }
            }
        }
    }
}
