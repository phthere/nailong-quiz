package com.nailong.quiz.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nailong.quiz.NailongApp
import com.nailong.quiz.data.AppSettings
import com.nailong.quiz.data.BuiltInBank
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSelector(app: NailongApp, settings: AppSettings) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val banks by app.bankCatalog.banks.collectAsState()
    val current = banks.firstOrNull { it.id == settings.selectedBank } ?: BuiltInBank.DEFAULT
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("当前题库 · 学习记录独立保存", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { open = true }) {
                Text("切换"); Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.ExpandMore, null, Modifier.size(21.dp))
            }
        }
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("选择题库", style = MaterialTheme.typography.headlineSmall)
            Text("切换后继续各自的学习进度", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            app.bankCatalog.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp)) {
                items(banks, key = { it.id }) { bank ->
                    GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, onClick = {
                        scope.launch {
                            sheet.hide(); open = false
                            if (bank.id != current.id) app.settingsRepo.update { it.copy(selectedBank = bank.id) }
                        }
                    }) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Filled.LibraryBooks, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(bank.title, style = MaterialTheme.typography.titleSmall)
                                Text("${bank.count} 道题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (bank.id == current.id) Icon(Icons.Filled.CheckCircle, "当前题库", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
