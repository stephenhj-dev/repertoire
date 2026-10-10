package app.oslo.tivimate.guide.remote

/** mDNS service types the scanner listens for. Trailing dots match what NsdManager expects. */
object TvServiceTypes {
    /** Android TV Remote v2 (Google TV, Android TV, Shield). Port 6466, pairing on 6467. */
    const val REMOTE = "_androidtvremote2._tcp."
    /** Android 11+ wireless debugging (TLS, needs a pairing code). */
    const val ADB_TLS = "_adb-tls-connect._tcp."
    /** Classic adbd advertisement, seen on some boxes with network debugging on. */
    const val ADB = "_adb._tcp."
    /** Google Cast; used only to enrich a device with its friendly name (fn) and model (md). */
    const val CAST = "_googlecast._tcp."
    /** Amazon Whisperplay; advertised by Fire TV devices, which have no Remote v2 service. */
    const val FIRE_TV = "_amzn-wplay._tcp."

    val ALL = listOf(REMOTE, ADB_TLS, ADB, CAST, FIRE_TV)

    fun same(left: String, right: String): Boolean = normalize(left) == normalize(right)

    private fun normalize(type: String) = type.trim().trimEnd('.').lowercase()
}

/** Default ports. Remote v2 uses the same ports on every Android TV / Google TV build. */
object TvPorts {
    const val REMOTE = 6466
    const val PAIRING = 6467
    const val ADB = 5555
}

data class TvDevice(
    val host: String,
    val name: String,
    val model: String = "",
    /** Remote v2 port when the device advertises _androidtvremote2._tcp. */
    val remotePort: Int? = null,
    /** Wireless-debugging port from _adb-tls-connect._tcp (TLS ADB). */
    val adbTlsPort: Int? = null,
    /** Advertises classic _adb._tcp. */
    val adbAdvertised: Boolean = false,
    /** Looks like an Amazon Fire TV. */
    val fireTv: Boolean = false,
) {
    val advertisesRemote: Boolean get() = remotePort != null
    val advertisesAdb: Boolean get() = adbAdvertised || adbTlsPort != null
}

/**
 * Folds every resolved service into one entry per host. Hosts that only advertise Cast
 * (speakers, Nest hubs, older Chromecasts) are dropped: they cannot run TiviMate. The phone's
 * own addresses are dropped too, since a phone with wireless debugging advertises _adb-tls-connect.
 */
fun mergeDevices(services: List<ResolvedService>, ownAddresses: Set<String> = emptySet()): List<TvDevice> {
    return services
        .filter { it.host.isNotBlank() && it.host !in ownAddresses }
        .groupBy { it.host }
        .mapNotNull { (host, found) -> deviceFor(host, found) }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.host }))
}

private fun deviceFor(host: String, found: List<ResolvedService>): TvDevice? {
    fun of(type: String) = found.firstOrNull { TvServiceTypes.same(it.type, type) }
    val remote = of(TvServiceTypes.REMOTE)
    val tls = of(TvServiceTypes.ADB_TLS)
    val adb = of(TvServiceTypes.ADB)
    val cast = of(TvServiceTypes.CAST)
    val fire = of(TvServiceTypes.FIRE_TV)
    if (remote == null && tls == null && adb == null && fire == null) return null

    val castName = cast?.attributes?.get("fn").orEmpty().trim()
    val castModel = cast?.attributes?.get("md").orEmpty().trim()
    val fireName = fire?.attributes?.get("n").orEmpty().trim()
    val name = listOf(
        castName,
        remote?.name?.let(::decodeServiceName).orEmpty(),
        fireName,
        fire?.name?.let(::decodeServiceName).orEmpty(),
    ).firstOrNull { it.isNotBlank() } ?: host
    val model = castModel.ifBlank { if (fire != null) "Fire TV" else "" }
    return TvDevice(
        host = host,
        name = name,
        model = model,
        remotePort = remote?.port?.takeIf { it > 0 },
        adbTlsPort = tls?.port?.takeIf { it > 0 },
        adbAdvertised = adb != null,
        fireTv = fire != null && remote == null,
    )
}

/** NSD hands back DNS-SD escapes on some releases, e.g. "Living\\032Room" for "Living Room". */
fun decodeServiceName(raw: String): String {
    val out = StringBuilder()
    var index = 0
    while (index < raw.length) {
        val char = raw[index]
        if (char == '\\' && index + 3 < raw.length && raw.substring(index + 1, index + 4).all { it.isDigit() }) {
            out.append(raw.substring(index + 1, index + 4).toInt().toChar())
            index += 4
        } else if (char == '\\' && index + 1 < raw.length) {
            out.append(raw[index + 1])
            index += 2
        } else {
            out.append(char)
            index++
        }
    }
    return out.toString().trim()
}

/** True when ADB properties describe an Amazon Fire TV (manufacturer Amazon, models AFT*). */
fun isFireTv(manufacturer: String, model: String): Boolean {
    return manufacturer.trim().equals("Amazon", ignoreCase = true) || model.trim().startsWith("AFT")
}
