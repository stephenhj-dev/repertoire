package app.oslo.tivimate.guide.ui

import androidx.compose.ui.graphics.Color

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oslo.tivimate.guide.GuideRow
import app.oslo.tivimate.guide.GuideViewModel
import app.oslo.tivimate.guide.Page
import app.oslo.tivimate.guide.R
import app.oslo.tivimate.guide.Section
import app.oslo.tivimate.guide.remote.Keys
import app.oslo.tivimate.guide.remote.NavTransport
import app.oslo.tivimate.guide.remote.Support
import kotlinx.coroutines.delay

// Legacy palette, kept until this file is replaced by the new screens.
private val Ink = Color(0xFF07090E)
private val Paper = Color(0xFF10141C)
private val Card = Color(0xFF171D28)
private val Line = Color(0xFF2C3648)
private val Amber = Color(0xFFF5C518)
private val Foam = Color(0xFFE7EDF6)
private val Mist = Color(0xFF8E9AAD)
private val Good = Color(0xFF3DDC97)

@Composable
fun GuideApp(model: GuideViewModel) {
    val tv by model.remote.status.collectAsState()
    var menu by remember { mutableStateOf(false) }
    BackHandler(enabled = menu || model.page != Page.Home || model.drilled) {
        when {
            menu -> menu = false
            model.page == Page.Guide && model.back() -> Unit
            else -> model.show(Page.Home)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = barTitle(model.page), onMenu = { menu = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (model.page) {
                    Page.Home -> HomeScreen()
                    Page.Devices -> DevicesScreen(model, tv.waitingForCode, tv.detail, tv.connected)
                    Page.Guide -> GuideScreen(model)
                    Page.Remote -> RemoteScreen(
                        model,
                        tv.connected,
                        tv.detail,
                        tv.volumeLevel,
                        tv.volumeMax,
                        tv.muted,
                    )
                    Page.Settings -> SettingsScreen(model)
                }
            }
        }
        if (menu) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = 0.55f))
                    .clickable { menu = false },
            )
        }
        AnimatedVisibility(
            visible = menu,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 3 },
        ) {
            MenuPanel(
                current = model.page,
                onPick = { page ->
                    menu = false
                    model.show(page)
                },
            )
        }
    }
}

private fun barTitle(page: Page) = when (page) {
    Page.Home -> ""
    Page.Devices -> "Devices"
    Page.Guide -> "Guide"
    Page.Remote -> "Remote"
    Page.Settings -> "Settings"
}

@Composable
private fun TopBar(title: String, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title.isNotEmpty()) {
            Text(
                title,
                color = Foam,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) { MenuGlyph() }
    }
}

