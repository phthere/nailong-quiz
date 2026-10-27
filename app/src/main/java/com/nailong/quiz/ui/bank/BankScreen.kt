package com.nailong.quiz.ui.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.domain.QuestionType
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.nav.PracticeBus
import com.nailong.quiz.ui.nav.Routes
import com.nailong.quiz.ui.practice.PracticeConfig
import com.nailong.quiz.ui.practice.PracticeMode

@Composable
fun BankScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    var counts by remember { mutableStateOf<Map<QuestionType, Int>>(emptyMap()) }
    var total by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val all = app.repository.allQuestions()
        total = all.size
        counts = all.groupingBy { it.type }.eachCount()
    }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("题库", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("共 $total 题 · ${app.repository.bank.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))

            GlassCard(Modifier.fillMaxWidth(), onClick = {
                PracticeBus.config = PracticeConfig(PracticeMode.SEQUENTIAL, "全部题目", shuffleOptions = settings.shuffleOptions)
                nav.navigate(Routes.PRACTICE)
            }) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FormatListBulleted, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.padding(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("全部题目", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("$total 题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            TypeEntry("单选题", counts[QuestionType.SINGLE] ?: 0, Icons.Filled.RadioButtonChecked) {
                startTypePractice(app, settings, nav, QuestionType.SINGLE)
            }
            TypeEntry("多选题", counts[QuestionType.MULTI] ?: 0, Icons.Filled.CheckBox) {
                startTypePractice(app, settings, nav, QuestionType.MULTI)
            }
            TypeEntry("判断题", counts[QuestionType.JUDGE] ?: 0, Icons.Filled.CheckBox) {
                startTypePractice(app, settings, nav, QuestionType.JUDGE)
            }
            TypeEntry("填空题", counts[QuestionType.FILL] ?: 0, Icons.Filled.Create) {
                startTypePractice(app, settings, nav, QuestionType.FILL)
            }
            TypeEntry("简答题", counts[QuestionType.ESSAY] ?: 0, Icons.Filled.ShortText) {
                startTypePractice(app, settings, nav, QuestionType.ESSAY)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun startTypePractice(app: NailongApp, settings: AppSettings, nav: NavHostController, type: QuestionType) {
    PracticeBus.config = PracticeConfig(
        PracticeMode.RANDOM, "${type.zh}练习",
        scope = com.nailong.quiz.data.RandomScope.ALL,
        typeFilter = setOf(type),
        count = Int.MAX_VALUE,
        shuffleOptions = settings.shuffleOptions
    )
    nav.navigate(Routes.PRACTICE)
}

@Composable
private fun TypeEntry(label: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$count 题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
