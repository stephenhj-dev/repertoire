package app.oslo.tivimate.guide.tune

data class UiNode(
    val text: String,
    val className: String,
    val clickable: Boolean,
    val focused: Boolean,
    val selected: Boolean,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

fun parseNodes(xml: String): List<UiNode> {
    val start = xml.indexOf("<hierarchy")
    val end = xml.lastIndexOf("</hierarchy>")
    if (start < 0 || end < 0) return emptyList()
    val body = xml.substring(start, end)
    val nodes = mutableListOf<UiNode>()
    for (match in NODE.findAll(body)) {
        val attrs = ATTR.findAll(match.groupValues[1]).associate {
            it.groupValues[1] to unescape(it.groupValues[2])
        }
        val box = BOUNDS.find(attrs["bounds"] ?: "") ?: continue
        nodes += UiNode(
            text = attrs["text"].orEmpty(),
            className = attrs["class"].orEmpty(),
            clickable = attrs["clickable"] == "true",
            focused = attrs["focused"] == "true",
            selected = attrs["selected"] == "true",
            left = box.groupValues[1].toInt(),
            top = box.groupValues[2].toInt(),
            right = box.groupValues[3].toInt(),
            bottom = box.groupValues[4].toInt(),
        )
    }
    return nodes
}

fun keyboardShown(dump: String) = "mInputShown=true" in dump

fun tivimateInFront(dump: String): Boolean {
    return dump.lineSequence().any { line ->
        ("mResumedActivity" in line || "topResumedActivity" in line) && "ar.tvplayer.tv" in line
    }
}

fun inSearch(items: List<UiNode>): Boolean {
    if (items.any { it.className.endsWith("StreamingTextView") }) return true
    val labels = items.map { it.text }.toSet()
    return labels.any { it in setOf("Speak to search", "Search history", "Channels") }
}

fun onMainMenu(items: List<UiNode>): Boolean {
    val labels = items.map { it.text }.toSet()
    return setOf("Search", "TV", "Movies", "Shows").all { it in labels }
}

fun hasResultCard(items: List<UiNode>): Boolean {
    return items.any {
        it.className.endsWith("ViewGroup") && it.clickable && it.top > 200 && it.bottom - it.top > 200
    }
}

fun fieldFocused(items: List<UiNode>): Boolean {
    return items.any { it.className.endsWith("StreamingTextView") && it.focused }
}

fun selectedTitle(items: List<UiNode>): String {
    items.firstOrNull {
        !it.className.endsWith("StreamingTextView") && it.selected && it.text.isNotEmpty()
    }?.let { return it.text }
    return items.firstOrNull {
        it.focused && it.text.isNotEmpty() && it.top > 200
    }?.text.orEmpty()
}

fun playing(items: List<UiNode>): Boolean {
    if (items.any { it.text == "Nothing found" }) return false
    if (items.any { it.text == "Play" && it.clickable }) return false
    return items.isNotEmpty() && !inSearch(items)
}

fun words(value: String): List<String> {
    val cleaned = buildString {
        for (char in value) append(if (char.isLetterOrDigit()) char.lowercaseChar() else ' ')
    }
    return cleaned.split(' ').filter { it.isNotEmpty() }
}

fun score(candidate: String, wanted: String): Int? {
    val left = words(candidate)
    val right = words(wanted)
    if (left.isEmpty() || right.isEmpty()) return null
    if (left.joinToString(" ") in setOf("search", "channels", "programs", "movies", "shows", "no information")) {
        return null
    }
    if (left == right) return 0
    if (right.all { it in left }) return 1
    return null
}

fun encodedText(value: String): String {
    val safe = buildString {
        for (char in value) {
            when {
                char.isLetterOrDigit() || char in ":.-+'" -> append(char)
                char.isWhitespace() -> append("%s")
            }
        }
    }
    var result = safe
    while (result.startsWith("%s")) result = result.removePrefix("%s")
    while (result.endsWith("%s")) result = result.removeSuffix("%s")
    return result
}

private fun unescape(value: String): String {
    return value
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}

private val NODE = Regex("<node\\b([^>]*)/?>")
private val ATTR = Regex("""([\w:-]+)="([^"]*)"""")
private val BOUNDS = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")
