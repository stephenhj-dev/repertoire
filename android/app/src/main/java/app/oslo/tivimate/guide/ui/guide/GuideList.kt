package app.oslo.tivimate.guide.ui.guide

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import app.oslo.tivimate.guide.GuideRow
import app.oslo.tivimate.guide.GuideViewModel
import app.oslo.tivimate.guide.ui.components.SectionHeader

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
            is GuideRow.Title -> TitleListItem(
                row = row,
                favorite = model.isFavorite(row.kind, row.name),
                tuning = model.tune?.name == row.name,
                onPlay = { onTitle(row) },
                onFavorite = { model.toggleFavorite(row.kind, row.name) },
                onDetails = if (row.kind != "live") ({ onDetails(row) }) else null,
                modifier = Modifier.animateItem(),
            )
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
