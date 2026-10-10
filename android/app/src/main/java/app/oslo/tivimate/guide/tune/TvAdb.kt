package app.oslo.tivimate.guide.tune

import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException

data class AdbStatus(val ok: Boolean, val detail: String)

object TvAdb {
    fun probe(directory: File, host: String): AdbStatus {
        val address = host.substringBefore(":").trim()
        if (address.isBlank()) return AdbStatus(false, "Enter the Shield address.")
        return try {
            Dadb.create(address, 5555, keys(directory), 4_000, 8_000).use { dadb ->
                val model = dadb.shell("getprop ro.product.model").output.trim().ifBlank { "Shield" }
                AdbStatus(true, "Debugging is connected on $address. $model is answering.")
            }
        } catch (error: Exception) {
            AdbStatus(false, advice(error))
        }
    }

    fun play(directory: File, host: String, name: String, report: (String) -> Unit): String {
        val address = host.substringBefore(":").ifBlank { "192.168.1.50" }
        report("Connecting to the Shield. Allow this phone if it asks.")
        return try {
            Dadb.create(address, 5555, keys(directory), 8_000, 45_000).use { dadb ->
                TiviMateTuner(TvShell { command -> dadb.shell(command).allOutput }).play(name, report)
            }
        } catch (error: Exception) {
            val text = error.message.orEmpty()
            when {
                error is ConnectException || "Connection refused" in text || "failed to connect" in text ->
                    "The Shield is not accepting debugging. Turn on network debugging, then try again."
                error is SocketTimeoutException || "timed out" in text ->
                    "Allow this phone on the Shield, then try the title again."
                else -> text.ifBlank { error.javaClass.simpleName }
            }
        }
    }

    private fun advice(error: Exception): String {
        val text = error.message.orEmpty()
        return when {
            error is ConnectException || "Connection refused" in text || "failed to connect" in text ->
                "Network debugging is not accepting connections. On the Shield, open Settings → Device preferences → Developer options and turn on Network debugging. The phone and the Shield need to be on the same Wi-Fi."
            error is SocketTimeoutException || "timed out" in text ->
                "The Shield did not answer. If it shows an Allow prompt, tap Allow and check Always allow. Confirm the address, and keep both devices on the same Wi-Fi."
            else -> text.ifBlank { "Could not open a debugging connection." }
        }
    }

    private fun keys(directory: File): AdbKeyPair {
        val privateKey = File(directory, "adbkey")
        val publicKey = File(directory, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }
}
