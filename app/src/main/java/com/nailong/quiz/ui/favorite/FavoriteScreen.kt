package com.nailong.quiz.ui.favorite

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.domain.Question
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.nav.Routes
import com.nailong.quiz.ui.practice.PracticeConfig
import com.nailong.quiz.ui.practice.PracticeMode
import com.nailong.quiz.ui.theme.WrongRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FavoriteScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    val favIds by app.repository.favoriteIdsFlow().collectAsState(initial = emptyList())
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var typeFilter by remember { mutableStateOf<QuestionType?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(favIds) {
        questions = app.repository.questionsByIds(favIds).sortedBy { it.id }
    }

    val filtered = if (typeFilter == null) questions else questions.filter { it.type == typeFilter }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text("我的收藏（${favIds.size}）") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            if (questions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有收藏题目，刷题时点击右上角爱心收藏") }
            } else {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "收藏顺序练习", questionIds = filtered.map { it.id }, shuffleOptions = settings.shuffleOptions)
                            nav.navigate(Routes.PRACTICE)
                        }, enabled = filtered.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("顺序练习") }
                        OutlinedButton(onClick = {
                            PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "收藏随机练习", questionIds = filtered.map { it.id }.shuffled(), shuffleOptions = settings.shuffleOptions)
                            nav.navigate(Routes.PRACTICE)
                        }, enabled = filtered.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("随机练习") }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("全部") })
                        QuestionType.entries.forEach { t ->
                            FilterChip(selected = typeFilter == t, onClick = { typeFilter = t }, label = { Text(t.zh) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { q ->
                        GlassCard(Modifier.fillMaxWidth(), onClick = {
                            PracticeBus.config = PracticeConfig(PracticeMode.COLLECTION, "收藏练习", questionIds = filtered.map { it.id }, shuffleOptions = settings.shuffleOptions, resumeIndex = filtered.indexOf(q))
                            nav.navigate(Routes.PRACTICE)
                        }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${q.typeZh} · 原题号 #${q.origNo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(q.stem, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                }
                                IconButton(onClick = { scope.launch { app.repository.toggleFavorite(q.id) } }) {
                                    Icon(Icons.Filled.Favorite, "取消收藏", tint = WrongRed)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
