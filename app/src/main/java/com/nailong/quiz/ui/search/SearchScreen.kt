package com.nailong.quiz.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    var keyword by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<QuestionType?>(null) }
    var results by remember { mutableStateOf<List<Question>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Question?>(null) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        scope.launch {
            var r = app.repository.search(keyword)
            typeFilter?.let { t -> r = r.filter { it.type == t } }
            results = r
            searched = true
        }
    }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text("题目搜索") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it; if (it.isNotBlank()) doSearch() },
                    placeholder = { Text("搜索题干或选项关键词…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = typeFilter == null, onClick = { typeFilter = null; if (keyword.isNotBlank()) doSearch() }, label = { Text("全部题型") })
                    QuestionType.entries.forEach { t ->
                        FilterChip(selected = typeFilter == t, onClick = { typeFilter = t; if (keyword.isNotBlank()) doSearch() }, label = { Text(t.zh) })
                    }
                }
                if (searched) {
                    Text("共找到 ${results.size} 道题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            when {
                !searched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("输入关键词开始搜索") }
                results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有找到匹配的题目") }
                else -> LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results, key = { it.id }) { q ->
                        GlassCard(Modifier.fillMaxWidth(), onClick = { detail = q }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${q.typeZh} · 原题号 #${q.origNo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(q.stem, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    detail?.let { q ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("${q.typeZh} #${q.origNo}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(q.stem, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    q.options.forEach { opt ->
                        val isAns = opt.label in Grader.answerLetters(q.answer)
                        Text("${opt.label}. ${opt.text}", style = MaterialTheme.typography.bodySmall, color = if (isAns) CorrectGreen else Color.Unspecified)
                    }
                    val std = when (q.type) {
                        QuestionType.FILL -> q.fillAnswers.joinToString("；")
                        else -> q.answer
                    }
                    Text("答案：$std", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = CorrectGreen)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    detail = null
                    PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "搜索结果练习", questionIds = results.map { it.id }, shuffleOptions = settings.shuffleOptions, resumeIndex = results.indexOf(q))
                    nav.navigate(Routes.PRACTICE)
                }) { Text("练这道题") }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("关闭") } }
        )
    }
}
