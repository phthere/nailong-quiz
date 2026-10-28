package com.nailong.quiz.ui.bank

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.*
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.*
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.nav.Routes
import com.nailong.quiz.ui.practice.PracticeConfig
import com.nailong.quiz.ui.practice.PracticeMode
import com.nailong.quiz.ui.settings.QuestionEditor
import com.nailong.quiz.ui.theme.CorrectGreen
import com.nailong.quiz.ui.theme.NailongYellow
import com.nailong.quiz.ui.theme.WrongRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BankScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    val repo = app.repository
    val all by remember(repo) { repo.allQuestionsFlow() }.collectAsState(emptyList())
    val latest by remember(repo) { repo.db.recordDao().latestAnswersFlow() }.collectAsState(emptyList())
    val favorites by remember(repo) { repo.favoriteIdsFlow() }.collectAsState(emptyList())
    val status = remember(latest) { latest.associate { it.questionId to it.correct } }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var filtered by remember { mutableStateOf<List<Question>>(emptyList()) }
    var editing by remember { mutableStateOf<QuestionJson?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val pending = remember(all) { all.count { it.needsReview } }
    LaunchedEffect(all, query, filter) {
        if (query.isNotBlank()) delay(150)
        filtered = withContext(Dispatchers.Default) {
            val term = query.trim()
            all.filter { question ->
                (filter == "ALL" || (filter == "REVIEW" && question.needsReview) || question.type.name == filter) &&
                    (term.isBlank() || question.origNo.toString() == term || question.stem.contains(term, true) ||
                        question.options.any { it.text.contains(term, true) })
            }
        }
    }
    AppBackground(settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(2.dp))
            PageHeader("题库 · ${all.size} 题", modifier = Modifier.testTag("bank_page_header"))
            OutlinedTextField(query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().testTag("bank_search"),
                shape = MaterialTheme.shapes.medium, singleLine = true,
                placeholder = { Text("搜索题干、选项或原题号") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "清除搜索") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf("ALL" to "全部") + QuestionType.entries.map { it.name to it.zh.removeSuffix("题") } +
                    if (pending > 0) listOf("REVIEW" to "待完善 $pending") else emptyList()
                options.forEach { (value, label) -> FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) }) }
            }
            Text("找到 ${filtered.size} 道 · 仅当前题库${if (pending > 0) " · $pending 道可稍后完善" else ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (filtered.isEmpty()) item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary)
                            Text("未找到相关题目", style = MaterialTheme.typography.titleMedium)
                            Text("试试其他关键词或题型", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(filtered, key = { it.id }) { question ->
                    GlassCard(Modifier.fillMaxWidth().testTag("bank_question_${question.id}"), onClick = {
                        PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "题目练习", questionIds = listOf(question.id), shuffleOptions = settings.shuffleOptions)
                        nav.navigate(Routes.PRACTICE) { launchSingleTop = true }
                    }) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("原题 ${question.origNo} · ${question.typeZh.removeSuffix("题")}", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                if (question.id in favorites) Icon(Icons.Filled.Star, "已收藏", Modifier.size(15.dp), tint = NailongYellow)
                                Text(if (question.needsReview) "待完善" else when (status[question.id]) { true -> "正确"; false -> "错误"; null -> "未答" },
                                    style = MaterialTheme.typography.labelMedium, color = if (question.needsReview) MaterialTheme.colorScheme.secondary else
                                        when (status[question.id]) { true -> CorrectGreen; false -> WrongRed; null -> MaterialTheme.colorScheme.onSurfaceVariant })
                            }
                            Text(question.stem.ifBlank { "题干待补充" }, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (repo.bank is ImportedBank) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editing = question.toQuestionJson() }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                    Text(if (question.needsReview) "完善题目" else "编辑题目")
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    editing?.let { question ->
        QuestionEditor(question, onDismiss = { if (!saving) editing = null }, saving = saving, onSave = { updated ->
            if (!saving) scope.launch {
                saving = true
                try {
                    val result = repo.updateQuestion(updated)
                    editing = null
                    saving = false
                    snackbar.showSnackbar(if (result.conflict) "已保存，可继续完善" else "题目已保存，学习记录保留")
                } catch (e: Exception) {
                    saving = false
                    snackbar.showSnackbar("保存失败：${e.message}")
                }
            }
        })
    }
}
