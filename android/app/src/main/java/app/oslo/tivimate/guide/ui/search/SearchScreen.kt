package app.oslo.tivimate.guide.ui.search

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import app.oslo.tivimate.guide.GuideRow
import app.oslo.tivimate.guide.GuideViewModel
import app.oslo.tivimate.guide.SearchScope
import app.oslo.tivimate.guide.Section
import app.oslo.tivimate.guide.ui.components.EmptyState
import app.oslo.tivimate.guide.ui.components.SectionHeader
import app.oslo.tivimate.guide.ui.components.SkeletonList
import app.oslo.tivimate.guide.ui.guide.guideRows

private enum class SearchPane { Start, Loading, Empty, Results }

@Composable
fun SearchScreen(
    model: GuideViewModel,
    onDetails: (GuideRow.Title) -> Unit,
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
            placeholder = { Text("Channels, movies, shows") },
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
        }
        Box(Modifier.fillMaxWidth()) {
            if (model.searchBusy && model.searchResults.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        }
        val pane = when {
            model.searchText.trim().length < 2 -> SearchPane.Start
            model.searchResults.isEmpty() && model.searchBusy -> SearchPane.Loading
            model.searchResults.isEmpty() -> SearchPane.Empty
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
                SearchPane.Results -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomInset.calculateBottomPadding() + 16.dp),
                ) {
                    guideRows(
                        rows = model.searchResults,
                        model = model,
                        onTitle = {
                            model.rememberSearch()
                            keyboard?.hide()
                            model.play(it.name)
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
                        onDetails = onDetails,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartPane(model: GuideViewModel, bottomInset: PaddingValues) {
    if (model.recentSearches.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Rounded.ManageSearch,
            title = "Find something to watch",
            body = "Search every channel, movie and show in your portal. Tap a result to play it on the TV.",
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
