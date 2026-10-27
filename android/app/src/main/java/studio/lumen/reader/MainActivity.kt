package studio.lumen.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import studio.lumen.reader.ui.LumenRoot
import studio.lumen.reader.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val store by viewModel.store.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (store.settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            // enableEdgeToEdge 只按系统深色决定图标颜色，应用内可单独切浅色主题；
            // 阅读页是实心浅色背景，图标必须跟着应用主题走，否则白图标看不见。
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            LumenTheme(
                darkTheme = dark,
                dynamicColor = store.settings.dynamicColor,
                schemeId = store.settings.colorScheme,
                cornerRadius = store.settings.cornerRadius
            ) {
                LumenRoot(viewModel)
            }
        }
    }
}
