package studio.lumen.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import studio.lumen.reader.ui.components.LocalCornerRadius
import studio.lumen.reader.ui.theme.schemes.AccentColorScheme
import studio.lumen.reader.ui.theme.schemes.AugustColorScheme
import studio.lumen.reader.ui.theme.schemes.BaseColorScheme
import studio.lumen.reader.ui.theme.schemes.CarlottaColorScheme
import studio.lumen.reader.ui.theme.schemes.ElinkColorScheme
import studio.lumen.reader.ui.theme.schemes.GRColorScheme
import studio.lumen.reader.ui.theme.schemes.KoharuColorScheme
import studio.lumen.reader.ui.theme.schemes.LemonColorScheme
import studio.lumen.reader.ui.theme.schemes.MujikaColorScheme
import studio.lumen.reader.ui.theme.schemes.PhoebeColorScheme
import studio.lumen.reader.ui.theme.schemes.SoraColorScheme
import studio.lumen.reader.ui.theme.schemes.TransparentColorScheme
import studio.lumen.reader.ui.theme.schemes.WHColorScheme
import studio.lumen.reader.ui.theme.schemes.YuukaColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 前 12 套来自 legado-with-MD3 的色板（GR 为默认），后 10 套与桌面端主题同名同色。
object LumenSchemes {
    val options = listOf(
        "gr" to ("草绿" to GRColorScheme),
        "lemon" to ("柠檬" to LemonColorScheme),
        "wh" to ("纯白" to WHColorScheme),
        "elink" to ("墨韵" to ElinkColorScheme),
        "sora" to ("苍空" to SoraColorScheme),
        "august" to ("八月" to AugustColorScheme),
        "carlotta" to ("焦糖" to CarlottaColorScheme),
        "koharu" to ("小春" to KoharuColorScheme),
        "yuuka" to ("悠花" to YuukaColorScheme),
        "phoebe" to ("菲比" to PhoebeColorScheme),
        "mujika" to ("梦华" to MujikaColorScheme),
        "transparent" to ("透明" to TransparentColorScheme),
        "sage" to ("鼠尾草" to AccentColorScheme(Color(0xFF38664D), Color(0xFFF6F8F5))),
        "sky" to ("晴空蓝" to AccentColorScheme(Color(0xFF376782), Color(0xFFF5F8FC))),
        "rose" to ("浅蔷薇" to AccentColorScheme(Color(0xFF9B596C), Color(0xFFFCF7F7))),
        "sand" to ("暖麦色" to AccentColorScheme(Color(0xFF907139), Color(0xFFFBF9F4))),
        "lavender" to ("浅薰衣草" to AccentColorScheme(Color(0xFF75638E), Color(0xFFF8F7FC))),
        "mint" to ("清薄荷" to AccentColorScheme(Color(0xFF397B68), Color(0xFFF4FAF8))),
        "apricot" to ("杏子橙" to AccentColorScheme(Color(0xFF9A703B), Color(0xFFFDF9F2))),
        "ice" to ("冰川蓝" to AccentColorScheme(Color(0xFF3C7B91), Color(0xFFF4FAFB))),
        "pearl" to ("珍珠灰" to AccentColorScheme(Color(0xFF626F83), Color(0xFFF7F8FA))),
        "peach" to ("蜜桃粉" to AccentColorScheme(Color(0xFFA5685D), Color(0xFFFFF8F5)))
    )

    fun resolve(id: String): BaseColorScheme =
        options.firstOrNull { it.first == id }?.second?.second ?: GRColorScheme
}

/** 标题与卡片名使用衬线，正文与标签保持无衬线，贴近参考应用的排版。 */
private val Serif = FontFamily.Serif
private val SerifHeadline = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.SemiBold,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

private val LumenTypography = Typography(
    displaySmall = SerifHeadline.copy(fontSize = 34.sp, lineHeight = 42.sp),
    headlineLarge = SerifHeadline.copy(fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = SerifHeadline.copy(fontSize = 25.sp, lineHeight = 33.sp),
    headlineSmall = SerifHeadline.copy(fontSize = 21.sp, lineHeight = 28.sp),
    titleLarge = SerifHeadline.copy(fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = SerifHeadline.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = SerifHeadline.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun LumenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    schemeId: String = "gr",
    cornerRadius: Int = 16,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> LumenSchemes.resolve(schemeId).getColorScheme(darkTheme)
    }
    CompositionLocalProvider(LocalCornerRadius provides cornerRadius.dp) {
        MaterialTheme(colorScheme = colors, typography = LumenTypography, content = content)
    }
}
