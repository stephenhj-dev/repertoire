package app.oslo.repertoire

import app.oslo.repertoire.tune.TiviMateScreens.Track
import app.oslo.repertoire.tune.TiviMateTuner
import app.oslo.repertoire.tune.TvShell
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Drives TiviMate on a real TV through the laptop's adb. Skipped unless LIVE_TV is set
 * (e.g. LIVE_TV=192.168.1.50:5555), so the normal test run never touches a device.
 */
class LiveShieldTest {
    private val serial: String? = System.getenv("LIVE_TV")

    private fun tuner(): TiviMateTuner {
        val shell = TvShell { command ->
            val process = ProcessBuilder("adb", "-s", serial, "shell", command).redirectErrorStream(true).start()
            process.outputStream.close()
            process.inputStream.bufferedReader().readText().also { process.waitFor() }
        }
        return TiviMateTuner(shell)
    }

    @Test
    fun audioAndSubtitleDialogs() {
        assumeTrue(serial != null && System.getenv("LIVE_TRACKS") != null)
        val tuner = tuner()
        val audio = tuner.openTracks(Track.Audio)
        println("audio: $audio")
        val current = audio.options.firstOrNull { it.selected }
        if (current != null) println("choose current audio: " + tuner.chooseTrack(Track.Audio, current.label))
        Thread.sleep(1500)
        val subs = tuner.openTracks(Track.Subtitles)
        println("subtitles: $subs")
        val currentSub = subs.options.firstOrNull { it.selected }
        if (currentSub != null) println("choose current subtitles: " + tuner.chooseTrack(Track.Subtitles, currentSub.label))
        else tuner.closeTracks()
    }
}
