package com.nailong.quiz.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun suggestedDailyGoal(remaining: Int, exam: LocalDate, today: LocalDate = LocalDate.now()): Int {
    val days = ChronoUnit.DAYS.between(today, exam).coerceAtLeast(1)
    return ((remaining.coerceAtLeast(0).toLong() + days - 1) / days).toInt().coerceAtLeast(1)
}

@Composable
fun GoalDialog(app: NailongApp, settings: AppSettings, remaining: Int, onDismiss: () -> Unit) {
    var goal by remember { mutableStateOf(settings.dailyGoal.toString()) }
    var date by remember { mutableStateOf(settings.targetExamDate) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
    val validDate = date.isBlank() || (parsed != null && !parsed.isBefore(LocalDate.now()))
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("调整每日目标") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(goal, { goal = it.filter(Char::isDigit).take(4) }, label = { Text("每天刷多少题") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(date, { date = it.take(10) }, label = { Text("考试日期（选填）") }, placeholder = { Text("2026-12-01") },
                supportingText = { Text("格式：年-月-日，可清空") }, isError = !validDate)
            if (parsed != null && validDate) {
                val suggestion = suggestedDailyGoal(remaining, parsed)
                Text("当前题库还有 $remaining 道未练习，距考试 ${ChronoUnit.DAYS.between(LocalDate.now(), parsed)} 天。建议每天 $suggestion 题，可按自己的节奏调整。")
                TextButton(onClick = { goal = suggestion.toString() }) { Text("采用建议：每天 $suggestion 题") }
            }
            if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !saving && validDate && (goal.toIntOrNull() ?: 0) in 1..9999, onClick = {
            saving = true
            scope.launch {
                try { app.settingsRepo.update { it.copy(dailyGoal = goal.toInt(), targetExamDate = date) }; onDismiss() }
                catch (e: Exception) { error = e.message }
                finally { saving = false }
            }
        }) { Text("保存目标") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") } })
}
