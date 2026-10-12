package app.oslo.repertoire.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import app.oslo.repertoire.GuideRow
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.data.splitDisplayName
import app.oslo.repertoire.ui.components.LocalNavAnimatedScope
import app.oslo.repertoire.ui.components.LocalSharedTransitionScope
import app.oslo.repertoire.ui.details.PersonScreen
import app.oslo.repertoire.ui.details.TitleDetailScreen
import app.oslo.repertoire.ui.watchlists.WatchlistScreen
import app.oslo.repertoire.ui.watchlists.WatchlistsScreen
import app.oslo.repertoire.ui.devices.DevicesScreen
import app.oslo.repertoire.ui.guide.GuideScreen
import app.oslo.repertoire.ui.remote.RemoteScreen
import app.oslo.repertoire.ui.search.SearchScreen
import app.oslo.repertoire.ui.settings.PortalScreen
import app.oslo.repertoire.ui.settings.SettingsScreen
import app.oslo.repertoire.ui.setup.SetupConnectScreen
import app.oslo.repertoire.ui.setup.SetupDoneScreen
import app.oslo.repertoire.ui.setup.SetupPortalScreen
import app.oslo.repertoire.ui.setup.SetupTvScreen
import app.oslo.repertoire.ui.setup.SetupWelcomeScreen
import app.oslo.repertoire.ui.setup.TvGuideScreen
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import app.oslo.repertoire.ui.components.LocalGlass
import app.oslo.repertoire.ui.components.glass
import app.oslo.repertoire.ui.components.glassSource
import app.oslo.repertoire.ui.components.rememberHaptics
import app.oslo.repertoire.ui.theme.Motion
import dev.chrisbanes.haze.HazeState

private const val DURATION = 300

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RepertoireApp(model: GuideViewModel) {
    val nav = rememberNavController()
    val tv by model.remote.status.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val topLevel = TopLevel.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }

    LaunchedEffect(model) {
        model.messages.collectLatest { message ->
            val action = when {
                message.retry != null -> "Retry"
                message.openDevices -> "Devices"
                else -> null
            }
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = action,
                withDismissAction = action != null,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                message.retry?.let(model::play) ?: nav.navigate(DevicesRoute) { launchSingleTop = true }
            }
        }
    }

    model.pendingImport?.let { shared ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = model::dismissImport,
            title = { Text("Import “${shared.name}”?") },
            text = {
                Text(
                    (if (shared.items.size == 1) "1 title" else "${shared.items.size} titles") +
                        ". It's added to your Lists; titles that aren't in your IPTV catalog are kept but skipped when playing.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    model.confirmImport { id -> nav.navigate(WatchlistRoute(id)) { launchSingleTop = true } }
                }) { Text("Import") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = model::dismissImport) { Text("Cancel") } },
        )
    }

    val glass = remember { HazeState() }
    val haptics = rememberHaptics()
    CompositionLocalProvider(LocalGlass provides glass) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            AnimatedVisibility(
                visible = topLevel != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                // Frosted bar: the page scrolls on underneath it.
                NavigationBar(
                    modifier = Modifier
                        .glass()
                        .drawBehind { drawLine(hairline, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1f) },
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    TopLevel.entries.forEach { tab ->
                        val selected = tab == topLevel
                        NavigationBarItem(
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            onClick = {
                                haptics.tap()
                                if (selected) {
                                    if (tab == TopLevel.Guide) model.backToRoot()
                                } else {
                                    nav.navigate(tab.route) {
                                        popUpTo(GuideRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Screens draw their own top bars under the status bar; only the bottom bar is inset here.
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding() + if (model.queue != null) UP_NEXT_HEIGHT else 0.dp)
        Box(Modifier.fillMaxSize()) {
            SharedTransitionLayout(Modifier.fillMaxSize().glassSource(glass)) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    NavHost(
                        navController = nav,
                        startDestination = if (model.setupNeeded) SetupGraph else MainGraph,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn(Motion.fade) + scaleIn(Motion.gentle(), initialScale = 0.985f) },
                        exitTransition = { fadeOut(tween(DURATION / 2)) },
                        popEnterTransition = { fadeIn(Motion.fade) + scaleIn(Motion.gentle(), initialScale = 0.985f) },
                        popExitTransition = { fadeOut(tween(DURATION / 2)) },
                    ) {
                        mainGraph(model, nav, tv, bottom)
                        setupGraph(model, nav, tv)
                        pushed(model, nav, tv)
                    }
                }
            }
            UpNextBar(
                model,
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (topLevel == null) Modifier.navigationBarsPadding() else Modifier)
                    .padding(bottom = padding.calculateBottomPadding() + 8.dp),
            )
            TuneCard(model, Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding() + 12.dp))
        }
    }
    }
}

