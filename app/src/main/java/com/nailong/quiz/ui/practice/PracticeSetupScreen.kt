package com.nailong.quiz.ui.practice

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.RandomScope
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.nav.Routes
import kotlinx.coroutines.launch

/** 练习配置（内存传递） */
data class PracticeConfig(
    val mode: PracticeMode,
    val title: String,
    val scope: RandomScope = RandomScope.ALL,
    val typeFilter: Set<QuestionType> = emptySet(),
    val count: Int = Int.MAX_VALUE,
    val shuffleOptions: Boolean = false,
    val questionIds: List<Int> = emptyList(),
    val resumeIndex: Int = 0
)

enum class PracticeMode { SEQUENTIAL, RANDOM, SPRINT, WRONG, FAVORITE, COLLECTION }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PracticeSetupScreen(app: NailongApp, settings: AppSettings, nav: NavHostController, mode: String) {
    val scope = rememberCoroutineScope()
    var randomScope by remember { mutableStateOf(RandomScope.ALL) }
    var countChoice by remember { mutableStateOf(20) }
    var typeFilter by remember { mutableStateOf(setOf<QuestionType>()) }
    var shuffleOpts by remember { mutableStateOf(settings.shuffleOptions) }
    var available by remember { mutableStateOf<Int?>(null) }
    val seqIndex by app.settingsRepo.seqIndex.collectAsState(initial = 0)

    val title = when (mode) {
        "sequential" -> "顺序刷题"
        "random" -> "随机刷题"
        "sprint" -> "奶龙考前冲刺"
        else -> "刷题"
    }

    fun refreshAvailable() {
        scope.launch {
            available = when (randomScope) {
                RandomScope.ALL -> app.repository.questionCount()
                RandomScope.UNPRACTICED -> app.repository.questionCount() - app.repository.practicedCount()
                RandomScope.WRONG -> app.repository.db.wrongDao().all().size
                RandomScope.FAVORITE -> app.repository.db.favoriteDao().ids().size
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(randomScope) { refreshAvailable() }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (mode) {
                    "sequential" -> {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("按题库原始顺序练习全部题目", style = MaterialTheme.typography.bodyMedium)
                                if (seqIndex > 0) {
                                    Text("上次练到第 ${seqIndex + 1} 题，可继续上次进度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("随机打乱选项", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = shuffleOpts, onCheckedChange = { shuffleOpts = it })
                                }
                            }
                        }
                        if (seqIndex > 0) {
                            Button(onClick = {
                                PracticeBus.config = PracticeConfig(PracticeMode.SEQUENTIAL, "顺序刷题", shuffleOptions = shuffleOpts, resumeIndex = seqIndex)
                                nav.navigate(Routes.PRACTICE)
                            }, modifier = Modifier.fillMaxWidth()) { Text("继续上次进度（第 ${seqIndex + 1} 题）") }
                        }
                        Button(onClick = {
                            PracticeBus.config = PracticeConfig(PracticeMode.SEQUENTIAL, "顺序刷题", shuffleOptions = shuffleOpts, resumeIndex = 0)
                            nav.navigate(Routes.PRACTICE)
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (seqIndex > 0) "从头开始" else "开始刷题") }
                    }

                    "random" -> {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("题目范围", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RandomScope.entries.forEach { s ->
                                        FilterChip(selected = randomScope == s, onClick = { randomScope = s }, label = { Text(s.zh) })
                                    }
                                }
                                available?.let {
                                    Text("当前范围可用题目：$it 题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("题目数量", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(10, 20, 50, 100, Int.MAX_VALUE).forEach { c ->
                                        FilterChip(selected = countChoice == c, onClick = { countChoice = c }, label = { Text(if (c == Int.MAX_VALUE) "全部" else "$c 题") })
                                    }
                                }
                                Text("题型筛选（不选为全部题型）", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QuestionType.entries.forEach { t ->
                                        FilterChip(
                                            selected = t in typeFilter,
                                            onClick = { typeFilter = if (t in typeFilter) typeFilter - t else typeFilter + t },
                                            label = { Text(t.zh) }
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("随机打乱选项", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = shuffleOpts, onCheckedChange = { shuffleOpts = it })
                                }
                            }
                        }
                        Button(onClick = {
                            val cnt = if (countChoice == Int.MAX_VALUE) Int.MAX_VALUE else countChoice
                            PracticeBus.config = PracticeConfig(PracticeMode.RANDOM, "随机刷题", scope = randomScope, typeFilter = typeFilter, count = cnt, shuffleOptions = shuffleOpts)
                            nav.navigate(Routes.PRACTICE)
                        }, modifier = Modifier.fillMaxWidth()) { Text("开始随机刷题") }
                    }

                    "sprint" -> {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("自动组合：未掌握错题 + 高频错题 + 未练习题目 + 收藏题目，优先抽取薄弱环节。", style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("随机打乱选项", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = shuffleOpts, onCheckedChange = { shuffleOpts = it })
                                }
                            }
                        }
                        listOf(20 to "20 题快速冲刺", 50 to "50 题强化冲刺", 100 to "100 题全面冲刺").forEach { (c, label) ->
                            Button(onClick = {
                                PracticeBus.config = PracticeConfig(PracticeMode.SPRINT, label, count = c, shuffleOptions = shuffleOpts)
                                nav.navigate(Routes.PRACTICE)
                            }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
