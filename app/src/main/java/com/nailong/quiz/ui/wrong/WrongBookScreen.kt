package com.nailong.quiz.ui.wrong

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.WrongQuestionEntity
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
import kotlinx.coroutines.launch

enum class WrongFilter(val zh: String) {
    ALL("全部"), SINGLE("单选"), MULTI("多选"), JUDGE("判断"), FILL("填空"), UNMASTERED("待巩固"), MASTERED("已掌握")
}

enum class WrongSort(val zh: String) { RECENT("最近错误"), COUNT("错误次数") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WrongBookScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    val wrongs by app.repository.wrongFlow().collectAsState(initial = emptyList())
    var questions by remember { mutableStateOf<Map<Int, Question>>(emptyMap()) }
    var filter by remember { mutableStateOf(WrongFilter.ALL) }
    var sort by remember { mutableStateOf(WrongSort.RECENT) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(wrongs) {
        questions = app.repository.questionsByIds(wrongs.map { it.questionId }).associateBy { it.id }
    }

    val filtered = wrongs.filter { w ->
        val q = questions[w.questionId] ?: return@filter true
        when (filter) {
            WrongFilter.ALL -> true
            WrongFilter.UNMASTERED -> !w.mastered
            WrongFilter.MASTERED -> w.mastered
            WrongFilter.SINGLE -> q.type == QuestionType.SINGLE
            WrongFilter.MULTI -> q.type == QuestionType.MULTI
            WrongFilter.JUDGE -> q.type == QuestionType.JUDGE
            WrongFilter.FILL -> q.type == QuestionType.FILL
        }
    }.let { list ->
        when (sort) {
            WrongSort.RECENT -> list.sortedByDescending { it.lastWrongTime }
            WrongSort.COUNT -> list.sortedByDescending { it.wrongCount }
        }
    }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("错题集", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("答错的题目会自动收录到这里，连续答对 ${settings.masteryThreshold} 次标记为已掌握", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            if (wrongs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "错题顺序重练", questionIds = filtered.map { it.questionId }, shuffleOptions = settings.shuffleOptions)
                        nav.navigate(Routes.PRACTICE)
                    }, enabled = filtered.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("顺序重练") }
                    OutlinedButton(onClick = {
                        PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "错题随机重练", questionIds = filtered.map { it.questionId }.shuffled(), shuffleOptions = settings.shuffleOptions)
                        nav.navigate(Routes.PRACTICE)
                    }, enabled = filtered.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("随机重练") }
                    OutlinedButton(onClick = {
                        val ids = filtered.filter { !it.mastered }.map { it.questionId }
                        if (ids.isNotEmpty()) {
                            PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "未掌握错题", questionIds = ids.shuffled(), shuffleOptions = settings.shuffleOptions)
                            nav.navigate(Routes.PRACTICE)
                        }
                    }, modifier = Modifier.weight(1f)) { Text("练未掌握") }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WrongFilter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.zh) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WrongSort.entries.forEach { s ->
                        FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.zh) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (wrongs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("太棒了，目前没有错题！") }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.questionId }) { w ->
                        val q = questions[w.questionId]
                        WrongCard(
                            w = w,
                            q = q,
                            onPractice = {
                                PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "单题重练", questionIds = listOf(w.questionId), shuffleOptions = settings.shuffleOptions)
                                nav.navigate(Routes.PRACTICE)
                            },
                            onToggleMaster = { scope.launch { app.repository.markMastered(w.questionId, !w.mastered) } },
                            onRemove = { scope.launch { app.repository.removeWrong(w.questionId) } }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WrongCard(w: WrongQuestionEntity, q: Question?, onPractice: () -> Unit, onToggleMaster: () -> Unit, onRemove: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onPractice) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(q?.typeZh ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("错 ${w.wrongCount} 次", style = MaterialTheme.typography.labelSmall, color = WrongRed)
                if (w.mastered) {
                    Icon(Icons.Filled.CheckCircle, "已掌握", tint = CorrectGreen, modifier = Modifier.height(16.dp))
                    Text("已掌握", style = MaterialTheme.typography.labelSmall, color = CorrectGreen)
                } else {
                    Text("待巩固", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleMaster) {
                    Icon(Icons.Filled.CheckCircle, "标记掌握", tint = if (w.mastered) CorrectGreen else MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, "移除", tint = MaterialTheme.colorScheme.outline)
                }
            }
            Text(q?.stem ?: "加载中…", style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            if (w.lastUserAnswer.isNotBlank()) {
                Text("上次你的答案：${w.lastUserAnswer}", style = MaterialTheme.typography.bodySmall, color = WrongRed)
            }
        }
    }
}
