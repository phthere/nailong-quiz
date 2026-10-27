package com.nailong.quiz.ui.exam

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.nav.Routes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamSetupScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    var duration by remember { mutableIntStateOf(settings.examDurationMin) }
    var count by remember { mutableIntStateOf(settings.examQuestionCount) }
    var shuffleOpts by remember { mutableStateOf(settings.shuffleOptions) }
    var types by remember { mutableStateOf(setOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE, QuestionType.FILL)) }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text("模拟考试") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("考试时长", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 45, 60, 90).forEach { d ->
                                FilterChip(selected = duration == d, onClick = { duration = d }, label = { Text("$d 分钟") })
                            }
                        }
                        Text("题目数量", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(20, 30, 50, 100).forEach { c ->
                                FilterChip(selected = count == c, onClick = { count = c }, label = { Text("$c 题") })
                            }
                        }
                        Text("抽取题型（简答题不参与自动判分，不纳入考试）", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE, QuestionType.FILL).forEach { t ->
                                FilterChip(
                                    selected = t in types,
                                    onClick = { types = if (t in types) types - t else types + t },
                                    label = { Text(t.zh) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("随机打乱选项", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = shuffleOpts, onCheckedChange = { shuffleOpts = it })
                        }
                        Text("评分规则：每题等分，满分 100 分，交卷后自动判分。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = {
                        if (types.isEmpty()) return@Button
                        PracticeBus.examConfig = ExamConfig(duration, count, shuffleOpts, types)
                        nav.navigate(Routes.EXAM)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开始考试") }
                TextButton(onClick = { nav.navigate(Routes.EXAM_HISTORY) }, modifier = Modifier.fillMaxWidth()) { Text("查看历史成绩") }
            }
        }
    }
}

@Composable
fun ConfirmSubmitDialog(unanswered: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认交卷？") },
        text = { Text(if (unanswered > 0) "还有 $unanswered 道题未作答，确定要交卷吗？" else "确定要交卷吗？交卷后将自动判分。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("交卷") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续答题") } }
    )
}
