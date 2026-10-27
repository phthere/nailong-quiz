package com.nailong.quiz.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.BuiltInBank
import kotlinx.coroutines.launch

@Composable
fun BankSelector(app: NailongApp, settings: AppSettings) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val banks by app.bankCatalog.banks.collectAsState()
    val current = banks.firstOrNull { it.id == settings.selectedBank } ?: BuiltInBank.DEFAULT
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("当前题库 · 学习记录独立保存", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${current.title} · ${current.count} 题", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = { open = true }) { Text("切换") }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("选择题库") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.bankCatalog.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    banks.forEach { bank ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                open = false
                                if (bank != current) scope.launch {
                                    app.settingsRepo.update { it.copy(selectedBank = bank.id) }
                                }
                            }
                        ) { Text("${if (bank == current) "✓ " else ""}${bank.title} · ${bank.count} 题") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } }
        )
    }
}
