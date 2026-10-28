package com.nailong.quiz.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.ui.components.*
import com.nailong.quiz.ui.nav.Routes
import com.nailong.quiz.ui.theme.NailongYellow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

 data class HomeState(val total: Int = 0, val practiced: Int = 0, val correct: Int = 0,
    val totalAnswers: Int = 0, val todayAnswers: Int = 0, val streak: Int = 0, val dailyGoal: Int = 30) {
    val accuracy: Float get() = if (totalAnswers == 0) 0f else correct.toFloat() / totalAnswers
    val coverage: Float get() = if (total == 0) 0f else practiced.toFloat() / total
}
class HomeViewModel(private val app: NailongApp) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val repo = app.repository
            val settings = app.settingsRepo.settings.first()
            mutableState.value = HomeState(repo.questionCount(), repo.practicedCount(), repo.correctCount(),
                repo.totalAnswers(), repo.todayAnswers(), repo.streakDays(), settings.dailyGoal)
        }
    }
}

@Composable
fun HomeScreen(app: NailongApp, settings: AppSettings, nav: androidx.navigation.NavHostController) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(app))
    val state by vm.state.collectAsState()
    val repository = app.repository
    val favorites by repository.db.favoriteDao().countFlow().collectAsState(0)
    val wrongs by repository.wrongCountFlow().collectAsState(0)
    val mastered by repository.masteredCountFlow().collectAsState(0)
    val answers by repository.db.recordDao().countFlow().collectAsState(0)
    val haptic = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .3f
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(answers, settings.dailyGoal) { vm.refresh() }
    LaunchedEffect(Unit) { visible = true }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun navigate(route: String) {
        if (settings.vibrate) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        nav.navigate(route) { launchSingleTop = true }
    }
    AppBackground(settings, showBg = true) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("home_scroll"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedVisibility(visible, enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { it / 6 }) {
                GlassCard(Modifier.fillMaxWidth().testTag("home_hero"), corner = 30.dp) {
                    Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(colors.surface,
                        colors.primary.copy(alpha = if (dark) .08f else .04f))))
                        .padding(22.dp).heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("奶龙刷题", style = MaterialTheme.typography.headlineLarge)
                            Text(repository.bank.title, style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(38.dp), tint = NailongYellow)
                    }
                }
            }
            if (settings.encouragement) Surface(color = colors.surface.copy(alpha = .94f), shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.WbSunny, null, Modifier.size(17.dp), tint = NailongYellow)
                    Text("今天也要和奶龙一起进步呀！", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(126.dp))
            GlassCard(Modifier.fillMaxWidth().testTag("home_progress"), corner = 28.dp) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(state.todayAnswers.toFloat() / state.dailyGoal.coerceAtLeast(1), 104.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${state.todayAnswers}", style = MaterialTheme.typography.headlineLarge)
                            Text("今日完成", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("小步进步，稳稳积累", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("今日目标 ${state.dailyGoal} 题", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text("已练习 ${state.practiced} / ${state.total}", style = MaterialTheme.typography.titleMedium)
                        Text("连续学习 ${state.streak} 天", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val accuracy = if (state.totalAnswers == 0) "—" else if (state.accuracy >= 1f) "100%" else "%.1f%%".format(state.accuracy * 100)
                MetricCard(accuracy, "正确率", Modifier.weight(1f), small = true) { navigate(Routes.STATS) }
                MetricCard("${(wrongs - mastered).coerceAtLeast(0)}", "待巩固", Modifier.weight(1f)) { navigate(Routes.WRONG) }
                MetricCard("$favorites", "已收藏", Modifier.weight(1f)) { navigate(Routes.FAVORITES) }
            }
            Text("和奶龙开始学习", style = MaterialTheme.typography.headlineSmall.copy(
                shadow = if (dark) Shadow(Color.Black.copy(alpha = .55f), blurRadius = 8f) else null),
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionCard("顺序刷题", "继续上次进度", Icons.Filled.FormatListNumbered, "sequential", Modifier.weight(1f)) { navigate(Routes.practiceSetup("sequential")) }
                ActionCard("随机刷题", "换个顺序记得更牢", Icons.Filled.Shuffle, "random", Modifier.weight(1f)) { navigate(Routes.practiceSetup("random")) }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionCard("模拟考试", "计时训练，完整复盘", Icons.Filled.Timer, "exam", Modifier.weight(1f)) { navigate(Routes.EXAM_SETUP) }
                ActionCard("考前冲刺", "优先突破薄弱题目", Icons.Filled.Bolt, "sprint", Modifier.weight(1f)) { navigate(Routes.practiceSetup("sprint")) }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionCard("错题巩固", "让每次错误有收获", Icons.Filled.Bookmark, "wrong", Modifier.weight(1f)) { navigate(Routes.WRONG) }
                ActionCard("我的收藏", "常看常记重点题", Icons.Filled.Star, "favorites", Modifier.weight(1f)) { navigate(Routes.FAVORITES) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { navigate(Routes.SEARCH) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Search, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("题目搜索")
                }
                OutlinedButton(onClick = { navigate(Routes.EXAM_HISTORY) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.History, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("考试记录")
                }
            }
            Text("完全离线 · 无广告 · 学习记录保存在本机", style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    GlassCard(modifier, corner = 24.dp, onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 20.dp).heightIn(min = 54.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, fontSize = if (small) 22.sp else 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionCard(label: String, subtitle: String, icon: ImageVector, tag: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxHeight().testTag("home_action_$tag"), corner = 28.dp, onClick = onClick) {
        Column(Modifier.fillMaxWidth().heightIn(min = 154.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(29.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
class HomeViewModelFactory(private val app: NailongApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(app) as T
}
