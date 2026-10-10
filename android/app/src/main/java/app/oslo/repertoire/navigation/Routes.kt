package app.oslo.repertoire.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

/** Graph holding the four bottom-bar destinations. */
@Serializable data object MainGraph
@Serializable data object RemoteRoute
@Serializable data object GuideRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute

/** First-run flow: portal login -> find TV -> connect/pair -> done. */
@Serializable data object SetupGraph
@Serializable data object SetupWelcomeRoute
@Serializable data object SetupPortalRoute
@Serializable data object SetupTvRoute
@Serializable data object SetupConnectRoute
@Serializable data object SetupDoneRoute

/** Pushed screens. */
@Serializable data object DevicesRoute
@Serializable data object PortalRoute

/*
 * Extension points for the TMDB features. The routes and placeholder screens are wired up so the
 * real pages can drop in without touching navigation:
 *  - TitleDetailRoute: movie/show page (poster, cast, director, ratings). tmdbId is filled in once
 *    titles are matched against TMDB; until then kind + name identify the catalog title.
 *  - PersonRoute: actor/director filmography.
 *  - WatchlistRoute: saved titles to watch later.
 */
@Serializable
data class TitleDetailRoute(
    val kind: String,
    val name: String,
    val catalogId: Long = 0,
    val icon: String? = null,
    val tmdbId: Int? = null,
)

@Serializable data class PersonRoute(val tmdbPersonId: Int, val name: String = "")
@Serializable data object WatchlistRoute

enum class TopLevel(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Remote(RemoteRoute, "Remote", Icons.Outlined.SettingsRemote, Icons.Rounded.SettingsRemote),
    Guide(GuideRoute, "Guide", Icons.Outlined.LiveTv, Icons.Rounded.LiveTv),
    Search(SearchRoute, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Settings(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}
