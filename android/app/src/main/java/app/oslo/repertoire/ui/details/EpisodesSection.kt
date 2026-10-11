package app.oslo.repertoire.ui.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.series.Episode
import app.oslo.repertoire.series.EpisodeAction
import app.oslo.repertoire.series.Season
import app.oslo.repertoire.ui.components.PosterArt
import app.oslo.repertoire.ui.components.rememberHaptics
import app.oslo.repertoire.ui.theme.Brand
import app.oslo.repertoire.ui.theme.Motion
import app.oslo.repertoire.ui.theme.OverlineStyle
import app.oslo.repertoire.ui.theme.Space

/** "Resume S2 E5" with the episode name under it, and "Next episode" beside it when there is one. */
@Composable
fun EpisodeActions(
    action: EpisodeAction,
    deviceName: String,
    tuning: Boolean,
    enabled: Boolean,
    onPlay: (Episode) -> Unit,
) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth()) {
        Button(
            onClick = { haptics.confirm(); onPlay(action.episode) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            if (tuning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text("${action.label} on $deviceName", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                action.episode.title?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val next = action.next
        if (next != null && action.label.startsWith("Resume")) {
            Spacer(Modifier.height(Space.s))
            FilledTonalButton(
                onClick = { haptics.confirm(); onPlay(next) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Next episode · ${next.code}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Season pills; the selected one fills with the brand amber. */
@Composable
fun SeasonPicker(seasons: List<Season>, selected: Int?, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val state = rememberLazyListState()
    LaunchedEffect(seasons.size) {
        val index = seasons.indexOfFirst { it.number == selected }
        if (index > 0) state.scrollToItem((index - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = Space.page),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        items(seasons, key = { it.number }) { season ->
            val on = season.number == selected
            val container by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = Motion.gentle(),
                label = "season",
            )
            Surface(
                onClick = { haptics.tap(); onSelect(season.number) },
                shape = CircleShape,
                color = container,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { this.selected = on },
            ) {
                Text(
                    if (season.isSpecials) "Specials" else "Season ${season.number}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/**
 * The season's episodes as a horizontal shelf of 16:9 stills (Apple TV style), opening on the
 * last watched one. Tap plays that episode on the TV.
 */
@Composable
fun EpisodeShelf(
    episodes: List<Episode>,
    lastWatched: Pair<Int, Int>?,
    tuning: Pair<Int, Int>?,
    enabled: Boolean,
    onPlay: (Episode) -> Unit,
) {
    val state = rememberLazyListState()
    val season = episodes.firstOrNull()?.season
    LaunchedEffect(season, episodes.size) {
        val index = episodes.indexOfFirst { (it.season to it.number) == lastWatched }
        state.scrollToItem(if (index > 0) index else 0)
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = Space.page),
        horizontalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        items(episodes, key = { "${it.season}-${it.number}" }) { episode ->
            EpisodeCard(
                episode,
                lastWatched = (episode.season to episode.number) == lastWatched,
                tuning = (episode.season to episode.number) == tuning,
                enabled = enabled,
                onPlay = { onPlay(episode) },
            )
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, lastWatched: Boolean, tuning: Boolean, enabled: Boolean, onPlay: () -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.snappy(), label = "press")
    val playable = episode.inCatalog
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(264.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (playable) 1f else 0.5f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && playable,
                role = Role.Button,
                onClickLabel = "Play ${episode.code}",
                onClick = { haptics.confirm(); onPlay() },
            )
            .semantics {
                contentDescription = buildString {
                    append("Episode ${episode.number}")
                    episode.title?.let { append(", $it") }
                    if (lastWatched) append(", last watched")
                    if (!playable) append(", not on your provider")
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(if (lastWatched) Modifier.border(2.dp, Brand.Amber, shape) else Modifier),
        ) {
            PosterArt(episode.still, episode.title ?: "E${episode.number}", Modifier.fillMaxSize(), shape = shape)
            // Quiet scrim so the number and play glyph read on bright stills.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)),
                    shape,
                ),
            )
            Text(
                "E${episode.number}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(Space.m),
            )
            if (lastWatched) {
                Surface(
                    color = Brand.Amber,
                    contentColor = Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopStart).padding(Space.s),
                ) {
                    Text("LAST WATCHED", style = OverlineStyle, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Space.s)
                    .size(34.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (tuning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(
            listOfNotNull(
                "EPISODE ${episode.number}",
                episode.runtimeMinutes?.let { if (it >= 60) "${it / 60}H ${it % 60}M" else "${it}M" },
                if (!playable) "NOT ON YOUR PROVIDER" else null,
            ).joinToString("  ·  "),
            style = OverlineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            episode.title ?: "Episode ${episode.number}",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        episode.overview?.let {
            Spacer(Modifier.height(Space.xxs))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Placeholder shelf while the episode list loads. */
@Composable
fun EpisodeShelfSkeleton() {
    Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
        repeat(2) {
            Column(Modifier.width(264.dp)) {
                PosterArt(null, "", Modifier.fillMaxWidth().aspectRatio(16f / 9f).alpha(0.4f), shape = RoundedCornerShape(12.dp))
            }
        }
    }
}
