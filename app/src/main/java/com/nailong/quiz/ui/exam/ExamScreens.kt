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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    var durationText by remember { mutableStateOf(settings.examDurationMin.toString()) }
    var countText by remember { mutableStateOf(settings.examQuestionCount.toString()) }
    var shuffleOpts by remember { mutableStateOf(settings.shuffleOptions) }
    val types = setOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE, QuestionType.FILL)
    var custom by remember { mutableStateOf(false) }
    var amounts by remember { mutableStateOf(types.associateWith { "0" }) }
    var pool by remember { mutableStateOf<List<com.nailong.quiz.domain.Question>?>(null) }
    LaunchedEffect(app.repository.bank.id) { pool = app.repository.allQuestions().filter { it.canAutoGrade } }
    val config = ExamConfig(durationText.toIntOrNull() ?: 0, countText.toIntOrNull() ?: 0, shuffleOpts, types,
        if (custom) amounts.mapValues { it.value.toIntOrNull() ?: -1 } else emptyMap())
    val error = pool?.let { config.validationError(it) } ?: "正在加载可用题目…"

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
                        OutlinedTextField(durationText, { durationText = it.filter(Char::isDigit).take(4) },
                            label = { Text("考试时长（分钟）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(countText, { countText = it.filter(Char::isDigit).take(4) },
                            label = { Text("题目总数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("自定义各题型数量", Modifier.weight(1f))
                            Switch(custom, { custom = it })
                        }
                        if (custom) types.forEach { type ->
                            OutlinedTextField(amounts[type].orEmpty(), { value -> amounts = amounts + (type to value.filter(Char::isDigit).take(4)) },
                                label = { Text("${type.zh}（可用 ${pool?.count { it.type == type } ?: 0} 道）") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        Text(if (custom) "已配置 ${amounts.values.sumOf { it.toIntOrNull() ?: 0 }} / ${config.count} 道" else "从当前题库随机抽取指定总数。")
                        Text("简答题使用参考答案自评，暂不纳入自动评分考试。", style = MaterialTheme.typography.bodySmall)
                        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("随机打乱选项", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = shuffleOpts, onCheckedChange = { shuffleOpts = it })
                        }
                        Text("评分规则：每题等分，满分 100 分，交卷后自动判分。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = {
                        if (error != null) return@Button
                        PracticeBus.examConfig = config
                        nav.navigate(Routes.EXAM)
                    },
                    enabled = error == null,
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
