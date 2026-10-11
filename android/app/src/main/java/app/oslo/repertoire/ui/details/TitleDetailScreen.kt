package app.oslo.repertoire.ui.details

import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.navigation.TitleDetailRoute
import app.oslo.repertoire.library.TitleDisplay
import app.oslo.repertoire.ui.watchlists.AddToWatchlistSheet
import app.oslo.repertoire.watchlist.TitleRef
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.oslo.repertoire.ui.components.VersionRow
import app.oslo.repertoire.ui.components.VersionInfoLine
import app.oslo.repertoire.tmdb.TitleNormalizer
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.tmdb.TmdbTitleDetail
import app.oslo.repertoire.tmdb.pickTrailer
import app.oslo.repertoire.ui.components.MessageCard
import app.oslo.repertoire.ui.components.MessageTone
import app.oslo.repertoire.ui.components.SectionHeader
import app.oslo.repertoire.ui.components.sharedElement
import app.oslo.repertoire.ui.theme.Brand

/** A person linked from a title page (director, creator or cast member). */
data class PersonLink(val id: Int, val name: String)

/**
 * Movie/series page: TMDB backdrop, poster, facts, overview, director/creators and cast, plus
 * Play on TV when the title is in the Xtream catalog. Without a TMDB token it shows the catalog
 * title with Play and Favorite only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleDetailScreen(
    model: GuideViewModel,
    onBack: () -> Unit,
    onPerson: (PersonLink) -> Unit,
    page: TitleDetailViewModel = viewModel(),
) {
    val route = page.route
    val state = page.state
    val detail = state.detail
    val context = LocalContext.current
    val parsed = TitleNormalizer.parse(route.name)
    val title = detail?.displayTitle ?: TitleDisplay.cleanName(route.name)
    val favoriteName = if (route.catalogId > 0) route.name else state.versions.firstOrNull()

    var saving by remember { mutableStateOf(false) }
    if (saving) {
        val ref = if (detail != null) {
            TitleRef(route.kind, detail.id, detail.displayTitle, detail.year, detail.posterPath)
        } else {
            TitleRef(route.kind, 0, title, parsed.year, null, catalogName = state.versions.firstOrNull() ?: route.name)
        }
        AddToWatchlistSheet(model, ref, onDismiss = { saving = false })
    }

    val listState = rememberLazyListState()
    val pageGlass = remember { HazeState() }
    val solid by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 640 } }
    val scrolled = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f }
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().hazeSource(pageGlass)) {
        BackdropAura(
            TmdbImages.backdrop(detail?.backdropPath),
            Modifier.graphicsLayer { translationY = -scrolled() * 0.6f },
        )
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp + if (model.queue != null) 88.dp else 0.dp)) {
            item(key = "header") {
                Header(route, detail, title, parsed.year, scrolled)
            }
            if (detail != null && detail.genres.isNotEmpty()) {
                item(key = "genres") {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { detail.genres.forEach { Pill(it.name) } }
                }
            }
            item(key = "actions") {
                Actions(
                    model = model,
                    page = page,
                    state = state,
                    trailerKey = detail?.videos?.results?.let(::pickTrailer)?.key,
                    favorite = favoriteName?.let { model.isFavorite(route.kind, it) },
                    onFavorite = { favoriteName?.let { model.toggleFavorite(route.kind, it) } },
                    onTrailer = { key ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")))
                        }
                    },
                    onSave = if (!state.loading || detail != null) ({ saving = true }) else null,
                )
            }
            if (Tmdb.enabled) {
                when {
                    state.loading && detail == null -> item(key = "loading") {
                        TextSkeleton(lines = 4, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                    }
                    state.error != null -> item(key = "error") {
                        MessageCard(
                            state.error,
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            action = "Retry",
                            onAction = page::load,
                        )
                    }
                    state.notFound -> item(key = "not-found") {
                        MessageCard(
                            "TMDB has no confident match for “${parsed.title}”${parsed.year?.let { " ($it)" } ?: ""}, so cast and synopsis aren't shown.",
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            tone = MessageTone.Info,
                            title = "No details found",
                        )
                    }
                }
            }
            if (detail != null) {
                detailSections(detail, route.kind, onPerson)
                item(key = "footnote") { TmdbFootnote() }
            }
        }
      }
        // Round back button over the artwork; frosts into a titled bar once the hero scrolls away.
        HeroTopBar(title, solid, onBack, pageGlass)
    }
}

@Composable
private fun Header(route: TitleDetailRoute, detail: TmdbTitleDetail?, title: String, fallbackYear: Int?, scrolled: () -> Float) {
    Box(Modifier.fillMaxWidth().height(430.dp)) {
        BackdropImage(TmdbImages.backdrop(detail?.backdropPath), Modifier.fillMaxWidth().height(340.dp), scrolled)
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val poster = TmdbImages.poster(detail?.posterPath) ?: route.icon
            PosterImage(
                url = poster,
                title = title,
                width = 124.dp,
                height = 186.dp,
                corner = 12.dp,
                modifier = Modifier
                    .shadow(24.dp, androidx.compose.foundation.shape.RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .then(if (route.catalogId > 0) Modifier.sharedElement("art-${route.kind}-${route.catalogId}") else Modifier),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                val original = detail?.displayOriginal?.takeIf { it != title && it.isNotBlank() }
                if (original != null) {
                    Text(original, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (route.kind == "show") Icons.Rounded.Tv else Icons.Rounded.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        factsLine(route.kind, detail, fallbackYear),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (detail != null && detail.voteCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = Brand.Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("%.1f".format(detail.voteAverage), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "  ·  %,d votes".format(detail.voteCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        TmdbMark(height = 14.dp)
                    }
                }
            }
        }
    }
}

private fun factsLine(kind: String, detail: TmdbTitleDetail?, fallbackYear: Int?): String {
    val parts = mutableListOf<String>()
    if (detail == null) {
        parts += if (kind == "show") "Series" else "Movie"
        fallbackYear?.let { parts += it.toString() }
        return parts.joinToString(" · ")
    }
    if (kind == "show") {
        val start = detail.year
        val end = TitleNormalizer.yearOf(detail.lastAirDate)
        val ended = detail.status == "Ended" || detail.status == "Canceled"
        if (start != null) parts += if (ended && end != null && end != start) "$start–$end" else if (!ended) "$start–" else "$start"
        detail.numberOfSeasons?.let { parts += if (it == 1) "1 season" else "$it seasons" }
        detail.episodeRunTime.firstOrNull()?.takeIf { it > 0 }?.let { parts += "${it}m episodes" }
    } else {
        detail.year?.let { parts += it.toString() }
        detail.runtime?.takeIf { it > 0 }?.let { parts += if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }
    }
    return parts.joinToString(" · ").ifEmpty { if (kind == "show") "Series" else "Movie" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(
    model: GuideViewModel,
    page: TitleDetailViewModel,
    state: TitleDetailState,
    trailerKey: String?,
    favorite: Boolean?,
    onFavorite: () -> Unit,
    onTrailer: (String) -> Unit,
    onSave: (() -> Unit)?,
) {
    // Parsing names is cheap (and cached), but only redo it when the names or preferences change.
    val prefs = model.playbackPrefs
    val versions = remember(state.versions, prefs) { model.orderVersions(state.versions) }
    val best = versions.firstOrNull()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (best != null) {
            val tuning = model.isTuning(state.versions)
            Button(
                onClick = { model.play(best.name) },
                enabled = model.tune == null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                if (tuning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Play on ${model.deviceName}", style = MaterialTheme.typography.titleMedium)
                    if (versions.size > 1 && best.tags.label.isNotEmpty()) {
                        Text(best.tags.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else if (state.loading && Tmdb.enabled) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (!state.loading && Tmdb.enabled) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Not in your IPTV catalog",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (trailerKey != null || favorite != null || onSave != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (trailerKey != null) {
                    FilledTonalButton(onClick = { onTrailer(trailerKey) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Rounded.SmartDisplay, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Trailer")
                    }
                }
                if (favorite != null) {
                    if (trailerKey == null) {
                        OutlinedButton(onClick = onFavorite, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (favorite) "In favorites" else "Add to favorites")
                        }
                    } else {
                        FilledTonalIconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                    }
                }
                if (onSave != null) {
                    if (trailerKey == null && favorite == null) {
                        OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save to a list")
                        }
                    } else {
                        FilledTonalIconButton(onClick = onSave, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Save to a watchlist")
                        }
                    }
                }
            }
        }
        if (best != null) {
            Spacer(Modifier.height(10.dp))
            ProviderName(best.name)
        }
        val others = versions.size - 1
        if (!state.expanded) {
            if (others > 0 || (state.detail != null && best != null)) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.TextButton(onClick = page::expand, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (others > 0) "More versions ($others)" else "Look for other versions")
                }
            }
        } else {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (versions.size == 1) "1 version" else "${versions.size} versions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.searchingMore) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Looking for more…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(2.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column {
                    versions.forEach { version ->
                        VersionRow(
                            version = version,
                            best = version == best,
                            tuning = model.tune?.name == version.name,
                            enabled = model.tune == null,
                            onPlay = { model.play(version.name) },
                            info = when (val info = state.info[version.name]) {
                                null -> null
                                VersionInfo.Loading -> VersionInfoLine.Loading
                                VersionInfo.Failed -> VersionInfoLine.Text("Couldn't reach the portal. Tap ⓘ to retry.")
                                is VersionInfo.Loaded -> VersionInfoLine.Text(info.detail?.line?.ifEmpty { null } ?: "The portal has no stream details")
                            },
                            onInfo = { page.loadInfo(version.name) },
                        )
                    }
                }
            }
        }
    }
}

/** The raw catalog name, which is what TiviMate is searched for. */
@Composable
private fun ProviderName(name: String) {
    Text(
        "Provider name: $name",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.detailSections(
    detail: TmdbTitleDetail,
    kind: String,
    onPerson: (PersonLink) -> Unit,
) {
    val overview = detail.overview?.takeIf { it.isNotBlank() }
    if (overview != null || !detail.tagline.isNullOrBlank()) {
        item(key = "overview") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                detail.tagline?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                overview?.let { ExpandableText(it) }
            }
        }
    }
    val makers: List<PersonLink> = if (kind == "show") {
        detail.createdBy.map { PersonLink(it.id, it.name) }
    } else {
        detail.credits?.crew.orEmpty().filter { it.job == "Director" }.map { PersonLink(it.id, it.name) }
    }.distinctBy { it.id }
    if (makers.isNotEmpty()) {
        item(key = "makers") {
            Column(Modifier.padding(top = 8.dp)) {
                SectionHeader(
                    when {
                        kind == "show" && makers.size > 1 -> "Creators"
                        kind == "show" -> "Creator"
                        makers.size > 1 -> "Directors"
                        else -> "Director"
                    },
                )
                FlowRowOfPeople(makers, onPerson)
            }
        }
    }
    val cast = detail.credits?.cast.orEmpty().sortedBy { it.order }.distinctBy { it.id }.take(20)
    if (cast.isNotEmpty()) {
        item(key = "cast") {
            Column(Modifier.padding(top = 8.dp)) {
                SectionHeader("Cast")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(cast, key = { it.id }) { member ->
                        Column(
                            Modifier
                                .width(96.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button, onClickLabel = "Open ${member.name}") { onPerson(PersonLink(member.id, member.name)) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ProfileImage(TmdbImages.profile(member.profilePath), size = 76.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(member.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                            member.character?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOfPeople(people: List<PersonLink>, onPerson: (PersonLink) -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        people.forEach { person ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onPerson(person) },
            ) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
