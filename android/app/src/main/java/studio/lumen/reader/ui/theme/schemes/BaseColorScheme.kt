package studio.lumen.reader.ui.theme.schemes

import androidx.compose.material3.ColorScheme

abstract class BaseColorScheme {
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme

    fun getColorScheme(darkTheme: Boolean): ColorScheme =
        if (darkTheme) darkScheme else lightScheme
}
