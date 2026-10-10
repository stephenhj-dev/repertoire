package app.oslo.tivimate.guide

import app.oslo.tivimate.guide.tune.TvShell
import app.oslo.tivimate.guide.tune.TiviMateTuner
import app.oslo.tivimate.guide.tune.encodedText
import app.oslo.tivimate.guide.tune.score
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneLogicTest {
    @Test
    fun titlesMatchWithPunctuation() {
        val wanted = "4K-EN - The Matrix  1999"
        assertEquals(0, score("4K-EN - The Matrix  (1999)", wanted))
        assertEquals(1, score("EN-4K - The Matrix (1999)", wanted))
        assertNull(score("The Matrix 1999", wanted))
        assertNull(score("Movies", wanted))
    }

    @Test
    fun spacesBecomeInputTokens() {
        assertEquals("4K-EN%s-%sThe%sMatrix%s%s1999", encodedText("4K-EN - The Matrix  1999"))
    }

    @Test
    fun closesKeyboardBeforeSelectingTheMovie() {
        val tv = FakeTv(keyboard = true)
        val result = TiviMateTuner(tv) { }.play("4K-EN - The Matrix  1999")
        assertEquals("Playing 4K-EN - The Matrix  1999", result)
        val back = tv.commands.indexOfFirst { "KEYCODE_BACK" in it }
        val center = tv.commands.indexOfFirst { "KEYCODE_DPAD_CENTER" in it }
        assertTrue(back in 0 until center)
        assertFalse(tv.commands.any { "1771" in it })
        assertEquals(2, tv.commands.count { "KEYCODE_DPAD_CENTER" in it })
    }

    @Test
    fun leavesSearchAloneWhenTheKeyboardIsClosed() {
        val tv = FakeTv(keyboard = false)
        val result = TiviMateTuner(tv) { }.play("4K-EN - The Matrix  1999")
        assertEquals("Playing 4K-EN - The Matrix  1999", result)
        assertFalse(tv.commands.any { "KEYCODE_BACK" in it })
    }
}

private class FakeTv(var keyboard: Boolean) : TvShell {
    var screen = "search"
    var focused = "field"
    val commands = mutableListOf<String>()

    override fun exec(command: String): String {
        commands += command
        when {
            command.startsWith("dumpsys input_method") -> return if (keyboard) "mInputShown=true\n" else "mInputShown=false\n"
            command.startsWith("dumpsys activity") -> return "mResumedActivity: ar.tvplayer.tv/.ui.MainActivity\n"
            command.startsWith("uiautomator dump") -> return "dumped\n"
            command.startsWith("cat ") -> return xml()
            "KEYCODE_BACK" in command -> keyboard = false
            "KEYCODE_DPAD_DOWN" in command -> if (!keyboard) focused = "card"
            "KEYCODE_DPAD_CENTER" in command -> {
                check(!keyboard) { "OK was pressed while the keyboard was open" }
                screen = if (screen == "search") "detail" else "playing"
            }
        }
        return ""
    }

    private fun xml(): String {
        val body = when (screen) {
            "detail" -> """
                <node text="4K-EN - The Matrix  (1999)" class="android.widget.TextView" clickable="false" focused="false" selected="false" bounds="[56,40][1520,116]" />
                <node text="Play" class="android.widget.TextView" clickable="true" focused="true" selected="false" bounds="[48,816][209,888]" />
            """
            "playing" -> """
                <node text="4K-EN - The Matrix  (1999)" class="android.widget.TextView" clickable="false" focused="false" selected="false" bounds="[80,989][1440,1048]" />
            """
            else -> """
                <node text="4K-EN - The Matrix  1999" class="androidx.leanback.widget.StreamingTextView" clickable="true" focused="${focused == "field"}" selected="false" bounds="[404,83][1556,132]" />
                <node text="Movies" class="android.widget.TextView" clickable="false" focused="false" selected="false" bounds="[48,232][139,270]" />
                <node class="android.view.ViewGroup" clickable="true" focused="${focused == "card"}" selected="false" bounds="[44,306][298,735]" />
                <node text="4K-EN - The Matrix  (1999)" class="android.widget.TextView" clickable="false" focused="${focused == "card"}" selected="${focused == "card"}" bounds="[48,679][294,731]" />
            """
        }
        return "<hierarchy>$body</hierarchy>"
    }
}
