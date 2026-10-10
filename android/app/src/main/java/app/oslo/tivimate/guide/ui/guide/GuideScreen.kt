package app.oslo.tivimate.guide.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oslo.tivimate.guide.GuideRow
import app.oslo.tivimate.guide.GuideView
import app.oslo.tivimate.guide.GuideViewModel
import app.oslo.tivimate.guide.Section
import app.oslo.tivimate.guide.ui.components.EmptyState
import app.oslo.tivimate.guide.ui.components.MessageCard
import app.oslo.tivimate.guide.ui.components.SkeletonList
import kotlinx.coroutines.delay

private val Tabs = listOf(
    Section.Tv to "Live TV",
    Section.Movies to "Movies",
    Section.Series to "Series",
    Section.Favorites to "Favorites",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    model: GuideViewModel,
    onOpenPortal: () -> Unit,
    onDetails: (GuideRow.Title) -> Unit,
    bottomInset: PaddingValues,
) {
    val view = model.view
    BackHandler(enabled = view.depth > 0) { model.back() }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
        TopAppBar(
            title = {
                AnimatedContent(view.title.takeIf { view.depth > 0 } ?: "Guide", label = "title") { title ->
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (view.depth == 0 && model.counts.total > 0) {
                            Text(
                                "%,d channels · %,d movies · %,d series".format(
                                    model.counts.live,
                                    model.counts.movies,
                                    model.counts.shows,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                if (view.depth > 0) {
                    IconButton(onClick = { model.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                if (model.hasPortal) {
                    IconButton(onClick = model::refresh, enabled = !model.syncing) {
                        if (model.syncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh guide")
                        }
                    }
                }
            },
            scrollBehavior = scroll,
        )
        AnimatedVisibility(model.syncing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                Text(
                    model.syncStep.ifBlank { "Updating the guide" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        model.syncError?.let { error ->
            MessageCard(
                text = error,
                title = "Couldn't update the guide",
                action = "Try again",
                onAction = model::refresh,
                secondary = "Edit portal",
                onSecondary = onOpenPortal,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AnimatedVisibility(view.depth == 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            PrimaryTabRow(selectedTabIndex = Tabs.indexOfFirst { it.first == model.section }.coerceAtLeast(0)) {
                Tabs.forEach { (section, label) ->
                    Tab(
                        selected = model.section == section,
                        onClick = { model.select(section) },
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }
        }
        AnimatedVisibility(view.depth > 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            FilterField(model)
        }
        AnimatedContent(
            targetState = view,
            contentKey = { it.key },
            transitionSpec = { guideTransition(initialState, targetState) },
            label = "guide",
            modifier = Modifier.weight(1f),
        ) { shown ->
            GuideList(shown, model, onOpenPortal, onDetails, bottomInset)
        }
    }
}

private fun guideTransition(from: GuideView, to: GuideView): ContentTransform {
    val duration = 260
    return when {
        to.depth > from.depth ->
            (slideInHorizontally(tween(duration)) { it / 4 } + fadeIn(tween(duration))) togetherWith
                (slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration / 2)))
        to.depth < from.depth ->
            (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration))) togetherWith
                (slideOutHorizontally(tween(duration)) { it / 4 } + fadeOut(tween(duration / 2)))
        else -> fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))
    }
}

@Composable
private fun FilterField(model: GuideViewModel) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = model.query,
        onValueChange = model::onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text("Filter this list") },
        leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
        trailingIcon = {
            if (model.query.isNotEmpty()) {
                IconButton(onClick = { model.onQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear filter") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

@Composable
private fun GuideList(
    view: GuideView,
    model: GuideViewModel,
    onOpenPortal: () -> Unit,
    onDetails: (GuideRow.Title) -> Unit,
    bottomInset: PaddingValues,
) {
    // Only show the skeleton when a load takes long enough to notice.
    val slow by produceState(false, model.loading) {
        value = false
        if (model.loading) {
            delay(150)
            value = true
        }
    }
    val titles = view.rows.filterNot { it is GuideRow.Note }
    when {
        titles.isEmpty() && (slow || (model.syncing && model.counts.total == 0)) ->
            if (model.syncing && model.counts.total == 0) {
                EmptyState(
                    icon = Icons.Rounded.CloudDownload,
                    title = "Downloading your guide",
                    body = "Channels, movies and series are being saved to this phone. This takes a minute the first time.",
                )
            } else {
                SkeletonList(poster = view.section == Section.Movies || view.section == Section.Series)
            }
        titles.isEmpty() && !model.hasPortal -> EmptyState(
            icon = Icons.Rounded.LiveTv,
            title = "Add your IPTV portal",
            body = "Sign in with the same Xtream login TiviMate uses. Your channels, movies and series show up here.",
            action = "Add portal",
            onAction = onOpenPortal,
        )
        titles.isEmpty() && view.filtering -> EmptyState(
            icon = Icons.Rounded.SearchOff,
            title = "No matches",
            body = "Nothing in ${view.title} matches “${model.query.trim()}”.",
            action = "Clear filter",
            onAction = { model.onQuery("") },
        )
        titles.isEmpty() && view.section == Section.Favorites -> EmptyState(
            icon = Icons.Rounded.StarBorder,
            title = "No favorites yet",
            body = "Tap the star next to any channel, movie or show to keep it here.",
        )
        titles.isEmpty() && !model.loading && model.counts.total == 0 && !model.syncing -> EmptyState(
            icon = Icons.Rounded.CloudDownload,
            title = "The guide is empty",
            body = "Download the catalog from your portal to start browsing.",
            action = "Refresh guide",
            onAction = model::refresh,
            secondary = "Edit portal",
            onSecondary = onOpenPortal,
        )
        else -> {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset.calculateBottomPadding() + 16.dp),
            ) {
                guideRows(
                    rows = view.rows,
                    model = model,
                    onTitle = { model.play(it.name) },
                    onBucket = { model.open(it) },
                    onMore = { model.open(it) },
                    onDetails = onDetails,
                )
                if (view.rows.isNotEmpty()) {
                    item(key = "load-more", contentType = "load-more") {
                        LaunchedEffect(view.rows.size, view.key) { model.loadMore() }
                    }
                }
            }
        }
    }
}
