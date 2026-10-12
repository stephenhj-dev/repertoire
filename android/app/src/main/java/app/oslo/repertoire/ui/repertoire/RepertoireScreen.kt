package app.oslo.repertoire.ui.repertoire

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oslo.repertoire.GuideViewModel
import app.oslo.repertoire.repertoire.WorkItem
import app.oslo.repertoire.tmdb.TmdbImages
import app.oslo.repertoire.ui.components.EmptyState
import app.oslo.repertoire.ui.components.PlayButton
import app.oslo.repertoire.ui.components.PosterArt
import app.oslo.repertoire.ui.components.rememberHaptics
import app.oslo.repertoire.ui.components.posterShimmer
import app.oslo.repertoire.ui.details.BackdropAura
import app.oslo.repertoire.ui.details.ProfileImage
import app.oslo.repertoire.ui.theme.Brand
import app.oslo.repertoire.ui.theme.DisplayFamily
import app.oslo.repertoire.ui.theme.Motion
import app.oslo.repertoire.ui.theme.OverlineStyle
import app.oslo.repertoire.ui.theme.Radii
import app.oslo.repertoire.ui.theme.Space
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * The people the user follows, each as a hero card: the backdrop of their best-known film,
 * portrait in a progress ring, serif name, "x of y watched", and the next film to play.
 */
