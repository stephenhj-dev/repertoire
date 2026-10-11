package app.oslo.repertoire

import app.oslo.repertoire.tune.TiviMateScreens
import app.oslo.repertoire.tune.TiviMateScreens.Track
import app.oslo.repertoire.tune.UiNode
import app.oslo.repertoire.tune.parseNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiviMateScreensTest {
    private fun node(text: String, top: Int = 0, desc: String = "", clickable: Boolean = true, checkable: Boolean = false, checked: Boolean = false) =
        UiNode(text, "android.widget.TextView", clickable, false, false, 100, top, 600, top + 60, desc, checkable, checked)

    @Test
    fun readsSeasonLabels() {
        assertEquals(2, TiviMateScreens.seasonNumber("Season 2"))
        assertEquals(12, TiviMateScreens.seasonNumber("S12"))
        assertEquals(3, TiviMateScreens.seasonNumber("Staffel 3"))
        assertEquals(1, TiviMateScreens.seasonNumber("Season 01 (10)"))
        assertNull(TiviMateScreens.seasonNumber("Seasons"))
        assertNull(TiviMateScreens.seasonNumber("2"))
        assertNull(TiviMateScreens.seasonNumber("S01E02"))
    }

    @Test
    fun matchesEpisodeRows() {
        assertEquals(0, TiviMateScreens.episodeMatch("Dark - S02E05 - Lost and Found", 2, 5, null))
        assertNull(TiviMateScreens.episodeMatch("Dark - S01E05 - Truths", 2, 5, null))
        assertEquals(0, TiviMateScreens.episodeMatch("2x05", 2, 5, null))
        assertEquals(1, TiviMateScreens.episodeMatch("5. Lost and Found", 2, 5, null))
        assertEquals(1, TiviMateScreens.episodeMatch("Episode 5", 2, 5, null))
        assertEquals(1, TiviMateScreens.episodeMatch("E5 - Lost", 2, 5, null))
        assertNull(TiviMateScreens.episodeMatch("15. Other", 2, 5, null))
        assertEquals(2, TiviMateScreens.episodeMatch("Lost and Found", 2, 5, "Lost and Found"))
        assertNull(TiviMateScreens.episodeMatch("Season 2", 2, 5, null))
        assertNull(TiviMateScreens.episodeMatch("2016", 2, 5, null))
    }

    @Test
    fun findsEpisodeNodePreferringCodes() {
        val items = listOf(node("Season 2"), node("5. Pilot", 100), node("Show S02E05", 200), node("4. Before", 300))
        assertEquals("Show S02E05", TiviMateScreens.episodeNode(items, 2, 5, null)!!.text)
        assertEquals(listOf(5, 4), TiviMateScreens.visibleEpisodes(items, 2))
        assertEquals("Season 2", TiviMateScreens.seasonNode(items, 2)!!.text)
    }

    private fun fixture(name: String) =
        parseNodes(javaClass.classLoader!!.getResource("tivimate/$name")!!.readText())

    @Test
    fun readsTheLivePlayerMenuFromAShieldDump() {
        val items = fixture("player-menu-live.xml")
        val row = TiviMateScreens.menuRow(items)!!
        assertEquals("Search", row.tiles[row.focused].text)
        assertEquals(listOf("Search", "Channels list", "Recordings", "Multiview", "Picture-in-picture", "1280 × 720", "Stereo", "0 ms", "Closed captions 1"), row.tiles.map { it.text })
        assertEquals(6, TiviMateScreens.trackTile(row, Track.Audio))
        assertEquals(8, TiviMateScreens.trackTile(row, Track.Subtitles))
        assertEquals(List(6) { "KEYCODE_DPAD_RIGHT" } + "KEYCODE_DPAD_CENTER", TiviMateScreens.keysTo(row, 6))
    }

    @Test
    fun otherScreensAreNotTheMenu() {
        assertNull(TiviMateScreens.menuRow(fixture("playback.xml")))
        assertNull(TiviMateScreens.menuRow(fixture("tv-guide.xml")))
        assertTrue(TiviMateScreens.fullscreenPlayback(fixture("playback.xml")))
        assertFalse(TiviMateScreens.fullscreenPlayback(fixture("tv-guide.xml")))
        assertNull(TiviMateScreens.trackDialog(fixture("player-menu-live.xml")))
        assertNull(TiviMateScreens.trackDialog(fixture("quick-panel.xml")))
    }

    @Test
    fun findsTracksByNeighboursWhenTilesShowValues() {
        fun row(vararg labels: String, focused: Int = 0) =
            TiviMateScreens.MenuRow(labels.mapIndexed { i, l -> node(l, 900).copy(left = i * 200, right = i * 200 + 190) }, focused)
        assertEquals(1, TiviMateScreens.trackTile(row("1920x1080", "English 5.1", "-40 ms", "Off", "Normal"), Track.Audio))
        assertEquals(3, TiviMateScreens.trackTile(row("1920x1080", "English 5.1", "-40 ms", "Off", "Normal"), Track.Subtitles))
        assertEquals(2, TiviMateScreens.trackTile(row("Search", "1920 × 1080", "Deutsch", "Subtitles"), Track.Audio))
        assertEquals(3, TiviMateScreens.trackTile(row("Search", "1920 × 1080", "Deutsch", "Subtitles"), Track.Subtitles))
        assertEquals(listOf("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_CENTER"), TiviMateScreens.keysTo(row("a", "b", "c", focused = 2), 1))
        assertTrue(TiviMateScreens.isOffset("0 ms"))
        assertTrue(TiviMateScreens.isOffset("-120 ms"))
        assertFalse(TiviMateScreens.isOffset("Stereo"))
        assertTrue(TiviMateScreens.isResolution("1280 × 720"))
        assertFalse(TiviMateScreens.isResolution("Mix 2x"))
    }

    @Test
    fun readsTheAudioDialog() {
        val dialog = TiviMateScreens.trackDialog(fixture("audio-dialog.xml"))!!
        assertEquals("Audio tracks", dialog.title)
        assertEquals(listOf(TiviMateScreens.TrackOption("Off", false), TiviMateScreens.TrackOption("Stereo", true)), TiviMateScreens.trackOptions(dialog))
        assertEquals(listOf("KEYCODE_DPAD_UP", "KEYCODE_DPAD_CENTER"), TiviMateScreens.keysToOption(dialog, "Off"))
        assertEquals(listOf("KEYCODE_DPAD_CENTER"), TiviMateScreens.keysToOption(dialog, "Stereo"))
        assertNull(TiviMateScreens.keysToOption(dialog, "Deutsch"))
    }

    @Test
    fun readsTheCaptionsDialogWithoutItsSettings() {
        val dialog = TiviMateScreens.trackDialog(fixture("cc-dialog.xml"))!!
        assertEquals("Closed captions", dialog.title)
        assertEquals(listOf("Off", "Closed captions 1"), dialog.options.map { it.text })
        assertEquals(1, dialog.focused)
    }

    @Test
    fun knowsWhichEpisodeHasFocus() {
        val tile = UiNode("", "android.widget.FrameLayout", true, true, false, 300, 600, 600, 800)
        val items = listOf(
            UiNode("", "android.view.ViewGroup", true, true, false, 0, 0, 1920, 1080),
            tile,
            node("S02E04 - The Hunt", 700).copy(left = 320, right = 580),
            node("S02E05 - Lost", 700).copy(left = 620, right = 880),
        )
        assertEquals(4, TiviMateScreens.focusedEpisode(items, 2))
        assertTrue(TiviMateScreens.showsSeason(items, 2, 9, null))
        assertFalse(TiviMateScreens.showsSeason(items, 3, 1, null))
    }

    @Test
    fun namedEntries() {
        assertTrue(TiviMateScreens.isTrackEntry("Closed captions 1", Track.Subtitles))
        assertTrue(TiviMateScreens.isTrackEntry("Audio track", Track.Audio))
        assertFalse(TiviMateScreens.isTrackEntry("Audio offset", Track.Audio))
        assertFalse(TiviMateScreens.isTrackEntry("Subtitle size", Track.Subtitles))
        assertFalse(TiviMateScreens.isTrackEntry("Stereo", Track.Audio))
    }

    @Test
    fun parsesContentDescriptionAndChecked() {
        val xml = """<?xml version='1.0'?><hierarchy rotation="0"><node index="0" text="" class="android.widget.ImageView" content-desc="Audio track" checkable="false" checked="false" clickable="true" focused="false" selected="false" bounds="[10,20][30,40]" /><node text="English" class="android.widget.CheckedTextView" content-desc="" checkable="true" checked="true" clickable="true" focused="true" selected="false" bounds="[0,0][5,5]" /></hierarchy>"""
        val nodes = parseNodes(xml)
        assertEquals("Audio track", nodes[0].label)
        assertTrue(nodes[1].checkable && nodes[1].checked)
    }
}
