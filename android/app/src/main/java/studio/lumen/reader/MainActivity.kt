package studio.lumen.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            LumenTheme(darkTheme = dark, dynamicColor = store.settings.dynamicColor) {
                LumenRoot(viewModel)
            }
        }
    }
}