@Composable
fun RepertoireScreen(
    model: GuideViewModel,
    onPerson: (Int, String) -> Unit,
    onFindPeople: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomInset: PaddingValues,
    page: RepertoireViewModel = viewModel(),
) {
    val cards = page.cards
    val listState = rememberLazyListState()
    val scrolled = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f }
    Box(Modifier.fillMaxSize()) {
        BackdropAura(
            cards.firstOrNull()?.row?.backdropPath?.let(TmdbImages::backdrop),
            Modifier.graphicsLayer { translationY = -scrolled() * 0.5f },
            height = 520.dp,
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomInset.calculateBottomPadding() + Space.xxl),
        ) {
            item(key = "header") { Header(page, cards.size, onOpenSettings) }
            if (page.loaded && cards.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.PersonAdd,
                        title = "Follow the people whose work you love",
                        body = "Add directors and actors from a title page or their own page. Repertoire tracks what you've seen of their films and lines up the next one to play.",
                        action = "Find people",
                        onAction = onFindPeople,
                        modifier = Modifier.padding(top = Space.huge),
                    )
                }
            }
            items(cards, key = { it.row.personId }) { card ->
                PersonHeroCard(
                    model,
                    card,
                    onOpen = { onPerson(card.row.personId, card.row.name) },
                    onRetry = { page.retry(card.row.personId) },
                    onCelebrated = { page.celebrated(card.row.personId) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (cards.isNotEmpty()) {
                item(key = "more") {
                    TextButton(onClick = onFindPeople, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(Space.s))
                        Text("Add someone")
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(page: RepertoireViewModel, people: Int, onOpenSettings: () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(start = Space.page, end = Space.s, top = Space.s, bottom = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (seen, total) = page.totals
            Text(
                when {
                    people == 0 -> "YOUR FILMMAKERS"
                    total == 0 -> if (people == 1) "1 PERSON" else "$people PEOPLE"
                    else -> "${if (people == 1) "1 PERSON" else "$people PEOPLE"} · $seen OF $total WATCHED"
                },
                style = OverlineStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
        }
        Text(
            "Repertoire",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = DisplayFamily),
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun PersonHeroCard(
    model: GuideViewModel,
    card: PersonCard,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onCelebrated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = card.row
    val progress = card.progress
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.snappy(), label = "press")
    // Milestone: a warm light sweeps across the card once, and the badge pops in.
    val sweep = remember { Animatable(if (card.celebrate) 0f else 1f) }
    LaunchedEffect(card.celebrate) {
        if (card.celebrate) {
            haptics.confirm()
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
            onCelebrated()
        }
    }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l, vertical = Space.s)
            .graphicsLayer {
                val s = 1f - 0.015f * press
                scaleX = s
                scaleY = s
            }
            .clip(Radii.Card)
            .background(cardColor)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Open ${row.name}") {
                haptics.tap()
                onOpen()
            }
            .drawWithContent {
                drawContent()
                val t = sweep.value
                if (t in 0.001f..0.999f) {
                    val x = size.width * (t * 1.6f - 0.3f)
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Brand.Amber.copy(alpha = 0.35f), Color.Transparent),
                            start = Offset(x - size.width * 0.25f, 0f),
                            end = Offset(x + size.width * 0.25f, size.height),
                        ),
                    )
                }
            },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            val backdrop = TmdbImages.backdrop(row.backdropPath)
            if (backdrop != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(backdrop).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val s = 1f + 0.03f * press
                        scaleX = s
                        scaleY = s
                    },
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.AmberDeep.copy(alpha = 0.35f), cardColor))))
            }
            // Film-still darkening: a soft top shade for the pill, a deep fade into the card below.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.35f to Color.Transparent,
                        0.62f to cardColor.copy(alpha = 0.55f),
                        1f to cardColor,
                    ),
                ),
            )
            Row(Modifier.align(Alignment.TopStart).padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.42f), contentColor = Color.White) {
                    Text(roleOf(row.department), style = OverlineStyle, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            val pop by animateFloatAsState(if (progress?.completed == true) 1f else 0f, Motion.bouncy(), label = "completed")
            if (pop > 0.01f) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(Space.m).graphicsLayer {
                        alpha = pop.coerceIn(0f, 1f)
                        scaleX = 0.4f + 0.6f * pop
                        scaleY = 0.4f + 0.6f * pop
                    },
                ) { CompletedBadge() }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = Space.l, end = Space.l, bottom = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingedPortrait(TmdbImages.profile(row.profilePath), progress?.fraction, size = 84.dp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DisplayFamily),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    when {
                        progress != null -> Text(
                            progress.label + if (progress.available < progress.total) " · ${progress.available} in your catalog" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        card.failed -> Text("Couldn't load their films", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        else -> Box(Modifier.width(140.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).posterShimmer())
                    }
                }
            }
        }
        Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.s, bottom = Space.l)) {
            ProgressStrip(progress?.fraction ?: 0f, visible = progress != null && progress.total > 0)
            Spacer(Modifier.height(Space.m))
            when {
                card.failed -> TextButton(onClick = onRetry) { Text("Try again") }
                progress == null -> Box(Modifier.fillMaxWidth().height(72.dp).clip(MaterialTheme.shapes.large).posterShimmer())
                progress.next != null -> NextUp(model, progress.next)
                progress.completed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Brand.Amber)
                    Spacer(Modifier.width(Space.s))
                    Text(
                        "You've seen everything of theirs that's in your catalog.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Space.s))
                    Text(
                        "None of their films are in your catalog right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun roleOf(department: String?): String = when (department) {
    "Directing" -> "DIRECTOR"
    "Acting", null -> "ACTOR"
    "Writing" -> "WRITER"
    "Production" -> "PRODUCER"
    "Camera" -> "CINEMATOGRAPHER"
    "Sound" -> "COMPOSER"
    else -> department.uppercase()
}

/** Portrait inside an amber arc showing how much of their work is watched. */
@Composable
private fun RingedPortrait(url: String?, fraction: Float?, size: Dp) {
    val shown by animateFloatAsState(fraction ?: 0f, Motion.gentle(), label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Box(
        Modifier.size(size).semantics { if (fraction != null) contentDescription = "${(fraction * 100).toInt()} percent watched" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.5.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
            if (shown > 0f) {
                drawArc(
                    Brush.sweepGradient(listOf(Brand.AmberDeep, Brand.Amber, Brand.AmberDeep)),
                    -90f,
                    360f * shown,
                    false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        ProfileImage(url, size = size - 12.dp)
    }
}

@Composable
private fun ProgressStrip(fraction: Float, visible: Boolean) {
    val shown by animateFloatAsState(if (visible) fraction else 0f, Motion.gentle(), label = "strip")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
        if (shown > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Brand.AmberDeep, Brand.Amber)),
                size = Size(size.width * shown, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
            )
        }
    }
}

@Composable
private fun CompletedBadge() {
    Surface(shape = CircleShape, color = Brand.Amber, contentColor = Brand.Ink, shadowElevation = 6.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("COMPLETED", style = OverlineStyle)
        }
    }
}

/** The earliest unwatched film that can be played, with one-tap play on the TV. */
@Composable
fun NextUp(model: GuideViewModel, item: WorkItem, modifier: Modifier = Modifier) {
    val credit = item.credit
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.s), verticalAlignment = Alignment.CenterVertically) {
            PosterArt(TmdbImages.poster(credit.posterPath), credit.title, Modifier.width(44.dp).aspectRatio(2f / 3f), shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("NEXT UP", style = OverlineStyle, color = MaterialTheme.colorScheme.primary)
                Text(credit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(credit.year?.toString(), if (credit.mediaType == "tv") "Series" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlayButton(
                tuning = model.isTuning(item.versions),
                enabled = model.tune == null,
                label = "Play ${credit.title} on TV",
                onPlay = { model.playBest(item.versions) },
                onLongPress = null,
            )
        }
    }
}
