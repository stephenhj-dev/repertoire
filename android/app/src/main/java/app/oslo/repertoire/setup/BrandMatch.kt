package app.oslo.repertoire.setup

/**
 * Suggests a guide from what we know about the TV: the manufacturer and model ADB reports
 * (getprop ro.product.manufacturer / ro.product.model) and the name or model from mDNS. Pure
 * string checks on folded text, no regex.
 */
object BrandMatch {
    fun suggest(manufacturer: String? = null, model: String? = null, name: String? = null): TvGuide? {
        val maker = fold(manufacturer)
        val hint = fold(listOfNotNull(model, name).joinToString(" "))
        val all = "$maker $hint"
        fun has(vararg words: String) = words.any { it in all }
        return when {
            maker.startsWith("nvidia") || has("shield") -> TvGuides.shield
            // Fire TV models report manufacturer Amazon and models like AFTMM, AFTKA, AFTSSS.
            maker.startsWith("amazon") || has("fire tv", "firetv", "fire stick", "firestick") || hint.startsWith("aft") -> TvGuides.fireTv
            // Fire TV Edition sets from TCL, Hisense, Toshiba, Insignia report Amazon models.
            maker.startsWith("onn") || has("onn 4k", "onn streaming", "onn tv", "onn ") || hint.startsWith("onn") -> TvGuides.onn
            maker.startsWith("google") || has("chromecast", "google tv streamer", "sabrina", "boreal", "kirkwood") -> TvGuides.googleTv
            maker.startsWith("xiaomi") || has("mibox", "mi box", "mi tv stick", "xiaomi") -> TvGuides.xiaomi
            maker.startsWith("sony") || has("bravia") -> TvGuides.sony
            maker.startsWith("tcl") || maker.startsWith("hisense") || has("tcl ", "hisense") || hint.startsWith("tcl") -> TvGuides.tclHisense
            maker.startsWith("philips") || maker.startsWith("tp vision") || maker.startsWith("tpvision") || has("philips") -> TvGuides.philips
            else -> null
        }
    }

    /** The guide to open: the suggestion, else the generic one. */
    fun guideFor(manufacturer: String? = null, model: String? = null, name: String? = null): TvGuide =
        suggest(manufacturer, model, name) ?: TvGuides.generic

    private fun fold(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val out = StringBuilder()
        var space = false
        for (ch in value.lowercase()) {
            if (ch.isLetterOrDigit()) {
                out.append(ch)
                space = false
            } else if (!space && out.isNotEmpty()) {
                out.append(' ')
                space = true
            }
        }
        return out.toString().trim()
    }
}
