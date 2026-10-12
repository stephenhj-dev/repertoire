package app.oslo.repertoire.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.GuideRow
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.ui.components.WatchedBadge
import app.oslo.repertoire.ui.components.PosterCard
import app.oslo.repertoire.ui.components.SectionHeader
import app.oslo.repertoire.ui.components.VersionChooserSheet
import app.oslo.repertoire.ui.components.sharedElement

/** True when [rows] have movies or series worth showing as posters. */
fun hasPosterRows(rows: List<GuideRow>): Boolean = rows.any { it is GuideRow.Title && it.kind != "live" }

/**
 * Guide or search rows in a poster grid: movies and series are poster cards (tap for details,
 * long press to choose a version and play); channels, folders and headers span the full width
 * and keep their row layout.
 */
internal fun LazyGridScope.guideCells(
    rows: List<GuideRow>,
    model: GuideViewModel,
    onTitle: (GuideRow.Title) -> Unit,
    onBucket: (GuideRow.Bucket) -> Unit,
    onMore: (GuideRow.More) -> Unit,
    onDetails: (GuideRow.Title) -> Unit,
) {
    itemsIndexed(
        rows,
        key = { index, row -> rowKey(index, row) },
        span = { _, row -> if (row is GuideRow.Title && row.kind != "live") GridItemSpan(1) else GridItemSpan(maxLineSpan) },
        contentType = { _, row -> if (row is GuideRow.Title && row.kind != "live") "poster" else row::class.simpleName },
    ) { _, row ->
        when {
            row is GuideRow.Title && row.kind != "live" -> PosterCell(row, model, onDetails)
            row is GuideRow.Title -> TitleListItem(
                row = row,
                favorite = model.isFavorite(row.kind, row.name),
                tuning = model.isTuning(row.playable),
                onPlay = { onTitle(row) },
                onFavorite = { model.toggleFavorite(row.kind, row.name) },
                onDetails = null,
            )
            row is GuideRow.Bucket -> BucketListItem(row, onClick = { onBucket(row) })
            row is GuideRow.Banner -> SectionHeader(row.name)
            row is GuideRow.More -> MoreItem(row.label) { onMore(row) }
            row is GuideRow.Note -> Text(
                row.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun PosterCell(row: GuideRow.Title, model: GuideViewModel, onDetails: (GuideRow.Title) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val favorite = model.isFavorite(row.kind, row.name)
    val tuning = model.isTuning(row.playable)
    PosterCard(
        url = row.icon,
        title = row.display,
        meta = row.year?.toString(),
        accent = listOfNotNull(row.badges.takeIf { it.isNotEmpty() }, row.moreVersions).joinToString("  ").ifEmpty { null },
        artModifier = Modifier.sharedElement("art-${row.kind}-${row.id}"),
        onClickLabel = "Details",
        onLongClick = { choosing = true },
        onClick = { onDetails(row) },
    ) {
        if (favorite) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(3.dp),
            )
        }
        WatchedBadge(model.isWatched(row), alignment = Alignment.TopStart)
        if (tuning) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
    }
    if (choosing) {
        VersionChooserSheet(
            title = row.display,
            versions = model.orderVersions(row.playable),
            tuningName = model.tune?.name,
            enabled = model.tune == null,
            onPlay = { model.play(it.name) },
            onDismiss = { choosing = false },
            watched = model.isWatched(row),
            onToggleWatched = { model.toggleWatched(row) },
        )
    }
}
