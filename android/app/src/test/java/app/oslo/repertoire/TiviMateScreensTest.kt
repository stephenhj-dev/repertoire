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

    @Test
    fun findsPlayerMenuEntriesByTextOrDescription() {
        val menu = listOf(node("", 900, desc = "Audio track"), node("CC", 900), node("Video tracks", 900), node("Channels list", 900))
        assertTrue(TiviMateScreens.playerMenuShown(menu))
        assertEquals("Audio track", TiviMateScreens.trackEntry(menu, Track.Audio)!!.label)
        assertEquals("CC", TiviMateScreens.trackEntry(menu, Track.Subtitles)!!.label)
        assertFalse(TiviMateScreens.isTrackEntry("Audio offset delay setting long", Track.Audio))
        assertTrue(TiviMateScreens.isTrackEntry("Closed captions: Off", Track.Subtitles))
        assertFalse(TiviMateScreens.isTrackEntry("Audio offset", Track.Audio))
        assertFalse(TiviMateScreens.isTrackEntry("Subtitle size", Track.Subtitles))
        assertFalse(TiviMateScreens.playerMenuShown(listOf(node("Search"), node("TV"))))
    }

    @Test
    fun readsTrackOptionsThatAppeared() {
        val menu = listOf(node("Audio track", 900), node("CC", 900))
        val dialog = menu + listOf(
            node("Audio tracks", 100, clickable = false),
            node("English (AC3 5.1)", 200, checkable = true, checked = true),
            node("Deutsch", 260, checkable = true),
            node("Settings", 320),
        )
        val options = TiviMateScreens.trackOptions(menu, dialog, Track.Audio)
        assertEquals(listOf("English (AC3 5.1)", "Deutsch"), options.map { it.label })
        assertEquals(listOf(true, false), options.map { it.selected })
        val subs = listOf(node("Closed captions", 100, clickable = false), node("Off", 200, checked = true), node("English", 260),
            node("Show closed captions for all channels", 320), node("System settings for closed captions", 380))
        assertEquals(listOf("Off", "English"), TiviMateScreens.trackOptions(menu, menu + subs, Track.Subtitles).map { it.label })
    }

    @Test
    fun parsesContentDescriptionAndChecked() {
        val xml = """<?xml version='1.0'?><hierarchy rotation="0"><node index="0" text="" class="android.widget.ImageView" content-desc="Audio track" checkable="false" checked="false" clickable="true" focused="false" selected="false" bounds="[10,20][30,40]" /><node text="English" class="android.widget.CheckedTextView" content-desc="" checkable="true" checked="true" clickable="true" focused="true" selected="false" bounds="[0,0][5,5]" /></hierarchy>"""
        val nodes = parseNodes(xml)
        assertEquals("Audio track", nodes[0].label)
        assertTrue(nodes[1].checkable && nodes[1].checked)
    }
}
