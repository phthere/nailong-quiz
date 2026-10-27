package com.nailong.quiz.ui.nav

import androidx.compose.foundation.layout.padding
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
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(inner)) {
            composable(Routes.HOME) { HomeScreen(app, settings, nav) }
            composable(Routes.BANK) { BankScreen(app, settings, nav) }
            composable(Routes.WRONG) { WrongBookScreen(app, settings, nav) }
            composable(Routes.STATS) { StatsScreen(app, settings, nav) }
            composable(Routes.MINE) { SettingsScreen(app, settings, nav) }
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
