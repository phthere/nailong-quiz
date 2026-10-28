package com.nailong.quiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nailong.quiz.data.DarkMode
import com.nailong.quiz.ui.nav.AppNav
import com.nailong.quiz.ui.theme.NailongTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NailongApp
        setContent {
            val settings by app.settingsRepo.settings.collectAsState(
                initial = com.nailong.quiz.data.AppSettings()
            )
            val dark = when (settings.darkMode) {
                DarkMode.SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            }
            var readyBank by remember { mutableStateOf<Int?>(null) }
            var bankError by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(settings.selectedBank) {
                readyBank = null
                bankError = null
                try {
                    app.activateBank(settings.selectedBank)
                    com.nailong.quiz.ui.nav.PracticeBus.config = null
                    com.nailong.quiz.ui.nav.PracticeBus.examConfig = null
                    readyBank = settings.selectedBank
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    bankError = "题库加载失败：${e.message}"
                }
            }
            NailongTheme(darkTheme = dark, fontScale = settings.fontScale) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (readyBank == settings.selectedBank) {
                        key(readyBank) { AppNav(app, settings) }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (bankError == null) CircularProgressIndicator()
                            Text(bankError ?: "正在加载题库…")
                        }
                    }
                }
            }
        }
    }
}
