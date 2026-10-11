package app.oslo.repertoire.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.oslo.repertoire.tmdb.Tmdb
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.tmdb.TmdbPersonHit
import app.oslo.repertoire.ui.details.ProfileImage
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.foundation.layout.size
import app.oslo.repertoire.GuideRow
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.SearchScope
import app.oslo.repertoire.Section
import app.oslo.repertoire.ui.components.EmptyState
import app.oslo.repertoire.ui.components.SectionHeader
import app.oslo.repertoire.ui.components.SkeletonList
import app.oslo.repertoire.ui.guide.guideRows
import app.oslo.repertoire.ui.guide.guideCells
import app.oslo.repertoire.ui.guide.hasPosterRows
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

private enum class SearchPane { Start, Loading, Empty, Results }

@Composable
fun SearchScreen(
    model: GuideViewModel,
    onDetails: (GuideRow.Title) -> Unit,
    onPerson: (TmdbPersonHit) -> Unit,
    bottomInset: PaddingValues,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (model.searchText.isEmpty()) runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextField(
            value = model.searchText,
            onValueChange = model::onSearchText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .focusRequester(focus),
            placeholder = { Text(if (Tmdb.enabled) "Channels, movies, shows, people" else "Channels, movies, shows") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (model.searchText.isNotEmpty()) {
                    IconButton(onClick = { model.onSearchText(""); runCatching { focus.requestFocus() } }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                model.rememberSearch()
                keyboard?.hide()
                focusManager.clearFocus()
            }),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchScope.entries.forEach { scope ->
                FilterChip(
                    selected = model.searchScope == scope,
                    onClick = { model.selectScope(scope) },
                    label = { Text(scope.label) },
                )
            }
            if (hasPosterRows(model.searchResults)) {
                FilterChip(
                    selected = model.posterGrid,
                    onClick = { model.showPosterGrid(!model.posterGrid) },
                    label = { Text("Posters") },
                    leadingIcon = { Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        Box(Modifier.fillMaxWidth()) {
            if (model.searchBusy && model.searchResults.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }
        val pane = when {
            model.searchText.trim().length < 2 -> SearchPane.Start
            model.searchResults.isEmpty() && model.peopleResults.isEmpty() && model.searchBusy -> SearchPane.Loading
            model.searchResults.isEmpty() && model.peopleResults.isEmpty() -> SearchPane.Empty
            else -> SearchPane.Results
        }
        AnimatedContent(
            targetState = pane,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "search",
            modifier = Modifier.weight(1f),
        ) { shown ->
            when (shown) {
                SearchPane.Start -> StartPane(model, bottomInset)
                SearchPane.Loading -> SkeletonList(rows = 6)
                SearchPane.Empty -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No matches",
                    body = if (model.counts.total == 0) {
                        "The guide is empty. Add your portal or refresh the guide first."
                    } else {
                        "Nothing in ${model.searchScope.label.lowercase().takeIf { model.searchScope != SearchScope.All } ?: "your guide"} matches “${model.searchText.trim()}”. Try fewer letters."
                    },
                )
                SearchPane.Results -> if (model.posterGrid && hasPosterRows(model.searchResults)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomInset.calculateBottomPadding() + 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (model.peopleResults.isNotEmpty()) {
                            item(key = "people-header", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("People", Modifier.padding(start = 0.dp)) }
                            item(key = "people", span = { GridItemSpan(maxLineSpan) }) {
                                PeopleRow(model.peopleResults) { person ->
                                    model.rememberSearch()
                                    keyboard?.hide()
                                    onPerson(person)
                                }
                            }
                        }
                        guideCells(
                            rows = model.searchResults,
                            model = model,
                            onTitle = { model.rememberSearch(); keyboard?.hide(); model.playRow(it) },
                            onBucket = {},
                            onMore = { more -> model.selectScope(scopeFor(more.section)) },
                            onDetails = { model.rememberSearch(); keyboard?.hide(); onDetails(it) },
                        )
                    }
                } else LazyColumn(
                    Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(bottom = bottomInset.calculateBottomPadding() + 16.dp),
                ) {
                    if (model.peopleResults.isNotEmpty()) {
                        item(key = "people-header") { SectionHeader("People") }
                        item(key = "people") {
                            PeopleRow(model.peopleResults) { person ->
                                model.rememberSearch()
                                keyboard?.hide()
                                onPerson(person)
                            }
                        }
                    }
                    guideRows(
                        rows = model.searchResults,
                        model = model,
                        onTitle = {
                            model.rememberSearch()
                            keyboard?.hide()
                            model.playRow(it)
                        },
                        onBucket = {},
                        onMore = { more ->
                            model.selectScope(
                                when (more.section) {
                                    Section.Movies -> SearchScope.Movies
                                    Section.Series -> SearchScope.Shows
                                    else -> SearchScope.Live
                                },
                            )
                        },
                        onDetails = {
                            model.rememberSearch()
                            keyboard?.hide()
                            onDetails(it)
                        },
                    )
                }
            }
        }
    }
}

private fun scopeFor(section: Section): SearchScope = when (section) {
    Section.Movies -> SearchScope.Movies
    Section.Series -> SearchScope.Shows
    else -> SearchScope.Live
}

@Composable
private fun StartPane(model: GuideViewModel, bottomInset: PaddingValues) {
    if (model.recentSearches.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Rounded.ManageSearch,
            title = "Find something to watch",
            body = "Search every channel, movie and show in your portal. Tap a channel to play it on the TV, or a movie or show for details.",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomInset.calculateBottomPadding() + 16.dp)) {
        item(key = "recent-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader("Recent searches") }
                TextButton(onClick = model::clearRecentSearches, modifier = Modifier.padding(end = 8.dp, top = 8.dp)) {
                    Text("Clear")
                }
            }
        }
        items(model.recentSearches, key = { "recent:$it" }) { text ->
            ListItem(
                headlineContent = { Text(text) },
                leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
                modifier = Modifier.clickable(onClickLabel = "Search for $text") { model.onSearchText(text) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    }
}

/** TMDB people as a horizontal row of portraits; tapping one opens their page. */
@Composable
private fun PeopleRow(people: List<TmdbPersonHit>, onPerson: (TmdbPersonHit) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(people, key = { it.id }) { person ->
            Column(
                Modifier
                    .width(96.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button, onClickLabel = "Open ${person.name}") { onPerson(person) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileImage(TmdbImages.profile(person.profilePath), size = 68.dp)
                Spacer(Modifier.height(6.dp))
                Text(person.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                val known = when (person.knownForDepartment) {
                    "Acting" -> "Actor"
                    "Directing" -> "Director"
                    null -> null
                    else -> person.knownForDepartment
                }
                if (known != null) {
                    Text(known, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
