package studio.lumen.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import studio.lumen.reader.MainViewModel
import studio.lumen.reader.ui.components.GlassMode
import studio.lumen.reader.ui.components.GlassSurface
import studio.lumen.reader.ui.components.LiquidBottomBar
import studio.lumen.reader.ui.components.LiquidNavItem
import studio.lumen.reader.ui.components.LocalFloatingNavPadding
import studio.lumen.reader.ui.components.glassNavShape
import studio.lumen.reader.ui.screens.BilingualScreen
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
    val floating = settings.floatingNav
    val showNav = route in mainRoutes
    val glassActive = showNav && glassMode != GlassMode.Off
    val backdrop = rememberLayerBackdrop()
    val contentBehindBar = glassActive || (showNav && floating)
    val navPadding = when {
        !showNav -> 0.dp
        floating -> 132.dp
        glassActive -> 128.dp
        else -> 0.dp
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    CompositionLocalProvider(LocalFloatingNavPadding provides navPadding) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showNav && !floating) {
                    StandardNavigationBar(route, glassMode, if (glassActive) backdrop else null) {
                        navController.navigateMain(it)
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = if (contentBehindBar) 0.dp else padding.calculateBottomPadding()
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (glassActive) Modifier.layerBackdrop(backdrop) else Modifier)
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
                                onOpenNewsroom = { navController.navigate("newsroom") },
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
                            ReaderScreen(
                                viewModel = viewModel,
                                onBack = {
                                    viewModel.finishReading()
                                    navController.popBackStack()
                                },
                                onBilingual = { navController.navigate("bilingual") },
                                onAsk = { service -> navController.navigate("chat/$service") }
                            )
                        }
                        composable("bilingual") {
                            BilingualScreen(
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
                if (showNav && floating) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        FloatingNavigationBar(route, glassMode, backdrop) { navController.navigateMain(it) }
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

@Composable
private fun RowScope.LumenNavItems(route: String?, onNavigate: (String) -> Unit) {
    destinations.forEach { destination ->
        NavigationBarItem(
            selected = route == destination.route,
            onClick = { onNavigate(destination.route) },
            icon = { Icon(destination.icon, contentDescription = destination.label) },
            label = { Text(destination.label) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun StandardNavigationBar(
    route: String?,
    mode: GlassMode,
    backdrop: LayerBackdrop?,
    onNavigate: (String) -> Unit
) {
    GlassSurface(
        mode = mode,
        shape = glassNavShape(false, mode),
        modifier = Modifier.fillMaxWidth(),
        backdrop = backdrop
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            LumenNavItems(route, onNavigate)
        }
    }
}

@Composable
private fun FloatingNavigationBar(
    route: String?,
    mode: GlassMode,
    backdrop: LayerBackdrop,
    onNavigate: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        LiquidBottomBar(
            items = destinations.map { LiquidNavItem(it.label, it.icon) },
            selectedIndex = destinations.indexOfFirst { it.route == route }.coerceAtLeast(0),
            backdrop = if (mode == GlassMode.Off) null else backdrop,
            onSelected = { onNavigate(destinations[it].route) }
        )
    }
}
