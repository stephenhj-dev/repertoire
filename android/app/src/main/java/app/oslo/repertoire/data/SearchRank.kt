package app.oslo.repertoire.data

fun rank(name: String, query: String): Int? {
    val folded = name.lowercase()
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return null
    if (folded == needle) return 0
    if (folded.startsWith(needle)) return 1
    if (needle in folded) return 2
    return null
}

fun likeNeedle(query: String): String =
    query.trim()
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
