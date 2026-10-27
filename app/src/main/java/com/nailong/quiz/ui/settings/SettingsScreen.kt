package com.nailong.quiz.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.BackupManager
import com.nailong.quiz.data.DarkMode
import com.nailong.quiz.data.ThemeBg
import com.nailong.quiz.ui.components.AppBackground
import com.nailong.quiz.ui.components.GlassCard
import com.nailong.quiz.ui.components.drawableRes
import com.nailong.quiz.ui.theme.WrongRed
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: NailongApp, settings: AppSettings, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var total by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<PendingAction?>(null) }
    val backupManager = remember { BackupManager(app, app.db) }

    androidx.compose.runtime.LaunchedEffect(Unit) { total = app.repository.questionCount() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let {
            scope.launch {
                backupManager.exportTo(it).onSuccess { n -> message = "已导出学习记录（$n 条答题记录）" }.onFailure { e -> message = "导出失败：${e.message}" }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                backupManager.importFrom(it).onSuccess { msg -> message = msg }.onFailure { e -> message = "恢复失败：${e.message}" }
            }
        }
    }

    AppBackground(settings = settings, showBg = !settings.bgHomeOnly) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("我的", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            // ---- 主题背景 ----
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("主题背景", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeBg.entries.forEach { bg ->
                            val selected = settings.themeBg == bg
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Image(
                                    painter = painterResource(bg.drawableRes()),
                                    contentDescription = bg.zh,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { scope.launch { app.settingsRepo.update { it.copy(themeBg = bg) } } }
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text(bg.zh, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { scope.launch { app.settingsRepo.update { it.copy(themeBg = ThemeBg.CLOUD) } } }) { Text("恢复默认主题（云端奶龙）") }

                    SettingSwitch("仅首页显示背景", settings.bgHomeOnly) { on -> scope.launch { app.settingsRepo.update { it.copy(bgHomeOnly = on) } } }

                    Text("背景模糊程度", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = settings.bgBlur, onValueChange = { v -> scope.launch { app.settingsRepo.update { it.copy(bgBlur = v) } } }, valueRange = 0f..1f)
                    Text("背景遮罩深浅", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = settings.scrimAlpha, onValueChange = { v -> scope.launch { app.settingsRepo.update { it.copy(scrimAlpha = v) } } }, valueRange = 0f..0.8f)

                    Text("深色模式", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkMode.entries.forEach { dm ->
                            FilterChip(selected = settings.darkMode == dm, onClick = { scope.launch { app.settingsRepo.update { it.copy(darkMode = dm) } } }, label = { Text(dm.zh) })
                        }
                    }

                    Text("字体大小", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.85f to "小", 1.0f to "标准", 1.15f to "大", 1.3f to "特大").forEach { (scale, label) ->
                            FilterChip(selected = settings.fontScale == scale, onClick = { scope.launch { app.settingsRepo.update { it.copy(fontScale = scale) } } }, label = { Text(label) })
                        }
                    }
                }
            }

            // ---- 答题设置 ----
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("答题设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    SettingSwitch("立即显示正确答案", settings.immediateFeedback) { on -> scope.launch { app.settingsRepo.update { it.copy(immediateFeedback = on) } } }
                    SettingSwitch("默认打乱选项顺序", settings.shuffleOptions) { on -> scope.launch { app.settingsRepo.update { it.copy(shuffleOptions = on) } } }
                    SettingSwitch("答题音效", settings.sound) { on -> scope.launch { app.settingsRepo.update { it.copy(sound = on) } } }
                    SettingSwitch("震动反馈", settings.vibrate) { on -> scope.launch { app.settingsRepo.update { it.copy(vibrate = on) } } }
                    SettingSwitch("奶龙鼓励语", settings.encouragement) { on -> scope.launch { app.settingsRepo.update { it.copy(encouragement = on) } } }
                    Spacer(Modifier.height(4.dp))
                    Text("错题掌握标准", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2 to "连续答对 2 次", 3 to "连续答对 3 次").forEach { (n, label) ->
                            FilterChip(selected = settings.masteryThreshold == n, onClick = { scope.launch { app.settingsRepo.update { it.copy(masteryThreshold = n) } } }, label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("每日学习目标", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30, 50).forEach { n ->
                            FilterChip(selected = settings.dailyGoal == n, onClick = { scope.launch { app.settingsRepo.update { it.copy(dailyGoal = n) } } }, label = { Text("$n 题") })
                        }
                    }
                }
            }

            // ---- 数据管理 ----
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("数据管理", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { exportLauncher.launch("nailong_backup.json") }, modifier = Modifier.weight(1f)) { Text("备份当前题库") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.weight(1f)) { Text("恢复当前题库") }
                    }
                    DangerButton("重置学习进度") { confirmAction = PendingAction.RESET_PROGRESS }
                    DangerButton("清空错题记录") { confirmAction = PendingAction.CLEAR_WRONG }
                    DangerButton("清空考试记录") { confirmAction = PendingAction.CLEAR_EXAMS }
                }
            }

            // ---- 关于 ----
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("关于软件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("奶龙刷题", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(app.repository.bank.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("版本 1.1.0 · 内置 3 套题库 · 当前 $total 题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("完全离线运行 · 数据仅保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 危险操作二次确认
    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("确认操作") },
            text = { Text(action.desc) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (action) {
                            PendingAction.RESET_PROGRESS -> { app.repository.resetProgress(); message = "学习进度已重置" }
                            PendingAction.CLEAR_WRONG -> { app.repository.clearWrong(); message = "错题记录已清空" }
                            PendingAction.CLEAR_EXAMS -> { app.db.examDao().clearExams(); app.db.examDao().clearAnswers(); message = "考试记录已清空" }
                        }
                        confirmAction = null
                    }
                }) { Text("确认", color = WrongRed) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("取消") } }
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好的") } }
        )
    }
}

private enum class PendingAction(val desc: String) {
    RESET_PROGRESS("将清空全部答题记录和刷题进度，错题与收藏保留。此操作不可撤销！"),
    CLEAR_WRONG("将清空全部错题记录。此操作不可撤销！"),
    CLEAR_EXAMS("将清空全部历史考试记录。此操作不可撤销！")
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = WrongRed)
    }
}