private fun slideIn(scope: AnimatedContentTransitionScope<*>) =
    scope.slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, Motion.pageOffset) { it / 4 } + fadeIn(Motion.fade)

private fun slideOut(scope: AnimatedContentTransitionScope<*>) =
    scope.slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, Motion.pageOffset) { it / 4 } + fadeOut(tween(DURATION / 2))

/** Registers a destination and exposes its AnimatedContentScope for shared elements. */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    pushed: Boolean = false,
    crossinline content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) {
    if (pushed) {
        composable<T>(
            enterTransition = { slideIn(this) },
            exitTransition = { fadeOut(tween(DURATION)) + scaleOut(Motion.gentle(), targetScale = 0.97f) },
            popEnterTransition = { fadeIn(Motion.fade) + scaleIn(Motion.gentle(), initialScale = 0.97f) },
            popExitTransition = { slideOut(this) },
        ) { entry -> CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) } }
    } else {
        composable<T> { entry -> CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) } }
    }
}

private fun detailsOf(row: GuideRow.Title) = TitleDetailRoute(
    kind = row.kind,
    name = row.name,
    catalogId = row.id,
    icon = row.icon,
    tmdbId = row.tmdbId,
)

private fun NavGraphBuilder.mainGraph(model: GuideViewModel, nav: NavHostController, tv: app.oslo.repertoire.remote.TvStatus, bottom: PaddingValues) {
    navigation<MainGraph>(startDestination = GuideRoute) {
        screen<RemoteRoute> {
            RemoteScreen(model, tv, onOpenDevices = { nav.navigate(DevicesRoute) }, bottomInset = bottom)
        }
        screen<GuideRoute> {
            GuideScreen(
                model,
                onOpenPortal = { nav.navigate(PortalRoute) },
                onDetails = { nav.navigate(detailsOf(it)) },
                bottomInset = bottom,
            )
        }
        screen<SearchRoute> {
            SearchScreen(
                model,
                onDetails = { nav.navigate(detailsOf(it)) },
                onPerson = { nav.navigate(PersonRoute(it.id, it.name)) },
                bottomInset = bottom,
            )
        }
        screen<WatchlistsRoute> {
            WatchlistsScreen(model, onOpen = { nav.navigate(WatchlistRoute(it)) }, bottomInset = bottom)
        }
        screen<SettingsRoute> {
            SettingsScreen(
                model,
                tv,
                onOpenPortal = { nav.navigate(PortalRoute) },
                onOpenDevices = { nav.navigate(DevicesRoute) },
                onOpenGuides = { nav.navigate(TvGuideRoute()) },
                onRunSetup = { nav.navigate(SetupGraph) },
                bottomInset = bottom,
            )
        }
    }
}

private fun NavGraphBuilder.pushed(model: GuideViewModel, nav: NavHostController, tv: app.oslo.repertoire.remote.TvStatus) {
    screen<DevicesRoute>(pushed = true) {
        DevicesScreen(model, tv, onBack = { nav.popBackStack() }, onGuide = { nav.navigate(TvGuideRoute(it)) })
    }
    screen<TvGuideRoute>(pushed = true) { entry ->
        TvGuideScreen(
            model,
            initialGuide = entry.toRoute<TvGuideRoute>().guideId,
            onBack = { nav.popBackStack() },
            onOpenDevices = { nav.navigate(DevicesRoute) { launchSingleTop = true } },
        )
    }
    screen<PortalRoute>(pushed = true) { PortalScreen(model, onBack = { nav.popBackStack() }) }
    screen<TitleDetailRoute>(pushed = true) {
        TitleDetailScreen(
            model,
            onBack = { nav.popBackStack() },
            onPerson = { nav.navigate(PersonRoute(it.id, it.name)) },
        )
    }
    screen<PersonRoute>(pushed = true) {
        PersonScreen(
            model,
            onBack = { nav.popBackStack() },
            onTitle = { nav.navigate(it) },
            onOpenList = { nav.navigate(WatchlistRoute(it)) },
        )
    }
    screen<WatchlistRoute>(pushed = true) {
        WatchlistScreen(
            model,
            onBack = { nav.popBackStack() },
            onTitle = { nav.navigate(it) },
            bottomInset = PaddingValues(bottom = if (model.queue != null) UP_NEXT_HEIGHT else 0.dp),
        )
    }
}

