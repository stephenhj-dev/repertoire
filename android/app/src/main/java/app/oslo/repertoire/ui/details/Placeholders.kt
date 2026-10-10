package app.oslo.repertoire.ui.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.oslo.repertoire.ui.components.EmptyState

/** Actor/director filmography (TMDB). Placeholder. */
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
