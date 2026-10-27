package studio.lumen.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.ui.components.GlassMode
import studio.lumen.reader.ui.components.GlassWallpaper
import studio.lumen.reader.ui.components.AppBottomBar
import studio.lumen.reader.ui.components.LiquidNavItem
import studio.lumen.reader.ui.components.LocalFloatingNavPadding
import studio.lumen.reader.ui.components.LocalGlassBackdrop
import studio.lumen.reader.ui.screens.ReadingScreen
import studio.lumen.reader.ui.screens.ChatScreen
import studio.lumen.reader.ui.screens.DiscoverScreen
import studio.lumen.reader.ui.screens.NewsroomScreen
import studio.lumen.reader.ui.screens.PluginsScreen
import studio.lumen.reader.ui.screens.ReaderScreen
import studio.lumen.reader.ui.screens.SettingsScreen
import studio.lumen.reader.ui.screens.SourcesScreen
import studio.lumen.reader.ui.screens.StudyScreen

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("discover", "精选", Icons.Outlined.Home),
    Destination("newsroom", "前三", Icons.Outlined.ViewList),
    Destination("sources", "媒体", Icons.Outlined.Public),
    Destination("study", "学习", Icons.Outlined.Book),
    Destination("settings", "设置", Icons.Outlined.Settings)
)

private val mainRoutes = destinations.map { it.route }.toSet()

@Composable
fun LumenRoot(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val store by viewModel.store.collectAsState()
    val settings = store.settings
    val glassMode = GlassMode.from(settings.glassEffect)
    val showNav = route in mainRoutes
    // WebView 与阅读模式都用纯色页面：拍进玻璃采样层既无意义，Chromium 还会在部分机型闪烁。
    val samplingAllowed = route != "site" && route != "reader"
    val glassOn = glassMode != GlassMode.Off && samplingAllowed
    // 壁纸层是页面内所有玻璃的采样源，内容层只给底栏采样：玻璃采样自己所在的渲染层
    // 会让 RenderNode 自引用并栈溢出。
    val wallpaperLayer = rememberLayerBackdrop()
    val contentLayer = rememberLayerBackdrop()
    val wallpaper = if (glassOn) wallpaperLayer else null
    val navLayer = if (glassOn && showNav) contentLayer else null
    val navPadding = if (showNav) 108.dp else 0.dp
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GlassWallpaper(Modifier.fillMaxSize(), backdrop = wallpaper)
        CompositionLocalProvider(
            LocalFloatingNavPadding provides navPadding,
            LocalGlassBackdrop provides wallpaper
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (showNav) {
                        AppBottomBar(
                            items = destinations.map { LiquidNavItem(it.label, it.icon) },
                            selectedIndex = destinations.indexOfFirst { it.route == route }.coerceAtLeast(0),
                            onSelected = { navController.navigateMain(destinations[it].route) },
                            glass = glassOn,
                            backdrop = navLayer
                        )
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding()
                        )
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (glassOn && samplingAllowed) {
                                    Modifier.layerBackdrop(contentLayer)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "discover",
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("discover") {
                                DiscoverScreen(
                                    viewModel = viewModel,
                                    onOpenArticle = { article ->
                                        viewModel.openArticle(article)
                                        navController.navigate("reader")
                                    },
                                    onOpenPlugins = { navController.navigate("plugins") }
                                )
                            }
                            composable("newsroom") {
                                NewsroomScreen(
                                    viewModel = viewModel,
                                    onOpenArticle = { article ->
                                        viewModel.openArticle(article)
                                        navController.navigate("reader")
                                    },
                                    onOpenPlugins = { navController.navigate("plugins") }
                                )
                            }
                            composable("sources") {
                                SourcesScreen(
                                    viewModel = viewModel,
                                    onOpenSource = { article ->
                                        viewModel.openArticle(article)
                                        navController.navigate("reader")
                                    },
                                    onOpenPlugins = { navController.navigate("plugins") }
                                )
                            }
                            composable("study") {
                                StudyScreen(
                                    viewModel = viewModel,
                                    onOpenArticle = { article ->
                                        viewModel.openArticle(article)
                                        navController.navigate("reader")
                                    }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(viewModel, onOpenPlugins = { navController.navigate("plugins") })
                            }
                            composable("plugins") {
                                PluginsScreen(viewModel, onBack = { navController.popBackStack() })
                            }
                            composable("reader") {
                                ReadingScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        viewModel.clearBilingual()
                                        viewModel.finishReading()
                                        navController.popBackStack()
                                    },
                                    onAsk = { service -> navController.navigate("chat/$service") },
                                    onOpenSite = { navController.navigate("site") }
                                )
                            }
                            composable("site") {
                                ReaderScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onAsk = { service -> navController.navigate("chat/$service") }
                                )
                            }
                            composable(
                                "chat/{service}",
                                arguments = listOf(navArgument("service") { type = NavType.StringType })
                            ) { entry ->
                                ChatScreen(
                                    viewModel = viewModel,
                                    service = entry.arguments?.getString("service") ?: "chatgpt",
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateMain(route: String) {
    navigate(route) {
        popUpTo("discover") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
