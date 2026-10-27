package com.nailong.quiz.ui.practice

import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.domain.Grader
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.theme.CorrectGreen
import com.nailong.quiz.ui.theme.QuestionStemStyle
import com.nailong.quiz.ui.theme.WrongRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    val config = PracticeBus.config
    if (config == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("练习配置丢失，请返回重试") }
        return
    }
    val vm: PracticeViewModel = viewModel(factory = PracticeViewModelFactory(app, config), key = config.title + config.mode)
    val st by vm.state.collectAsState()
    var showJump by remember { mutableStateOf(false) }
    var essayRevealed by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    // 答题反馈（震动/音效）
    fun feedback(correct: Boolean) {
        if (settings.vibrate) {
            val v = ctx.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createOneShot(if (correct) 40 else 120, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v?.vibrate(if (correct) 40 else 120)
        }
        if (settings.sound) {
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
                tg.startTone(if (correct) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 180)
            }
        }
    }
    val prevSubmittedSize = remember { intArrayOf(0) }
    androidx.compose.runtime.LaunchedEffect(st.submitted.size) {
        if (st.submitted.size > prevSubmittedSize[0]) {
            val last = st.submitted.entries.lastOrNull()
            if (last != null) feedback(last.value)
        }
        prevSubmittedSize[0] = st.submitted.size
    }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(st.title, style = MaterialTheme.typography.titleMedium)
                        if (st.questions.isNotEmpty()) Text("第 ${st.index + 1} / ${st.questions.size} 题", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    st.current?.let { q ->
                        IconButton(onClick = { vm.toggleFavorite() }) {
                            Icon(
                                if (q.id in st.favoriteIds) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "收藏",
                                tint = if (q.id in st.favoriteIds) WrongRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            when {
                st.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("题目加载中…") }
                st.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(st.error!!)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { nav.popBackStack() }) { Text("返回") }
                    }
                }
                st.finished -> PracticeSummary(vm, st, settings, nav)
                else -> {
                    val q = st.current!!
                    LinearProgressIndicator(
                        progress = { (st.index + 1).toFloat() / st.questions.size },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 题干卡
                        GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(q.typeZh, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    Text("原题号 #${q.origNo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (q.conflict) Text("答案存疑", style = MaterialTheme.typography.labelSmall, color = WrongRed)
                                }
                                Text(q.stem, style = QuestionStemStyle)
                            }
                        }

                        val submitted = st.submitted.containsKey(q.id)
                        val isCorrect = st.submitted[q.id] == true

                        when (q.type) {
                            QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE -> {
                                val selection = st.selections[q.id] ?: emptySet()
                                val correctLabels = Grader.correctDisplayLabels(q).let { if (q.type == QuestionType.JUDGE) setOf(q.answer) else it }
                                q.effectiveOptions.forEach { opt ->
                                    OptionCard(
                                        label = opt.label,
                                        text = if (q.type == QuestionType.JUDGE) (if (opt.label == "对") "正确" else "错误") else opt.text,
                                        selected = opt.label in selection,
                                        submitted = submitted,
                                        isCorrectOption = opt.label in correctLabels,
                                        isMulti = q.type == QuestionType.MULTI,
                                        onClick = { vm.selectOption(opt.label) }
                                    )
                                }
                                if (q.type == QuestionType.MULTI && !submitted) {
                                    Button(onClick = { vm.submit() }, enabled = selection.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("确认答案") }
                                }
                                if (q.type != QuestionType.MULTI && !st.immediate && !submitted) {
                                    Button(onClick = { vm.submit() }, enabled = selection.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("确认答案") }
                                }
                            }

                            QuestionType.FILL -> {
                                val inputs = st.fillInputs[q.id] ?: List(q.blankCount.coerceAtLeast(1)) { "" }
                                val perBlank = if (submitted) Grader.gradeFillPerBlank(inputs, q) else emptyList()
                                repeat(q.blankCount.coerceAtLeast(1)) { i ->
                                    OutlinedTextField(
                                        value = inputs.getOrElse(i) { "" },
                                        onValueChange = { vm.updateFill(i, it) },
                                        label = { Text("第 ${i + 1} 空") },
                                        enabled = !submitted,
                                        isError = submitted && perBlank.getOrNull(i) == false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (!submitted) {
                                    Button(onClick = { vm.submit() }, enabled = inputs.any { it.isNotBlank() }, modifier = Modifier.fillMaxWidth()) { Text("确认答案") }
                                }
                            }

                            QuestionType.ESSAY -> {
                                if (!essayRevealed && !submitted) {
                                    Button(onClick = { essayRevealed = true }, modifier = Modifier.fillMaxWidth()) { Text("查看参考答案") }
                                }
                                if ((essayRevealed || submitted)) {
                                    GlassCard(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("参考答案", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(q.answer.ifBlank { "原资料未提供参考答案，请结合教材复习并自行评估。" }, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (!submitted) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(onClick = { vm.selfGrade(true) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen)) { Text("我答对了") }
                                            OutlinedButton(onClick = { vm.selfGrade(false) }, modifier = Modifier.weight(1f)) { Text("还需巩固") }
                                        }
                                    }
                                }
                            }
                        }

                        // 反馈区
                        if (submitted && q.type != QuestionType.ESSAY) {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        if (isCorrect) "奶龙夸夸你！答对啦！" else "没关系，再记住这个知识点！",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isCorrect) CorrectGreen else WrongRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val ansText = when (q.type) {
                                        QuestionType.FILL -> q.fillAnswers.joinToString("；")
                                        QuestionType.JUDGE -> q.answer
                                        else -> Grader.correctDisplayLabels(q).sorted().joinToString("")
                                    }
                                    Text("正确答案：$ansText", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // 底部导航栏
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = { vm.prev() }, enabled = st.index > 0, modifier = Modifier.weight(1f)) { Text("上一题") }
                        if (config.mode == PracticeMode.SEQUENTIAL) {
                            OutlinedButton(onClick = { showJump = true }, modifier = Modifier.weight(1f)) { Text("跳转") }
                        }
                        Button(onClick = { essayRevealed = false; vm.next() }, modifier = Modifier.weight(1f)) {
                            Text(if (st.index >= st.questions.size - 1) "完成" else "下一题")
                        }
                    }
                }
            }
        }
    }

    if (showJump) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("跳转到题号") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    label = { Text("1 - ${st.questions.size}") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    input.toIntOrNull()?.let { vm.jumpTo(it - 1) }
                    showJump = false
                }) { Text("跳转") }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun OptionCard(
    label: String,
    text: String,
    selected: Boolean,
    submitted: Boolean,
    isCorrectOption: Boolean,
    isMulti: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        submitted && isCorrectOption -> CorrectGreen
        submitted && selected && !isCorrectOption -> WrongRed
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    val bg = when {
        submitted && isCorrectOption -> CorrectGreen.copy(alpha = 0.12f)
        submitted && selected && !isCorrectOption -> WrongRed.copy(alpha = 0.10f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }
    androidx.compose.material3.Card(
        onClick = { if (!submitted) onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    submitted && isCorrectOption -> CorrectGreen
                    submitted && selected && !isCorrectOption -> WrongRed
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.padding(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PracticeSummary(vm: PracticeViewModel, st: PracticeUiState, settings: AppSettings, nav: NavHostController) {
    val acc = if (st.answeredCount == 0) 0f else st.correctCount.toFloat() / st.answeredCount
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("本次练习完成！", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${(acc * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("正确率", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SummaryStat("已答", "${st.answeredCount}")
                    SummaryStat("答对", "${st.correctCount}")
                    SummaryStat("新增错题", "${st.newWrongIds.size}")
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("奶龙学习建议", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(vm.summaryAdvice(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (st.newWrongIds.isNotEmpty()) {
            Button(onClick = {
                PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "本次错题重练", questionIds = st.newWrongIds.toList(), shuffleOptions = settings.shuffleOptions)
                nav.navigate(com.nailong.quiz.ui.nav.Routes.PRACTICE) { popUpTo(com.nailong.quiz.ui.nav.Routes.HOME) }
            }, modifier = Modifier.fillMaxWidth()) { Text("立即重练本次错题") }
        }
        OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
