package app.oslo.repertoire.ui.details

import dev.chrisbanes.haze.hazeSource
import app.oslo.repertoire.tmdb.Filmography
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.navigation.TitleDetailRoute
import app.oslo.repertoire.tmdb.CreditGroup
import app.oslo.repertoire.tmdb.TitleNormalizer
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.tmdb.TmdbPerson
import app.oslo.repertoire.tmdb.catalogKind
import app.oslo.repertoire.ui.components.EmptyState
import app.oslo.repertoire.ui.components.MessageCard
import app.oslo.repertoire.ui.components.SectionHeader
import app.oslo.repertoire.ui.components.SkeletonList
import app.oslo.repertoire.ui.components.PlayButton
import app.oslo.repertoire.ui.components.VersionChooserSheet
import app.oslo.repertoire.library.Version
import app.oslo.repertoire.watchlist.QueueItem
import app.oslo.repertoire.watchlist.TitleRef
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.oslo.repertoire.library.VersionPicker
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Where a filmography entry leads: its title page, linked to the catalog when we have it. */
fun PersonCredit.detailRoute(): TitleDetailRoute {
    val best = library.firstOrNull()
    return TitleDetailRoute(
        kind = catalogKind(credit.mediaType),
        name = best?.name ?: credit.title,
        catalogId = best?.id ?: 0,
        icon = TmdbImages.poster(credit.posterPath) ?: best?.icon,
        tmdbId = credit.id,
    )
}

/**
 * Actor/director page: photo, facts, biography, best-known titles and the full filmography in
 * chronological order, split by department, with what's in the catalog marked and playable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    model: GuideViewModel,
    onBack: () -> Unit,
    onTitle: (TitleDetailRoute) -> Unit,
    onOpenList: (Long) -> Unit = {},
    page: PersonViewModel = viewModel(),
) {
    val state = page.state
    val person = state.person
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val pageGlass = remember { dev.chrisbanes.haze.HazeState() }
    val solid by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 560 } }
    val scrolled = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f }
    val backdrop = remember(person) {
        person?.combinedCredits?.let { Filmography.heroBackdrop(it, person.knownForDepartment) }?.let(TmdbImages::backdrop)
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(Modifier.hazeSourceCompat(pageGlass))) {
            BackdropAura(backdrop, Modifier.graphicsLayer { translationY = -scrolled() * 0.6f })
            when {
                state.error != null && person == null -> MessageCard(
                    state.error,
                    Modifier.statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp),
                    action = "Retry",
                    onAction = page::load,
                )
                person == null -> SkeletonList(Modifier.statusBarsPadding().padding(top = 64.dp), rows = 6, poster = true)
                else -> PersonContent(model, page, person, onTitle, onOpenList, listState, backdrop, scrolled)
            }
        }
        HeroTopBar(person?.name ?: page.route.name, solid || person == null, onBack, pageGlass)
    }
}

private fun Modifier.hazeSourceCompat(state: dev.chrisbanes.haze.HazeState): Modifier = hazeSource(state)

@Composable
private fun PersonContent(
    model: GuideViewModel,
    page: PersonViewModel,
    person: TmdbPerson,
    onTitle: (TitleDetailRoute) -> Unit,
    onOpenList: (Long) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    backdrop: String?,
    scrolled: () -> Float,
) {
    val state = page.state
    val group = page.group
    val visible = page.visible
    val inGroup = state.groups[group].orEmpty()
    val owned = inGroup.count { it.inLibrary }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") { PersonHeader(person, backdrop, scrolled) }
        val bio = person.biography?.takeIf { it.isNotBlank() }
        if (bio != null) {
            item(key = "bio") { ExpandableText(bio, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        }
        if (state.knownFor.isNotEmpty()) {
            item(key = "known-header") { SectionHeader("Known for") }
            item(key = "known") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.knownFor, key = { "${it.credit.mediaType}${it.credit.id}" }) { entry ->
                        Column(
                            Modifier
                                .width(104.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button, onClickLabel = "Open ${entry.credit.title}") { onTitle(entry.detailRoute()) }
                                .padding(4.dp),
                        ) {
                            Box {
                                PosterImage(TmdbImages.poster(entry.credit.posterPath), entry.credit.title, width = 96.dp, height = 144.dp, corner = 12.dp)
                                if (entry.inLibrary) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "In your library",
                                        tint = app.oslo.repertoire.ui.theme.LocalStatusColors.current.onSuccess,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .then(Modifier.background(app.oslo.repertoire.ui.theme.LocalStatusColors.current.success))
                                            .padding(3.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(entry.credit.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            entry.credit.year?.let {
                                Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (state.groups.isNotEmpty()) {
            item(key = "filmography-header") { SectionHeader("Filmography") }
            item(key = "controls") { Controls(page, owned, inGroup.size) }
            if (owned > 0) {
                item(key = "play-all") { PlayAllRow(model, page, person, onOpenList) }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.PersonSearch,
                        title = "Nothing in your library",
                        body = "None of ${person.name}'s ${group?.label?.lowercase() ?: ""} credits are in your IPTV catalog.",
                        action = "Show all",
                        onAction = { page.onlyLibrary = false },
                    )
                }
            }
            items(visible, key = { "${group}:${it.credit.mediaType}:${it.credit.id}" }) { entry ->
                CreditRow(model, entry, onOpen = { onTitle(entry.detailRoute()) })
            }
        }
        item(key = "footnote") { TmdbFootnote() }
    }
}

/** "Play all" and "Save as watchlist" for the selected group's titles in the library, oldest first. */
@Composable
private fun PlayAllRow(model: GuideViewModel, page: PersonViewModel, person: TmdbPerson, onOpenList: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val group = page.group
    val owned = page.libraryCredits()
    val name = "${person.name} · ${group?.label ?: "Films"}"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { model.startQueue(name, owned.map { QueueItem(it.credit.title, it.library.map { e -> e.name }) }) },
            enabled = model.tune == null,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play all ${owned.size}")
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val id = model.watchlists.create(
                        name,
                        owned.map { TitleRef(catalogKind(it.credit.mediaType), it.credit.id, it.credit.title, it.credit.year, it.credit.posterPath) },
                        personId = person.id,
                    )
                    model.notify("Saved ${owned.size} titles to $name")
                    onOpenList(id)
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save as list", maxLines = 1)
        }
    }
}