@Composable
private fun MenuGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val y = listOf(size.height * 0.22f, size.height * 0.5f, size.height * 0.78f)
        y.forEach { line ->
            drawLine(
                color = Foam,
                start = androidx.compose.ui.geometry.Offset(0f, line),
                end = androidx.compose.ui.geometry.Offset(size.width, line),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MenuPanel(current: Page, onPick: (Page) -> Unit) {
    Column(
        Modifier
            .padding(top = 58.dp, end = 12.dp)
            .width(232.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Paper)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(vertical = 8.dp),
    ) {
        MenuRow("Devices", current == Page.Devices) { onPick(Page.Devices) }
        MenuRow("Guide", current == Page.Guide) { onPick(Page.Guide) }
        MenuRow("Settings", current == Page.Settings) { onPick(Page.Settings) }
    }
}

@Composable
private fun MenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Ink else Foam,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Amber else Card.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun HomeScreen() {
    var reveal by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (reveal < 1f) {
            val elapsed = (System.nanoTime() - start) / 1_000_000f
            reveal = (elapsed / 900f).coerceIn(0f, 1f)
            delay(16)
        }
    }
    val eased = FastOutSlowInEasing.transform(reveal)
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(0.86f + 0.14f * eased).alpha(eased)) {
            Box(
                Modifier
                    .size(280.dp)
                    .background(
                        Brush.radialGradient(listOf(Amber.copy(alpha = 0.22f * pulse), Ink.copy(alpha = 0f))),
                        CircleShape,
                    ),
            )
            Image(
                painter = painterResource(R.drawable.brand_mark),
                contentDescription = "TiviMate Assistant",
                modifier = Modifier.size(196.dp).clip(RoundedCornerShape(36.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "TiviMate Assistant",
            color = Foam,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.6).sp,
            modifier = Modifier.alpha(eased),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Guide and remote for TiviMate on your TV",
            color = Mist,
            fontSize = 15.sp,
            modifier = Modifier.alpha(eased),
        )
    }
}

@Composable
private fun DevicesScreen(model: GuideViewModel, waiting: Boolean, detail: String, linked: Boolean) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                if (model.devices.isEmpty()) "No TV yet. Tap Find, or enter its address." else "TVs on this network",
                color = Mist,
                fontSize = 13.sp,
            )
        }
        itemsIndexed(model.devices) { _, device ->
            val selected = device.host == model.selectedHost
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Card)
                    .border(1.dp, if (selected) Amber else Line, RoundedCornerShape(18.dp))
                    .clickable { model.selectDevice(device) }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, color = Foam, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        if (device.model.isNotBlank() && device.model != device.name) {
                            Text(device.model, color = Mist, fontSize = 13.sp)
                        }
                        Text(device.host, color = Amber, fontSize = 14.sp)
                    }
                    if (selected && model.adbOk == true) GoodMark()
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        !selected -> "Tap to use this TV"
                        model.checkingAdb -> "Checking what this TV supports"
                        else -> model.adbDetail
                    },
                    color = if (selected && model.adbOk == true) Good else Mist,
                    fontSize = 13.sp,
                )
                if (selected) {
                    Spacer(Modifier.height(6.dp))
                    Text(capabilityLine(model), color = Foam, fontSize = 13.sp)
                }
            }
        }
        item { Field("TV address", model.host) { model.host = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Use address") { model.useAddress(model.host) }
                PrimaryButton(if (model.scanning) "Scanning" else "Find", model::findTv)
            }
        }
        item { PrimaryButton(if (model.checkingAdb) "Checking" else "Check device", model::checkDevice) }
        item {
            Text(
                if (linked) "Remote link: $detail" else "Remote link: ${detail.ifBlank { "not paired" }}",
                color = Mist,
                fontSize = 13.sp,
            )
        }
        if (model.fireTv || model.remoteV2 == Support.No) {
            item {
                Text(
                    if (model.fireTv) {
                        "Fire TV has no Android TV remote service. The remote sends keys over ADB debugging instead, so turn on ADB debugging in My Fire TV → Developer options."
                    } else {
                        "This TV has no Android TV remote service. The remote sends keys over ADB debugging instead."
                    },
                    color = Mist,
                    fontSize = 13.sp,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model.remoteV2 != Support.No) PrimaryButton("Pair remote", model::pair)
                PrimaryButton("Open remote") { model.show(Page.Remote) }
            }
        }
        if (waiting) {
            item { Field("Code on the TV", model.pin) { model.pin = it.uppercase().take(6) } }
            item { PrimaryButton("Submit code", model::submitPin) }
        }
        if (model.status.isNotBlank()) item { Text(model.status, color = Amber, fontSize = 13.sp) }
    }
}

private fun capabilityLine(model: GuideViewModel): String {
    fun mark(value: Support) = when (value) {
        Support.Yes -> "yes"
        Support.No -> "no"
        Support.Unknown -> "?"
    }
    val via = when (model.transport) {
        NavTransport.RemoteV2 -> if (model.paired) "Android TV remote" else "Android TV remote (pair first)"
        NavTransport.Adb -> "ADB key events"
        NavTransport.None -> "not available"
    }
    return "Remote service: ${mark(model.remoteV2)} · Debugging: ${mark(model.adbSupport)} · Remote uses $via"
}

