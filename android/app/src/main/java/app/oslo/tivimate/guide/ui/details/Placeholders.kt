package app.oslo.tivimate.guide.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.oslo.tivimate.guide.GuideViewModel
import app.oslo.tivimate.guide.navigation.TitleDetailRoute
import app.oslo.tivimate.guide.ui.components.ChannelLogo
import app.oslo.tivimate.guide.ui.components.EmptyState
import app.oslo.tivimate.guide.ui.components.MessageCard
import app.oslo.tivimate.guide.ui.components.MessageTone
import app.oslo.tivimate.guide.ui.components.sharedElement

/**
 * Movie/show page. For now it shows the catalog title with Play and Favorite; the TMDB block
 * (poster, synopsis, cast with links to PersonRoute, director, ratings) slots in below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleDetailScreen(route: TitleDetailRoute, model: GuideViewModel, onBack: () -> Unit) {
    val favorite = model.isFavorite(route.kind, route.name)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ChannelLogo(
                url = route.icon,
                title = route.name,
                width = 160.dp,
                height = 240.dp,
                modifier = Modifier.sharedElement("art-${route.kind}-${route.catalogId}"),
            )
            Spacer(Modifier.height(20.dp))
            Text(route.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            AssistChip(onClick = {}, label = { Text(if (route.kind == "movie") "Movie" else "Series") })
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tuning = model.tune?.name == route.name
                Button(onClick = { model.play(route.name) }, enabled = !tuning, modifier = Modifier.height(52.dp)) {
                    if (tuning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play on ${model.deviceName}")
                }
                FilledTonalIconButton(onClick = { model.toggleFavorite(route.kind, route.name) }, modifier = Modifier.size(52.dp)) {
                    Icon(
                        if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            // TMDB extension point: replace this card with synopsis, ratings, director and a cast
            // row whose items navigate to PersonRoute(tmdbPersonId).
            MessageCard(
                text = "Cast, director, ratings and filmographies from TMDB are on the way.",
                title = "More about this title soon",
                tone = MessageTone.Info,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Actor/director filmography (TMDB). Placeholder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(name: String, onBack: () -> Unit) {
    ComingSoon(name.ifBlank { "Person" }, Icons.Rounded.Person, "Filmographies for actors and directors are coming soon.", onBack)
}

/** Saved titles to watch later. Placeholder. */
@Composable
fun WatchlistScreen(onBack: () -> Unit) {
    ComingSoon("Watchlist", Icons.Rounded.BookmarkBorder, "Save movies and shows to watch later. Until then, star them to keep them in Favorites.", onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoon(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, body: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
        )
        EmptyState(icon = icon, title = "Coming soon", body = body)
    }
}

