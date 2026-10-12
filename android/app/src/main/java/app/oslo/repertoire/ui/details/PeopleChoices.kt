package app.oslo.repertoire.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.ui.components.rememberHaptics
import app.oslo.repertoire.ui.theme.DisplayFamily
import app.oslo.repertoire.ui.theme.OverlineStyle
import app.oslo.repertoire.ui.theme.Space

/** A person on a title page who can be added to the Repertoire. */
data class PersonChoice(
    val id: Int,
    val name: String,
    /** "Director", "Creator", or the character played. */
    val role: String?,
    val profilePath: String?,
    /** TMDB department for the follow snapshot ("Directing", "Acting"). */
    val department: String?,
)

/** One person with a follow toggle; used in the Add sheet. */
@Composable
fun PersonChoiceRow(model: GuideViewModel, person: PersonChoice) {
    val followed = model.isFollowed(person.id)
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) {
                haptics.tap()
                if (followed) model.unfollow(person.id, person.name) else model.follow(person.id, person.name, person.profilePath, person.department)
            }
            .padding(horizontal = Space.xxl, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileImage(TmdbImages.profile(person.profilePath), size = 44.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            person.role?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            if (followed) Icons.Rounded.Check else Icons.Rounded.TheaterComedy,
            contentDescription = if (followed) "In your Repertoire" else "Add to Repertoire",
            tint = if (followed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tapping a director or actor on a title page: follow in one tap, or open their page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonQuickSheet(model: GuideViewModel, person: PersonChoice, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val followed = model.isFollowed(person.id)
    val haptics = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.xxl).padding(bottom = Space.xxl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileImage(TmdbImages.profile(person.profilePath), size = 72.dp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    person.role?.takeIf { it.isNotBlank() }?.let {
                        Text(it.uppercase(), style = OverlineStyle, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(person.name, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFamily))
                }
            }
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                if (followed) {
                    FilledTonalButton(onClick = { haptics.tap(); model.unfollow(person.id, person.name) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("In your Repertoire", maxLines = 1)
                    }
                } else {
                    Button(
                        onClick = {
                            haptics.confirm()
                            model.follow(person.id, person.name, person.profilePath, person.department)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Rounded.TheaterComedy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to Repertoire", maxLines = 1)
                    }
                }
                OutlinedButton(onClick = { onDismiss(); onOpen() }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Filmography", maxLines = 1)
                }
            }
        }
    }
}