@Composable
private fun GoodMark() {
    Canvas(Modifier.size(28.dp)) {
        drawCircle(Good.copy(alpha = 0.16f))
        drawCircle(color = Good, style = Stroke(width = 2.dp.toPx()))
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.34f)
        }
        drawPath(
            path,
            Good,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun GuideScreen(model: GuideViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Leave TiviMate open, then tap a title.",
            color = Mist,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Field("Search live TV, movies, shows", model.query, Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            model.onQuery(it)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Section.Tv, Section.Movies, Section.Series).forEach { section ->
                val selected = model.section == section
                Text(
                    when (section) {
                        Section.Tv -> "Live TV"
                        Section.Movies -> "Movies"
                        Section.Series -> "TV Shows"
                        Section.All -> "All"
                    },
                    color = if (selected) Ink else Foam,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Amber else Card)
                        .clickable { model.select(section) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Text(model.heading, color = Mist, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        if (model.status.isNotBlank()) {
            Text(model.status, color = Amber, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            itemsIndexed(model.rows, key = { index, row -> "$index:${rowKey(row)}" }) { _, row ->
                GuideLine(row) { model.open(row) }
            }
            item {
                if (model.rows.isNotEmpty()) {
                    LaunchedEffect(model.rows.size, model.heading) { model.loadMore() }
                }
            }
        }
    }
}

private fun rowKey(row: GuideRow): String = when (row) {
    is GuideRow.Bucket -> "b:${row.id}:${row.label}"
    is GuideRow.Title -> "t:${row.kind}:${row.name}"
    is GuideRow.Banner -> "n:${row.name}"
    is GuideRow.More -> "m:${row.label}"
    is GuideRow.Note -> "note:${row.text}"
}

@Composable
private fun GuideLine(row: GuideRow, onClick: () -> Unit) {
    when (row) {
        is GuideRow.Note -> Text(row.text, color = Mist, modifier = Modifier.padding(vertical = 12.dp))
        is GuideRow.Banner -> Text(
            row.name,
            color = Amber,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        )
        is GuideRow.More -> Text(
            row.label,
            color = Amber,
            modifier = Modifier.clickable(onClick = onClick).padding(vertical = 12.dp),
        )
        is GuideRow.Bucket -> Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(Card).clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.label, color = Foam, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.count.toString(), color = Mist)
        }
        is GuideRow.Title -> Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(Card).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (row.caption.isNotBlank()) Text(row.caption, color = Amber, fontSize = 12.sp)
            Text(row.name, color = Foam, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsScreen(model: GuideViewModel) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Playlist", color = Amber, fontWeight = FontWeight.SemiBold) }
        item { Field("Portal URL", model.portalUrl) { model.portalUrl = it } }
        item { Field("Username", model.username) { model.username = it } }
        item { Field("Password", model.password, password = true) { model.password = it } }
        item { PrimaryButton(if (model.syncing) "Refreshing" else "Save and refresh", model::savePortal) }
        item { Text("Connection", color = Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Stay linked to the TV", color = Foam)
                    Text(
                        if (model.paired) "Pairing is kept until you disconnect." else "Pair the TV from Devices first.",
                        color = Mist,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = model.linked,
                    onCheckedChange = { on -> if (on) model.connect() else model.disconnectTv() },
                    enabled = model.paired,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Amber,
                        checkedThumbColor = Ink,
                        uncheckedTrackColor = Line,
                        uncheckedThumbColor = Mist,
                    ),
                )
            }
        }
        item {
            Text(
                "Video stays on the TV. This phone is the guide and the remote.",
                color = Mist,
                fontSize = 13.sp,
            )
        }
        if (model.status.isNotBlank()) item { Text(model.status, color = Amber) }
    }
}

@Composable
private fun RemoteScreen(
    model: GuideViewModel,
    connected: Boolean,
    detail: String,
    level: Int?,
    max: Int?,
    muted: Boolean,
) {
    val volume = when {
        muted -> "Muted"
        level != null && max != null && max > 0 -> "Volume $level / $max"
        else -> if (connected) "Volume" else detail
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            val viaAdb = model.transport == NavTransport.Adb
            Text(
                when {
                    viaAdb -> "${model.deviceName} · keys over ADB"
                    connected -> "${model.deviceName} connected"
                    else -> detail
                },
                color = if (connected || viaAdb) Amber else Mist,
            )
        }
        if (model.status.isNotBlank()) item { Text(model.status, color = Mist, fontSize = 13.sp) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HoldKey("Vol −") { model.hold(Keys.VOLUME_DOWN, it) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(volume, color = Foam) }
                HoldKey("Vol +") { model.hold(Keys.VOLUME_UP, it) }
                Key("Mute") { model.tap(Keys.VOLUME_MUTE) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldKey("Ch −") { model.hold(Keys.CHANNEL_DOWN, it) }
                HoldKey("Ch +") { model.hold(Keys.CHANNEL_UP, it) }
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Key("Up") { model.tap(Keys.DPAD_UP) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Key("Left") { model.tap(Keys.DPAD_LEFT) }
                    Key("OK") { model.tap(Keys.DPAD_CENTER) }
                    Key("Right") { model.tap(Keys.DPAD_RIGHT) }
                }
                Key("Down") { model.tap(Keys.DPAD_DOWN) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldKey("Rewind") { model.hold(Keys.MEDIA_REWIND, it) }
                Key("Play") { model.tap(Keys.MEDIA_PLAY) }
                Key("Pause") { model.tap(Keys.MEDIA_PAUSE) }
                HoldKey("Forward") { model.hold(Keys.MEDIA_FAST_FORWARD, it) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key("Previous") { model.tap(Keys.MEDIA_PREVIOUS) }
                Key("Next") { model.tap(Keys.MEDIA_NEXT) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key("Audio") { model.tap(Keys.MEDIA_AUDIO_TRACK) }
                Key("Subtitles") { model.tap(Keys.CAPTIONS) }
                Key("TV menu") { model.menu() }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key("Back") { model.tap(Keys.BACK) }
                Key("Home") { model.tap(Keys.HOME) }
                Key("TiviMate") { model.launchTiviMate() }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { digits ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        digits.forEach { digit -> Key(digit.toString()) { model.digit(digit) } }
                    }
                }
                Key("0") { model.digit(0) }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Card, contentColor = Foam),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) { Text(label) }
}

@Composable
private fun HoldKey(label: String, onHold: (Boolean) -> Unit) {
    Box(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onHold(true)
                        tryAwaitRelease()
                        onHold(false)
                    },
                )
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Foam) }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Amber,
            unfocusedBorderColor = Line,
            focusedLabelColor = Amber,
            unfocusedLabelColor = Mist,
            cursorColor = Amber,
            focusedTextColor = Foam,
            unfocusedTextColor = Foam,
        ),
    )
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
        shape = RoundedCornerShape(12.dp),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}
