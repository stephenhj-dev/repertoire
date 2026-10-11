package app.oslo.repertoire.tune

import app.oslo.repertoire.remote.TvPorts
import app.oslo.repertoire.remote.adbKeyCommand
import dadb.Dadb
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Remote control over ADB key events, for TVs without the Android TV Remote v2 service
 * (Fire TV) or that have not been paired. Keeps one ADB connection open so keys are quick.
 */
class AdbRemote(private val directory: File) {
    private var connection: Dadb? = null
    private var target = ""

    suspend fun key(host: String, code: Int, longPress: Boolean = false) {
        shell(host, adbKeyCommand(code, longPress))
    }

    suspend fun shell(host: String, command: String): String = withContext(Dispatchers.IO) {
        synchronized(this@AdbRemote) {
            var last: Exception? = null
            for (attempt in 0 until 2) {
                try {
                    return@withContext open(host).shell(command).allOutput
                } catch (error: Exception) {
                    last = error
                    closeLocked()
                }
            }
            val error = last ?: IllegalStateException("ADB failed")
            throw IllegalStateException(TvAdb.advice(error), error)
        }
    }

    suspend fun launchTiviMate(host: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        TiviMate.launch(TvShell { command -> runBlockingShell(host, command) }, packageName)
    }

    /** Opens TiviMate's audio or subtitle picker on the TV and reads its choices. */
    suspend fun openTracks(host: String, packageName: String, track: TiviMateScreens.Track): TrackPicker =
        withContext(Dispatchers.IO) { tuner(host, packageName).openTracks(track) }

    suspend fun chooseTrack(host: String, packageName: String, track: TiviMateScreens.Track, label: String): String =
        withContext(Dispatchers.IO) { tuner(host, packageName).chooseTrack(track, label) }

    private fun tuner(host: String, packageName: String) =
        TiviMateTuner(TvShell { command -> runBlockingShell(host, command) }, packageName = packageName)

    private fun runBlockingShell(host: String, command: String): String = synchronized(this) {
        try {
            open(host).shell(command).allOutput
        } catch (error: Exception) {
            closeLocked()
            open(host).shell(command).allOutput
        }
    }

    fun close() {
        synchronized(this) { closeLocked() }
    }

    private fun open(host: String): Dadb {
        val address = host.substringBefore(":").trim()
        require(address.isNotBlank()) { "Choose a TV on the Devices page first." }
        val current = connection
        if (current != null && target == address) return current
        closeLocked()
        val created = Dadb.create(address, TvPorts.ADB, TvAdb.keys(directory), 4_000, 8_000)
        connection = created
        target = address
        return created
    }

    private fun closeLocked() {
        runCatching { connection?.close() }
        connection = null
        target = ""
    }
}
