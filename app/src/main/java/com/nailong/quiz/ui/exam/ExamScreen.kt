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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.nav.Routes
import com.nailong.quiz.ui.theme.QuestionStemStyle
import com.nailong.quiz.ui.theme.WrongRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    val config = PracticeBus.examConfig
    if (config == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("考试配置丢失，请返回重试") }
        return
    }
    val vm: ExamViewModel = viewModel(factory = ExamViewModelFactory(app, config), key = "exam")
    val st by vm.state.collectAsState()
    var showCard by remember { mutableStateOf(false) }
    var showSubmit by remember { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }

    // 交卷完成 -> 跳转结果页
    androidx.compose.runtime.LaunchedEffect(st.resultExamId) {
        if (st.resultExamId > 0) {
            nav.navigate(Routes.examResult(st.resultExamId)) { popUpTo(Routes.HOME) }
        }
    }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    val m = st.remainingSec / 60
                    val s = st.remainingSec % 60
                    Text("剩余 %02d:%02d".format(m, s), color = if (st.remainingSec < 300) WrongRed else MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = { IconButton(onClick = { showExit = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出") } },
                actions = {
                    TextButton(onClick = { showCard = true }) { Text("答题卡") }
                    TextButton(onClick = { showSubmit = true }) { Text("交卷", color = WrongRed) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            when {
                st.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("试卷生成中…") }
                st.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(st.error!!) }
                else -> {
                    val q = st.current!!
                    LinearProgressIndicator(
                        progress = { (st.index + 1).toFloat() / st.questions.size },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("第 ${st.index + 1} / ${st.questions.size} 题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.toggleMark() }) {
                            Text(if (q.id in st.marked) "★ 已标记" else "☆ 标记", color = if (q.id in st.marked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(q.typeZh, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Text(q.stem, style = QuestionStemStyle)
                            }
                        }
                        when (q.type) {
                            QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE -> {
                                val selection = st.answers[q.id] ?: emptySet()
                                q.effectiveOptions.forEach { opt ->
                                    val selected = opt.label in selection
                                    Card(
                                        onClick = { vm.select(opt.label) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                    ) {
                                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(opt.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.padding(6.dp))
                                            Text(if (q.type == QuestionType.JUDGE) (if (opt.label == "对") "正确" else "错误") else opt.text, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                            QuestionType.FILL -> {
                                val inputs = st.fillAnswers[q.id] ?: List(q.blankCount.coerceAtLeast(1)) { "" }
                                repeat(q.blankCount.coerceAtLeast(1)) { i ->
                                    OutlinedTextField(
                                        value = inputs.getOrElse(i) { "" },
                                        onValueChange = { vm.updateFill(i, it) },
                                        label = { Text("第 ${i + 1} 空") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { vm.prev() }, enabled = st.index > 0, modifier = Modifier.weight(1f)) { Text("上一题") }
                        Button(onClick = { vm.next() }, enabled = st.index < st.questions.size - 1, modifier = Modifier.weight(1f)) { Text("下一题") }
                    }
                }
            }
        }
    }

    if (showCard) {
        AlertDialog(
            onDismissRequest = { showCard = false },
            title = { Text("答题卡（已答 ${st.answeredCount}/${st.questions.size}）") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(st.questions.size) { i ->
                        val q = st.questions[i]
                        val answered = st.answers.containsKey(q.id) || st.fillAnswers[q.id]?.any { it.isNotBlank() } == true
                        val marked = q.id in st.marked
                        Surface(
                            onClick = { vm.goTo(i); showCard = false },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = when {
                                i == st.index -> MaterialTheme.colorScheme.primary
                                marked -> MaterialTheme.colorScheme.secondaryContainer
                                answered -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCard = false }) { Text("关闭") } }
        )
    }

    if (showSubmit) {
        ConfirmSubmitDialog(
            unanswered = st.questions.size - st.answeredCount,
            onConfirm = { showSubmit = false; vm.submitExam() },
            onDismiss = { showSubmit = false }
        )
    }
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("退出考试？") },
            text = { Text("退出后本次考试不会保存成绩。") },
            confirmButton = { TextButton(onClick = { showExit = false; nav.popBackStack() }) { Text("退出") } },
            dismissButton = { TextButton(onClick = { showExit = false }) { Text("继续考试") } }
        )
    }
}
