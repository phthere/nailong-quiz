package com.nailong.quiz.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.PdfQuestionParser
import com.nailong.quiz.data.QuestionJson
import com.nailong.quiz.domain.Option
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(app: NailongApp, settings: AppSettings, nav: NavHostController, documentMode: Boolean = false) {
    val vm: PdfImportViewModel = viewModel(factory = PdfImportViewModelFactory(app))
    val state by vm.state.collectAsState()
    var forceOcr by rememberSaveable { mutableStateOf(false) }
    var onlyIssues by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QuestionJson?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { if (documentMode) vm.readDocument(uri) else vm.read(uri, forceOcr) }
    }
    val issues = remember(state.drafts) { state.drafts.associate { it.question.id to PdfQuestionParser.issues(it.question) } }
    val selected = state.drafts.count { it.included }
    val invalid = state.drafts.count { it.included && issues[it.question.id].orEmpty().isNotEmpty() }

    AppBackground(settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(title = { Text(if (documentMode) "从文档新建题库" else "从 PDF 新建题库") }, navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            })
            when {
                state.busy -> Column(Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(state.progress)
                    Text(if (documentMode) "正在本机直接读取文档文字，请保持此页面打开。" else "识别在本机完成。扫描页较多时需要一些时间，请保持此页面打开。", style = MaterialTheme.typography.bodySmall)
                    if (state.document == null) OutlinedButton(onClick = vm::cancel) { Text("取消识别") }
                }
                state.savedBankId != null -> Column(Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    Text("题库已保存", style = MaterialTheme.typography.headlineSmall)
                    Text("${state.title} · $selected 道题\n可在顶部“切换”中找到；待完善题目可在题库页继续编辑。")
                    Button(onClick = { scope.launch { app.settingsRepo.update { it.copy(selectedBank = state.savedBankId!!) } } },
                        modifier = Modifier.fillMaxWidth()) { Text("切换到新题库") }
                    OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("返回") }
                }
                else -> {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(if (documentMode) "选择文档 → 一键新建题库" else "选择 PDF → 一键新建题库", fontWeight = FontWeight.SemiBold)
                                    Text(if (documentMode) "支持 Word（.docx）和纯文本（.txt）。直接读取文字，保留常见自动编号和题库表格；优先使用带清晰题号、选项及答案的文档。" else "支持带数字题号的单选、多选、判断、填空和简答题。文字版直接读取；扫描版使用本机中文识别。", style = MaterialTheme.typography.bodySmall)
                                    if (!documentMode) Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = forceOcr, onCheckedChange = { forceOcr = it })
                                        Text("整份 PDF 按图片识别（文字乱码时可重试）", style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(onClick = { picker.launch(if (documentMode) arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "application/octet-stream") else arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (documentMode) { if (state.document == null) "选择文档文件" else "重新选择文档" } else { if (state.document == null) "选择 PDF 文件" else "重新选择 PDF" })
                                    }
                                    Text(if (documentMode) "DOCX 最多 50 MB、TXT 最多 10 MB，单次最多 5000 题。旧版 .doc 请另存为 .docx，图片内容请使用 PDF 扫描导入。缺失答案也可保存，稍后再完善。" else "单次最多 50 MB、1000 页、5000 道题。缺失答案也可保存；未完善题目可先学习并自评。", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                        if (state.document != null) {
                            item {
                                OutlinedTextField(value = state.title, onValueChange = vm::title, label = { Text("新题库名称") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            item {
                                Text("识别 ${state.drafts.size} 题 · 已选 $selected 题 · 待完善 $invalid 题", fontWeight = FontWeight.SemiBold)
                                state.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("只看待完善题目")
                                    Switch(checked = onlyIssues, onCheckedChange = { onlyIssues = it })
                                }
                                if (invalid > 0) TextButton(onClick = vm::excludeInvalid) { Text("可选：不导入待完善题目") }
                                OutlinedButton(onClick = { editing = vm.addQuestion() }, enabled = state.drafts.size < 5000) { Text("补充遗漏题目") }
                            }
                            items(state.drafts.filter { !onlyIssues || issues[it.question.id].orEmpty().isNotEmpty() }, key = { it.question.id }) { draft ->
                                val q = draft.question
                                val problems = issues[q.id].orEmpty()
                                GlassCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = draft.included, onCheckedChange = { vm.include(q.id, it) })
                                            Text("${q.typeZh} · 原题号 ${q.origNo}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                            TextButton(onClick = { editing = q }) { Text("修改") }
                                        }
                                        Text(q.stem.ifBlank { "（题干待补充，可先保存）" }, maxLines = 6)
                                        q.options.forEach { Text("${it.label}. ${it.text}", style = MaterialTheme.typography.bodySmall, maxLines = 3) }
                                        Text("答案：${q.answer.ifBlank { "未识别到" }}", color = if (problems.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                        problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                        Text(q.source, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    if (state.document != null) Surface(tonalElevation = 3.dp) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (invalid > 0) "$invalid 道待完善也可一起新建，之后在题库中编辑" else "可直接保存全部选中题目", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = vm::save, enabled = selected > 0, modifier = Modifier.fillMaxWidth()) {
                                Text("新建题库（$selected 题）")
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { question -> QuestionEditor(question, onDismiss = { editing = null }, onSave = { vm.update(it); editing = null }) }
}

@Composable
internal fun QuestionEditor(question: QuestionJson, onDismiss: () -> Unit, onSave: (QuestionJson) -> Unit, saving: Boolean = false) {
    var type by remember(question.id) { mutableStateOf(QuestionType.from(question.type)) }
    var stem by remember(question.id) { mutableStateOf(question.stem) }
    var options by remember(question.id) { mutableStateOf(question.options.joinToString("\n") { "${it.label}. ${it.text.replace('\n', ' ')}" }) }
    var answer by remember(question.id) { mutableStateOf(if (question.type == "FILL") question.fillAnswers.joinToString("\n") else question.answer) }
    val parsedOptions = options.lines().filter { it.isNotBlank() }.map { line ->
        val match = Regex("^\\s*([A-Ha-h])[.、:：)]\\s*(.*)$").matchEntire(line)
        if (match == null) Option("?", line) else Option(match.groupValues[1].uppercase(), match.groupValues[2])
    }
    val edited = PdfQuestionParser.withAnswer(question.copy(type = type.name, typeZh = type.zh, stem = stem,
        options = if (type == QuestionType.SINGLE || type == QuestionType.MULTI) parsedOptions else emptyList(), conflict = false), answer)
    val problems = PdfQuestionParser.issues(edited)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("核对原题号 ${question.origNo}") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuestionType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.zh) }) }
            }
            OutlinedTextField(value = stem, onValueChange = { stem = it }, label = { Text("题干（填空用 ____）") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            if (type == QuestionType.SINGLE || type == QuestionType.MULTI) OutlinedTextField(
                value = options, onValueChange = { options = it }, label = { Text("选项，每行如 A. 选项内容") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text(when (type) {
                QuestionType.SINGLE, QuestionType.MULTI -> "答案字母，如 A 或 ABC"
                QuestionType.JUDGE -> "答案：对或错"
                QuestionType.FILL -> "每空答案单独一行"
                QuestionType.ESSAY -> "参考答案"
            }) }, modifier = Modifier.fillMaxWidth(), minLines = if (type == QuestionType.ESSAY) 3 else 1)
            problems.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { onSave(edited) }, enabled = !saving) { Text(if (saving) "保存中…" else "保存修改") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
