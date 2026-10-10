package app.oslo.repertoire.tune

fun interface TvShell {
    fun exec(command: String): String
}

class TiviMateTuner(
    private val shell: TvShell,
    private val packageName: String = TiviMate.PLAYER,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun play(name: String, report: (String) -> Unit = {}): String {
        report("Opening TiviMate")
        val opened = ensureSearch()
        if (!inSearch(opened)) return "Could not open TiviMate search"
        report("Typing $name")
        focusField(opened)
        typeText(name)
        pause(600)
        return if (openAndPlay(name, report)) "Playing $name" else "Could not find $name"
    }

    private fun ensureSearch(): List<UiNode> {
        TiviMate.launch(shell, packageName)
        pause(500)
        var items = dumpNodes()
        if (inSearch(items)) return items
        repeat(7) {
            if (onMainMenu(items)) {
                // Leaving playback, the main menu is still animating in, so the
                // first tap on Search is often dropped. Let it settle, then
                // retry the tap a few times before giving up.
                pause(400)
                items = dumpNodes()
                repeat(3) {
                    if (onMainMenu(items)) items = openSearch(items)
                    if (inSearch(items)) return items
                    pause(500)
                    items = dumpNodes()
                    if (inSearch(items)) return items
                }
                return items
            }
            key("KEYCODE_BACK")
            pause(450)
            if (!tivimateInFront(shell.exec("dumpsys activity activities"), packageName)) {
                TiviMate.launch(shell, packageName)
                pause(700)
            }
            items = dumpNodes()
            if (inSearch(items)) return items
        }
        if (onMainMenu(items)) items = openSearch(items)
        return items
    }

    private fun openSearch(items: List<UiNode>): List<UiNode> {
        val scale = screenScale(items)
        val search = items.firstOrNull { it.text == "Search" && it.left < scaled(500, scale) } ?: return items
        val pad = scaled(16, scale)
        tap(scaled(32, scale), (search.top - pad).coerceAtLeast(0), scaled(408, scale), search.bottom + pad)
        // Poll instead of one fixed wait: search can take a moment to open.
        var now = items
        repeat(5) {
            pause(400)
            now = dumpNodes()
            if (inSearch(now)) return now
        }
        return now
    }

    private fun focusField(items: List<UiNode>) {
        val field = items.firstOrNull { it.className.endsWith("StreamingTextView") }
            ?: items.firstOrNull { it.text in setOf("Speak to search", "Search") && it.top < scaled(180, screenScale(items)) }
            ?: return
        tap(field.left, field.top, field.right, field.bottom)
        pause(300)
        val current = field.text
        if (current.isNotBlank() && current.lowercase() !in setOf("search", "speak to search")) {
            key("123")
            val count = (current.length + 2).coerceAtMost(40)
            shell.exec("input keyevent ${List(count) { "67" }.joinToString(" ")}")
        }
    }

    private fun openAndPlay(name: String, report: (String) -> Unit): Boolean {
        val items = waitForResults()
        if (items.any { it.text == "Nothing found" } && !hasResultCard(items)) return false
        report("Closing the keyboard")
        if (!dismissKeyboard()) return false
        if (!focusResult(name)) return false
        if (keyboardShown(shell.exec("dumpsys input_method"))) return false
        report("Selecting $name")
        key("KEYCODE_DPAD_CENTER")
        var seen = emptyList<UiNode>()
        repeat(4) {
            pause(500)
            seen = dumpNodes()
            if (seen.any { it.text == "Play" && it.clickable } || !inSearch(seen)) return@repeat
        }
        val play = seen.firstOrNull { it.text == "Play" && it.clickable }
        if (play != null) {
            if (play.focused) key("KEYCODE_DPAD_CENTER") else tap(play.left, play.top, play.right, play.bottom)
            pause(1400)
            seen = dumpNodes()
        }
        return playing(seen)
    }

    private fun waitForResults(): List<UiNode> {
        var items = emptyList<UiNode>()
        repeat(5) {
            items = dumpNodes()
            if (hasResultCard(items) || items.any { it.text == "Nothing found" }) return items
            pause(350)
        }
        return items
    }

    private fun dismissKeyboard(): Boolean {
        repeat(3) {
            if (!keyboardShown(shell.exec("dumpsys input_method"))) {
                pause(350)
                if (!keyboardShown(shell.exec("dumpsys input_method"))) return true
            }
            key("KEYCODE_BACK")
            pause(550)
        }
        return !keyboardShown(shell.exec("dumpsys input_method"))
    }

    private fun focusResult(wanted: String): Boolean {
        if (!dismissKeyboard()) return false
        var previous = ""
        var stuck = 0
        var movedDown = false
        repeat(12) {
            if (keyboardShown(shell.exec("dumpsys input_method")) && !dismissKeyboard()) return false
            val items = dumpNodes()
            if (fieldFocused(items) || !movedDown) {
                key("KEYCODE_DPAD_DOWN")
                movedDown = true
                pause(450)
                return@repeat
            }
            if (items.any { it.text == "Nothing found" } && !hasResultCard(items)) return false
            val title = selectedTitle(items)
            if (score(title, wanted) != null) return true
            stuck = if (title == previous) stuck + 1 else 0
            previous = title
            key(if (stuck > 0) "KEYCODE_DPAD_DOWN" else "KEYCODE_DPAD_RIGHT")
            pause(350)
        }
        return false
    }

    private fun dumpNodes(): List<UiNode> {
        shell.exec("uiautomator dump /sdcard/tm-tune.xml")
        val xml = shell.exec("cat /sdcard/tm-tune.xml")
        return parseNodes(xml)
    }

    private fun typeText(value: String) {
        val text = encodedText(value)
        if (text.isNotEmpty()) shell.exec("input text $text")
    }

    private fun key(name: String) {
        shell.exec("input keyevent $name")
    }

    private fun tap(left: Int, top: Int, right: Int, bottom: Int) {
        val x = (left + right) / 2
        val y = (top + bottom) / 2
        shell.exec("input tap $x $y")
    }
}
