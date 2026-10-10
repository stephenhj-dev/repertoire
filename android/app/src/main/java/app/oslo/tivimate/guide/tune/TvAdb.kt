package app.oslo.tivimate.guide.tune

import app.oslo.tivimate.guide.remote.Support
import app.oslo.tivimate.guide.remote.TvPorts
import app.oslo.tivimate.guide.remote.isFireTv
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException

data class AdbStatus(
    val ok: Boolean,
    val detail: String,
    val support: Support = if (ok) Support.Yes else Support.Unknown,
    val manufacturer: String = "",
    val model: String = "",
    /** Installed TiviMate player package; null when it is missing or ADB did not answer. */
    val tivimate: String? = null,
) {
    val fireTv: Boolean get() = isFireTv(manufacturer, model)
}

/** ADB over the network (classic adbd on port 5555) to any Android TV, Google TV or Fire TV. */
object TvAdb {
    fun probe(directory: File, host: String): AdbStatus {
        val address = host.substringBefore(":").trim()
        if (address.isBlank()) return AdbStatus(false, "Enter the TV address.", Support.Unknown)
        return try {
            Dadb.create(address, TvPorts.ADB, keys(directory), 4_000, 8_000).use { dadb ->
                val lines = dadb.shell(
                    "getprop ro.product.manufacturer; getprop ro.product.model; pm list packages ar.tvplayer",
                ).output.lines().map { it.trim() }
                val manufacturer = lines.getOrNull(0).orEmpty()
                val model = lines.getOrNull(1).orEmpty()
                val player = TiviMate.installedPlayer(lines.drop(2).joinToString("\n"))
                val label = listOf(manufacturer, model).filter { it.isNotBlank() }.distinct()
                    .joinToString(" ").ifBlank { "The TV" }
                val detail = "Debugging is connected on $address. $label is answering." +
                    if (player == null) " TiviMate is not installed on this TV." else ""
                AdbStatus(true, detail, Support.Yes, manufacturer, model, player)
            }
        } catch (error: Exception) {
            AdbStatus(false, advice(error), support(error))
        }
    }

    fun play(
        directory: File,
        host: String,
        name: String,
        packageName: String = TiviMate.PLAYER,
        report: (String) -> Unit,
    ): String {
        val address = host.substringBefore(":").trim()
        if (address.isBlank()) return "Choose a TV on the Devices page first."
        report("Connecting to the TV. Allow this phone if it asks.")
        return try {
            Dadb.create(address, TvPorts.ADB, keys(directory), 8_000, 45_000).use { dadb ->
                TiviMateTuner(TvShell { command -> dadb.shell(command).allOutput }, packageName = packageName)
                    .play(name, report)
            }
        } catch (error: Exception) {
            val text = error.message.orEmpty()
            when {
                error is ConnectException || "Connection refused" in text || "failed to connect" in text ->
                    "The TV is not accepting debugging. Turn on network debugging, then try again."
                error is SocketTimeoutException || "timed out" in text ->
                    "Allow this phone on the TV, then try the title again."
                else -> text.ifBlank { error.javaClass.simpleName }
            }
        }
    }

    fun advice(error: Exception): String {
        val text = error.message.orEmpty()
        return when {
            refused(error) ->
                "Network debugging is not accepting connections on port ${TvPorts.ADB}. Turn it on in Developer options: " +
                    "Shield: Device preferences → Developer options → Network debugging. " +
                    "Google TV / Android TV: System → Developer options → USB debugging. " +
                    "Fire TV: My Fire TV → Developer options → ADB debugging. " +
                    "The phone and the TV need to be on the same Wi-Fi."
            error is SocketTimeoutException || "timed out" in text ->
                "The TV did not answer. If it shows an Allow prompt, tap Allow and check Always allow. Confirm the address, and keep both devices on the same Wi-Fi."
            else -> text.ifBlank { "Could not open a debugging connection." }
        }
    }

    private fun refused(error: Exception): Boolean {
        val text = error.message.orEmpty()
        return error is ConnectException || "Connection refused" in text || "failed to connect" in text
    }

    private fun support(error: Exception): Support = if (refused(error)) Support.No else Support.Unknown

    internal fun keys(directory: File): AdbKeyPair {
        val privateKey = File(directory, "adbkey")
        val publicKey = File(directory, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }
}
