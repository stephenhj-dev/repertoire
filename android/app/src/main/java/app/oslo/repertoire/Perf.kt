package app.oslo.repertoire

import android.util.Log

/** Timing logs under the "Perf" tag (adb logcat -s Perf). */
object Perf {
    inline fun <T> time(label: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            Log.d("Perf", "$label: ${(System.nanoTime() - start) / 1_000_000} ms")
        }
    }

    fun log(message: String) {
        Log.d("Perf", message)
    }
}
