package com.nailong.quiz.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.components.ProgressRing
import com.nailong.quiz.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeState(
    val total: Int = 0,
    val practiced: Int = 0,
    val correct: Int = 0,
    val totalAnswers: Int = 0,
    val todayAnswers: Int = 0,
    val streak: Int = 0,
    val dailyGoal: Int = 30
) {
    val accuracy: Float get() = if (totalAnswers == 0) 0f else correct.toFloat() / totalAnswers
    val coverage: Float get() = if (total == 0) 0f else practiced.toFloat() / total
}

class HomeViewModel(private val app: NailongApp) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val repo = app.repository
            app.settingsRepo.settings.first().let { s ->
                _state.value = HomeState(
                    total = repo.questionCount(),
                    practiced = repo.practicedCount(),
                    correct = repo.correctCount(),
                    totalAnswers = repo.totalAnswers(),
                    todayAnswers = repo.todayAnswers(),
                    streak = repo.streakDays(),
                    dailyGoal = s.dailyGoal
                )
            }
        }
    }
}

@Composable
fun HomeScreen(app: NailongApp, settings: AppSettings, nav: androidx.navigation.NavHostController) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(app))
    val st by vm.state.collectAsState()
    val favCount by app.repository.db.favoriteDao().countFlow().collectAsState(initial = 0)
    val wrongCount by app.repository.db.wrongDao().countFlow().collectAsState(initial = 0)

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }

    AppBackground(settings = settings, showBg = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("奶龙刷题", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(app.repository.bank.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("今天也要和奶龙一起进步呀！", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))

            // 学习进度卡
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(progress = st.coverage, sizeDp = 110.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(st.coverage * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("已完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatLine("已练题目", "${st.practiced} / ${st.total}")
                        StatLine("当前正确率", "${(st.accuracy * 100).toInt()}%")
                        StatLine("今日刷题", "${st.todayAnswers} / ${st.dailyGoal}")
                        StatLine("连续学习", "${st.streak} 天")
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            Text("快捷刷题", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("顺序刷题", Icons.Filled.FormatListNumbered, Modifier.weight(1f)) { nav.navigate(Routes.practiceSetup("sequential")) }
                QuickAction("随机刷题", Icons.Filled.Shuffle, Modifier.weight(1f)) { nav.navigate(Routes.practiceSetup("random")) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("模拟考试", Icons.Filled.Timer, Modifier.weight(1f)) { nav.navigate(Routes.EXAM_SETUP) }
                QuickAction("考前冲刺", Icons.Filled.AutoAwesome, Modifier.weight(1f)) { nav.navigate(Routes.practiceSetup("sprint")) }
            }
            Spacer(Modifier.height(18.dp))

            Text("我的学习", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard("错题巩固", "$wrongCount 题", Icons.Filled.Bookmark, Modifier.weight(1f)) { nav.navigate(Routes.WRONG) }
                FeatureCard("我的收藏", "$favCount 题", Icons.Filled.Favorite, Modifier.weight(1f)) { nav.navigate(Routes.FAVORITES) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard("题目搜索", "全库检索", Icons.Filled.Search, Modifier.weight(1f)) { nav.navigate(Routes.SEARCH) }
                FeatureCard("考试记录", "历史成绩", Icons.Filled.History, Modifier.weight(1f)) { nav.navigate(Routes.EXAM_HISTORY) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier = modifier, onClick = onClick) {
        Row(modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FeatureCard(label: String, sub: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

class HomeViewModelFactory(private val app: NailongApp) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(app) as T
    }
}
