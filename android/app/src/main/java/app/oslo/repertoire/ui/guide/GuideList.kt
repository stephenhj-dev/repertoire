package app.oslo.repertoire.ui.guide

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import app.oslo.repertoire.GuideRow
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.ui.components.SectionHeader
import app.oslo.repertoire.ui.components.VersionChooserSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Stable keys: catalog ids for titles, ids for buckets; banners/notes fall back to position. */
internal fun rowKey(index: Int, row: GuideRow): String = when (row) {
    is GuideRow.Title -> "t${row.id}:${row.kind}:${row.name}"
    is GuideRow.Bucket -> "b${row.kind}:${row.id}:${row.label}"
    is GuideRow.Banner -> "n$index:${row.name}"
    is GuideRow.More -> "m${row.section}:${row.label}"
    is GuideRow.Note -> "note$index"
}

private fun rowType(row: GuideRow): Int = when (row) {
    is GuideRow.Title -> 0
    is GuideRow.Bucket -> 1
    is GuideRow.Banner -> 2
    is GuideRow.More -> 3
    is GuideRow.Note -> 4
}

/** Renders guide or search rows into a LazyColumn. */
internal fun LazyListScope.guideRows(
    rows: List<GuideRow>,
    model: GuideViewModel,
    onTitle: (GuideRow.Title) -> Unit,
    onBucket: (GuideRow.Bucket) -> Unit,
    onMore: (GuideRow.More) -> Unit,
    onDetails: (GuideRow.Title) -> Unit,
) {
    itemsIndexed(rows, key = { index, row -> rowKey(index, row) }, contentType = { _, row -> rowType(row) }) { _, row ->
        when (row) {
            is GuideRow.Title -> {
                var choosing by remember { mutableStateOf(false) }
                TitleListItem(
                    row = row,
                    favorite = model.isFavorite(row.kind, row.name),
                    tuning = model.isTuning(row.playable),
                    onPlay = { onTitle(row) },
                    onFavorite = { model.toggleFavorite(row.kind, row.name) },
                    onDetails = if (row.kind != "live") ({ onDetails(row) }) else null,
                    modifier = Modifier.animateItem(),
                    onChooseVersion = if (row.kind != "live") ({ choosing = true }) else null,
                    watched = model.isWatched(row),
                    onToggleWatched = if (row.kind != "live") ({ model.toggleWatched(row) }) else null,
                )
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
            is GuideRow.Bucket -> BucketListItem(row, onClick = { onBucket(row) }, modifier = Modifier.animateItem())
            is GuideRow.Banner -> SectionHeader(row.name)
            is GuideRow.More -> MoreItem(row.label) { onMore(row) }
            is GuideRow.Note -> Text(
                row.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}
