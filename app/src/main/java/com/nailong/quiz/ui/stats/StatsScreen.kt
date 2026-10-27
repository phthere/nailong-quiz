package com.nailong.quiz.ui.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.ExamRecordEntity
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.components.ProgressRing
import com.nailong.quiz.ui.theme.CorrectGreen
import com.nailong.quiz.ui.theme.NailongYellow
import com.nailong.quiz.ui.theme.SkyBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatsState(
    val total: Int = 0,
    val practiced: Int = 0,
    val totalAnswers: Int = 0,
    val correct: Int = 0,
    val today: Int = 0,
    val streak: Int = 0,
    val daily7: List<Pair<Long, Int>> = emptyList(),
    val daily30: List<Pair<Long, Int>> = emptyList(),
    val typeAccuracy: Map<String, Pair<Int, Int>> = emptyMap()
)

@Composable
fun StatsScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    var st by remember { mutableStateOf(StatsState()) }
    val wrongCount by app.repository.wrongCountFlow().collectAsState(initial = 0)
    val masteredCount by app.repository.masteredCountFlow().collectAsState(initial = 0)
    val favCount by app.repository.db.favoriteDao().countFlow().collectAsState(initial = 0)
    val exams by app.repository.db.examDao().examsFlow().collectAsState(initial = emptyList())

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val repo = app.repository
        st = StatsState(
            total = repo.questionCount(),
            practiced = repo.practicedCount(),
            totalAnswers = repo.totalAnswers(),
            correct = repo.correctCount(),
            today = repo.todayAnswers(),
            streak = repo.streakDays(),
            daily7 = repo.dailyCounts(7),
            daily30 = repo.dailyCounts(30),
            typeAccuracy = repo.accuracyByType()
        )
    }

    val accuracy = if (st.totalAnswers == 0) 0f else st.correct.toFloat() / st.totalAnswers
    val masteryProgress = if (wrongCount == 0) 0f else masteredCount.toFloat() / wrongCount

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("学习统计", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        StatCell("总题数", "${st.total}", Modifier.weight(1f))
                        StatCell("已练习", "${st.practiced}", Modifier.weight(1f))
                        StatCell("未练习", "${st.total - st.practiced}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        StatCell("累计答题", "${st.totalAnswers}", Modifier.weight(1f))
                        StatCell("总正确率", "${(accuracy * 100).toInt()}%", Modifier.weight(1f))
                        StatCell("今日刷题", "${st.today}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        StatCell("连续学习", "${st.streak} 天", Modifier.weight(1f))
                        StatCell("当前错题", "$wrongCount", Modifier.weight(1f))
                        StatCell("收藏题目", "$favCount", Modifier.weight(1f))
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("最近 7 天刷题趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    BarChart(st.daily7.map { it.second }, Modifier.fillMaxWidth().height(120.dp), SkyBlue)
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("最近 30 天学习趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    BarChart(st.daily30.map { it.second }, Modifier.fillMaxWidth().height(120.dp), NailongYellow)
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("各题型正确率", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (st.typeAccuracy.isEmpty()) {
                        Text("还没有答题数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    st.typeAccuracy.forEach { (type, stat) ->
                        val acc = if (stat.second == 0) 0f else stat.first.toFloat() / stat.second
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(type, style = MaterialTheme.typography.bodySmall)
                                Text("${stat.first}/${stat.second} · ${(acc * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MiniProgress(acc, if (acc >= 0.7f) CorrectGreen else MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(progress = masteryProgress, sizeDp = 90.dp, color = CorrectGreen) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(masteryProgress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("已掌握", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.padding(10.dp))
                    Column {
                        Text("错题掌握进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("已掌握 $masteredCount / $wrongCount 道错题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("历史模拟考试成绩", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (exams.isEmpty()) {
                        Text("还没有考试记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        ExamTrendChart(exams.takeLast(10), Modifier.fillMaxWidth().height(120.dp))
                        Text("最近 ${exams.size} 次考试 · 平均分 ${"%.1f".format(exams.map { it.score }.average())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniProgress(value: Float, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(color = Color(0xFFE0E6EC), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
        drawRoundRect(color = color, size = size.copy(width = size.width * value.coerceIn(0f, 1f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
    }
}

@Composable
private fun BarChart(values: List<Int>, modifier: Modifier, color: Color) {
    val barColor = color
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val maxV = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val n = values.size
        val gap = if (n > 10) 3.dp.toPx() else 8.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        values.forEachIndexed { i, v ->
            val h = size.height * v / maxV
            val x = i * (barW + gap)
            drawRoundRect(color = track, topLeft = Offset(x, 0f), size = Size(barW, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
            if (v > 0) {
                drawRoundRect(color = barColor, topLeft = Offset(x, size.height - h), size = Size(barW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
            }
        }
    }
}

@Composable
private fun ExamTrendChart(exams: List<ExamRecordEntity>, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Canvas(modifier) {
        if (exams.isEmpty()) return@Canvas
        // 60 分参考线
        val y60 = size.height * (1 - 60f / 100f)
        drawLine(grid, Offset(0f, y60), Offset(size.width, y60), strokeWidth = 2f)
        val points = exams.mapIndexed { i, e ->
            val x = if (exams.size == 1) size.width / 2 else size.width * i / (exams.size - 1)
            val y = size.height * (1 - (e.score.toFloat() / 100f).coerceIn(0f, 1f))
            Offset(x, y)
        }
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, lineColor, style = Stroke(width = 4f))
        }
        points.forEach { drawCircle(lineColor, radius = 6f, center = it) }
    }
}
