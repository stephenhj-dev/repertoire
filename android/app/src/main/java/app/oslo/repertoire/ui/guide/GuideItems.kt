package app.oslo.repertoire.ui.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.BucketKind
import app.oslo.repertoire.GuideRow
import app.oslo.repertoire.data.splitDisplayName
import app.oslo.repertoire.ui.components.ChannelLogo
import app.oslo.repertoire.ui.components.CodeBadge
import app.oslo.repertoire.ui.components.IconBadge
import app.oslo.repertoire.ui.components.sharedElement

private fun countLabel(count: Int, kind: BucketKind): String {
    val noun = when (kind) {
        BucketKind.Category -> if (count == 1) "title" else "titles"
        else -> if (count == 1) "channel" else "channels"
    }
    return "%,d %s".format(count, noun)
}

@Composable
fun BucketListItem(row: GuideRow.Bucket, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClickLabel = "Open ${row.label}", onClick = onClick),
        headlineContent = { Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(countLabel(row.count, row.kind)) },
        leadingContent = {
            when (row.kind) {
                BucketKind.Country, BucketKind.Package -> CodeBadge(row.id)
                BucketKind.Section -> IconBadge(Icons.AutoMirrored.Rounded.ViewList)
                BucketKind.Category -> IconBadge(Icons.Rounded.Movie)
            }
        },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/**
 * A channel, movie or show. Tapping plays it on the TV; the star saves it; movies and shows
 * have an info button that opens the (upcoming) detail page.
 */
@Composable
fun TitleListItem(
    row: GuideRow.Title,
    favorite: Boolean,
    tuning: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val name = splitDisplayName(row.name, row.kind)
    val overline = listOfNotNull(name.tag, row.caption.takeIf { it.isNotBlank() }).joinToString(" · ")
    val poster = row.kind != "live"
    ListItem(
        modifier = modifier
            .clickable(onClickLabel = "Play on TV", role = Role.Button, onClick = onPlay)
            .semantics {
                if (onDetails != null) onClick(label = "Details") { onDetails(); true }
            },
        overlineContent = if (overline.isNotEmpty()) ({ Text(overline, maxLines = 1) }) else null,
        headlineContent = { Text(name.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            ChannelLogo(
                url = row.icon,
                title = name.title,
                width = 48.dp,
                height = if (poster) 64.dp else 48.dp,
                modifier = Modifier.sharedElement("art-${row.kind}-${row.id}"),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tuning) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.5.dp)
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDetails != null) {
                    IconButton(onClick = onDetails) {
                        Icon(Icons.Outlined.Info, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
fun MoreItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
        Text(label)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
}
