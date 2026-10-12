package app.oslo.repertoire.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

/** Graph holding the bottom-bar destinations. */
@Serializable data object MainGraph
@Serializable data object RemoteRoute
@Serializable data object GuideRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute

/** The people the user follows and their films. */
@Serializable data object RepertoireRoute

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

/** Step-by-step TV connection guide; [guideId] empty = the one suggested for the chosen TV. */
@Serializable data class TvGuideRoute(val guideId: String = "")

/*
 * Extension points for the TMDB features. The routes and placeholder screens are wired up so the
 * real pages can drop in without touching navigation:
 *  - TitleDetailRoute: movie/show page (poster, cast, director, ratings). tmdbId is filled in once
 *    titles are matched against TMDB; until then kind + name identify the catalog title.
 *  - PersonRoute: actor/director filmography.
 *  - WatchlistsRoute / WatchlistRoute: the user's lists of titles to watch in order.
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

/** The Lists tab and one list. */
@Serializable data object WatchlistsRoute
@Serializable data class WatchlistRoute(val listId: Long)

enum class TopLevel(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    // Settings moved to a gear on the tab pages so the bar keeps five destinations.
    Repertoire(RepertoireRoute, "Repertoire", Icons.Outlined.TheaterComedy, Icons.Rounded.TheaterComedy),
    Guide(GuideRoute, "Guide", Icons.Outlined.LiveTv, Icons.Rounded.LiveTv),
    Search(SearchRoute, "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Lists(WatchlistsRoute, "Lists", Icons.AutoMirrored.Outlined.PlaylistPlay, Icons.AutoMirrored.Rounded.PlaylistPlay),
    Remote(RemoteRoute, "Remote", Icons.Outlined.SettingsRemote, Icons.Rounded.SettingsRemote),
}