@Composable
private fun PersonHeader(person: TmdbPerson, backdrop: String?, scrolled: () -> Float) {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        BackdropImage(backdrop, Modifier.fillMaxWidth().height(300.dp), scrolled)
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Portrait with a thin amber ring, lifted off the backdrop.
            Box(
                Modifier
                    .shadow(20.dp, androidx.compose.foundation.shape.CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), androidx.compose.foundation.shape.CircleShape)
                    .padding(2.dp),
            ) {
                ProfileImage(TmdbImages.profile(person.profilePath), size = 116.dp)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
                person.knownForDepartment?.let {
                    Text(
                        (if (it == "Acting") "Actor" else if (it == "Directing") "Director" else it).uppercase(),
                        style = app.oslo.repertoire.ui.theme.OverlineStyle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(person.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                val born = TitleNormalizer.yearOf(person.birthday)
                val died = TitleNormalizer.yearOf(person.deathday)
                val life = when {
                    born != null && died != null -> "$born–$died"
                    born != null -> "Born $born"
                    else -> null
                }
                listOfNotNull(life, person.placeOfBirth?.takeIf { it.isNotBlank() }).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(page: PersonViewModel, owned: Int, total: Int) {
    val groups = page.state.groups
    Column {
        if (groups.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CreditGroup.entries.filter { it in groups }.forEach { g ->
                    FilterChip(
                        selected = page.group == g,
                        onClick = { page.group = g },
                        label = { Text("${g.label} ${groups[g]?.size ?: 0}") },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { page.newestFirst = !page.newestFirst },
                label = { Text(if (page.newestFirst) "Newest first" else "Oldest first") },
                leadingIcon = {
                    Icon(
                        if (page.newestFirst) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
            FilterChip(
                selected = page.onlyLibrary,
                onClick = { page.onlyLibrary = !page.onlyLibrary },
                enabled = owned > 0 || page.onlyLibrary,
                label = { Text("Only in my library · $owned of $total") },
                leadingIcon = if (page.onlyLibrary) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CreditRow(model: GuideViewModel, entry: PersonCredit, onOpen: () -> Unit) {
    val credit = entry.credit
    val best = entry.library.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Details", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            credit.year?.toString() ?: "—",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        Spacer(Modifier.width(8.dp))
        PosterImage(TmdbImages.poster(credit.posterPath), credit.title, width = 52.dp, height = 78.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(credit.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val role = buildList {
                if (credit.mediaType == "tv") add("Series")
                if (credit.roleLine.isNotBlank()) add(credit.roleLine)
                credit.episodeCount?.takeIf { credit.mediaType == "tv" && it > 0 }?.let { add(if (it == 1) "1 episode" else "$it episodes") }
            }.joinToString(" · ")
            if (role.isNotEmpty()) {
                Text(role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (entry.inLibrary) {
                Spacer(Modifier.height(6.dp))
                val badges = VersionPicker.summary(entry.library.map { Version(it.name) }, model.playbackPrefs.language).line
                LibraryBadge(label = if (badges.isEmpty()) "In your library" else "In your library · $badges")
            }
        }
        if (best != null) {
            Spacer(Modifier.width(8.dp))
            val names = entry.library.map { it.name }
            var choosing by remember { mutableStateOf(false) }
            PlayButton(
                tuning = model.isTuning(names),
                enabled = model.tune == null,
                label = "Play ${credit.title} on TV",
                onPlay = { model.playBest(names) },
                onLongPress = { choosing = true },
            )
            if (choosing) {
                VersionChooserSheet(
                    title = credit.title,
                    versions = model.orderVersions(names),
                    tuningName = model.tune?.name,
                    enabled = model.tune == null,
                    onPlay = { model.play(it.name) },
                    onDismiss = { choosing = false },
                )
            }
        }
    }
}
