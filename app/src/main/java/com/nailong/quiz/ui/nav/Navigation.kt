package com.nailong.quiz.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.ui.bank.BankScreen
import com.nailong.quiz.ui.exam.ExamHistoryScreen
import com.nailong.quiz.ui.exam.ExamResultScreen
import com.nailong.quiz.ui.exam.ExamScreen
import com.nailong.quiz.ui.exam.ExamSetupScreen
import com.nailong.quiz.ui.favorite.FavoriteScreen
import com.nailong.quiz.ui.home.HomeScreen
import com.nailong.quiz.ui.practice.PracticeScreen
import com.nailong.quiz.ui.practice.PracticeSetupScreen
import com.nailong.quiz.ui.search.SearchScreen
import com.nailong.quiz.ui.settings.SettingsScreen
import com.nailong.quiz.ui.stats.StatsScreen
import com.nailong.quiz.ui.wrong.WrongBookScreen

object Routes {
    const val HOME = "home"
    const val BANK = "bank"
    const val WRONG = "wrong"
    const val STATS = "stats"
    const val MINE = "mine"
    const val PRACTICE_SETUP = "practice_setup/{mode}"
    const val PRACTICE = "practice"
    const val EXAM_SETUP = "exam_setup"
    const val EXAM = "exam"
    const val EXAM_RESULT = "exam_result/{examId}"
    const val EXAM_HISTORY = "exam_history"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val PDF_IMPORT = "pdf_import"
    const val DOCUMENT_IMPORT = "document_import"
    const val SPRINT = "sprint"

    fun practiceSetup(mode: String) = "practice_setup/$mode"
    fun examResult(examId: Long) = "exam_result/$examId"
}

data class TabItem(val route: String, val label: String, val icon: ImageVector)

val tabs = listOf(
    TabItem(Routes.HOME, "首页", Icons.Filled.Home),
    TabItem(Routes.BANK, "题库", Icons.Filled.MenuBook),
    TabItem(Routes.WRONG, "错题", Icons.Filled.Bookmark),
    TabItem(Routes.STATS, "统计", Icons.Filled.Analytics),
    TabItem(Routes.MINE, "我的", Icons.Filled.Person),
)

/** 当前练习/考试配置在内存中传递（进程内有效），顺序模式进度持久化在数据库 */
object PracticeBus {
    var config: com.nailong.quiz.ui.practice.PracticeConfig? = null
    var examConfig: com.nailong.quiz.ui.exam.ExamConfig? = null
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNav(app: NailongApp, settings: AppSettings) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val isTab = tabs.any { it.route == current }

    Scaffold(
        topBar = { if (isTab) com.nailong.quiz.ui.components.BankSelector(app, settings) },
        bottomBar = {
            if (isTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val scale = animateFloatAsState(if (current == tab.route) 1.06f else 1f, spring(stiffness = 600f), label = "navigationIcon")
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                if (current != tab.route) nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) },
                            label = { Text(tab.label, fontWeight = if (current == tab.route) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            enterTransition = {
                if (tabs.any { it.route == initialState.destination.route } && tabs.any { it.route == targetState.destination.route }) fadeIn(tween(180))
                else fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 10 }
            },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it / 14 } },
            popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 10 } }
        ) {
            composable(Routes.HOME) { HomeScreen(app, settings, nav) }
            composable(Routes.BANK) { BankScreen(app, settings, nav) }
            composable(Routes.WRONG) { WrongBookScreen(app, settings, nav) }
            composable(Routes.STATS) { StatsScreen(app, settings, nav) }
            composable(Routes.MINE) { SettingsScreen(app, settings, nav) }
            composable(Routes.DOCUMENT_IMPORT) { com.nailong.quiz.ui.settings.PdfImportScreen(app, settings, nav, documentMode = true) }
            composable(Routes.PDF_IMPORT) { com.nailong.quiz.ui.settings.PdfImportScreen(app, settings, nav) }
            composable(Routes.PRACTICE_SETUP) { back ->
                val mode = back.arguments?.getString("mode") ?: "sequential"
                PracticeSetupScreen(app, settings, nav, mode)
            }
            composable(Routes.PRACTICE) { PracticeScreen(app, settings, nav) }
            composable(Routes.EXAM_SETUP) { ExamSetupScreen(app, settings, nav) }
            composable(Routes.EXAM) { ExamScreen(app, settings, nav) }
            composable(Routes.EXAM_RESULT) { back ->
                val id = back.arguments?.getString("examId")?.toLongOrNull() ?: 0L
                ExamResultScreen(app, settings, nav, id)
            }
            composable(Routes.EXAM_HISTORY) { ExamHistoryScreen(app, settings, nav) }
            composable(Routes.SEARCH) { SearchScreen(app, settings, nav) }
            composable(Routes.FAVORITES) { FavoriteScreen(app, settings, nav) }
        }
    }
}