private fun NavGraphBuilder.setupGraph(model: GuideViewModel, nav: NavHostController, tv: app.oslo.repertoire.remote.TvStatus) {
    val finish = {
        model.finishSetup()
        // Started from Settings: return there. First run: replace setup with the main graph.
        val fromMain = runCatching { nav.getBackStackEntry<MainGraph>() }.isSuccess
        if (fromMain) {
            nav.popBackStack<SetupGraph>(inclusive = true)
        } else {
            nav.navigate(MainGraph) { popUpTo<SetupGraph> { inclusive = true } }
        }
    }
    navigation<SetupGraph>(startDestination = SetupWelcomeRoute) {
        screen<SetupWelcomeRoute> { SetupWelcomeScreen(onStart = { nav.navigate(SetupPortalRoute) }, onSkip = { finish() }) }
        screen<SetupPortalRoute>(pushed = true) {
            SetupPortalScreen(model, onBack = { nav.popBackStack() }, onNext = { nav.navigate(SetupTvRoute) { launchSingleTop = true } })
        }
        screen<SetupTvRoute>(pushed = true) {
            SetupTvScreen(model, tv, onGuide = { nav.navigate(TvGuideRoute(it)) }, onBack = { nav.popBackStack() }, onNext = {
                if (model.hasTv) nav.navigate(SetupConnectRoute) else nav.navigate(SetupDoneRoute)
            })
        }
        screen<SetupConnectRoute>(pushed = true) {
            SetupConnectScreen(model, tv, onGuide = { nav.navigate(TvGuideRoute(it)) }, onBack = { nav.popBackStack() }, onNext = { nav.navigate(SetupDoneRoute) })
        }
        screen<SetupDoneRoute>(pushed = true) { SetupDoneScreen(model, onFinish = { finish() }) }
    }
}

private val UP_NEXT_HEIGHT = 80.dp

/**
 * Persistent Up next control while a queue plays (Play all, a watchlist): what's on, what's
 * next, and Next to tune it. Hidden while a title is being tuned (the tune card shows instead).
 */
@Composable
private fun UpNextBar(model: GuideViewModel, modifier: Modifier = Modifier) {
    val queue = model.queue
    AnimatedVisibility(
        visible = queue != null && model.tune == null,
        modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        val shown = queue ?: return@AnimatedVisibility
        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(
                        shown.current.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val next = shown.next
                    Text(
                        if (next != null) "Up next: ${next.title}" + (if (shown.remaining > 1) " · ${shown.remaining} left" else "") else "Last in ${shown.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.FilledTonalButton(onClick = model::queueNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (shown.next != null) "Next" else "Done")
                }
                androidx.compose.material3.IconButton(onClick = model::stopQueue) {
                    Icon(Icons.Rounded.Close, contentDescription = "Stop ${shown.title}")
                }
            }
        }
    }
}

/** Floating frosted card (Up next, tuning), with a hairline edge and soft shadow for depth. */
@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    androidx.compose.foundation.layout.Box(
        modifier
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .glass()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
    ) { content() }
}

/** Progress while a title is being tuned on the TV. */
@Composable
private fun TuneCard(model: GuideViewModel, modifier: Modifier = Modifier) {
    val tune = model.tune
    AnimatedVisibility(
        visible = tune != null,
        modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        val shown = tune ?: return@AnimatedVisibility
        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(
                        "Opening ${splitDisplayName(shown.name).title}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        shown.step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
