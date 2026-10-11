package app.oslo.repertoire

import app.oslo.repertoire.library.PlaybackPrefs
import app.oslo.repertoire.library.Quality
import app.oslo.repertoire.library.TitleDisplay
import app.oslo.repertoire.library.Version
import app.oslo.repertoire.library.VersionPicker
import app.oslo.repertoire.library.VersionTagParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTagParserTest {
    private fun tags(raw: String) = VersionTagParser.parse(raw)

    @Test
    fun readsLanguagePrefixes() {
        assertEquals(listOf("EN"), tags("EN - Heat (1995)").languages)
        assertEquals(listOf("FR"), tags("FR - Heat").languages)
        assertEquals(listOf("DE"), tags("|DE| Heat").languages)
        assertEquals(listOf("ES"), tags("[ES] Heat").languages)
        assertEquals(listOf("NL"), tags("NL: Heat").languages)
        assertEquals(listOf("PT"), tags("PT-BR - Cidade de Deus").languages)
        assertEquals(listOf("TR"), tags("TR - Heat").languages)
    }

    @Test
    fun readsQualityAndHdr() {
        val heat = tags("DE - Heat 4K")
        assertEquals(listOf("DE"), heat.languages)
        assertEquals(Quality.UHD, heat.quality)
        assertEquals(Quality.FHD, tags("EN - Heat FHD").quality)
        assertEquals(Quality.HD, tags("Heat [HD]").quality)
        assertEquals(Quality.SD, tags("Heat (SD)").quality)
        assertEquals(Quality.UHD, tags("Dune.2021.2160p.HDR").quality)
        assertTrue(tags("Dune (2021) [4K HDR]").hdr)
        assertTrue(tags("Dune 4K DV").dolbyVision)
        assertTrue(tags("Dune 4K Dolby Vision").dolbyVision)
        assertEquals("EN 4K HDR", tags("EN - Dune (2021) [4K HDR]").label)
    }

    @Test
    fun readsMultiSubAndDub() {
        val multiSub = tags("MULTI-SUB Heat")
        assertTrue(multiSub.multi && multiSub.sub)
        assertEquals("MULTI-SUB", multiSub.label)
        assertTrue(tags("[MULTI SUB] Heat").sub)
        assertTrue(tags("MULTI - Heat").multi)
        assertFalse(tags("MULTI - Heat").sub)
        val vostfr = tags("Heat VOSTFR")
        assertEquals(listOf("FR"), vostfr.languages)
        assertTrue(vostfr.sub)
        assertTrue(tags("IT - Heat DUB").dub)
    }

    @Test
    fun titleWordsAreNotLanguageCodes() {
        assertEquals(emptyList<String>(), tags("It (2017)").languages)
        assertEquals(listOf("EN"), tags("EN - It (2017)").languages)
        assertEquals(listOf("EN"), tags("EN - IT (2017)").languages)
        assertEquals(emptyList<String>(), tags("No Time to Die").languages)
        assertEquals(listOf("ES"), tags("ES - La Casa de Papel").languages)
        assertEquals(emptyList<String>(), tags("X-Men: Apocalypse").languages)
        assertEquals(listOf("FR"), tags("Heat FR").languages)
    }

    @Test
    fun tokenizesWithoutRegex() {
        assertEquals(listOf("EN", "Heat", "1995", "4K"), VersionTagParser.tokens("EN - Heat (1995) {4K}"))
        assertEquals(listOf("MULTI-SUB", "Heat"), VersionTagParser.tokens("MULTI-SUB Heat"))
        assertEquals(listOf("X", "Men"), VersionTagParser.tokens("X-Men"))
    }

    @Test
    fun localeLanguageFallsBackToEnglish() {
        assertEquals("FR", VersionTagParser.languageForLocale("fr"))
        assertEquals("NO", VersionTagParser.languageForLocale("nb"))
        assertEquals("EN", VersionTagParser.languageForLocale("xx"))
        assertEquals("EN", VersionTagParser.languageForLocale(null))
    }
}

class VersionPickerTest {
    private val heat = listOf("EN - Heat (1995)", "FR - Heat", "DE - Heat 4K", "MULTI-SUB Heat", "EN - Heat 4K").map(::Version)

    private fun best(prefs: PlaybackPrefs, versions: List<Version> = heat) = VersionPicker.best(versions, prefs)?.name

    @Test
    fun picksPreferredLanguage() {
        assertEquals("FR - Heat", best(PlaybackPrefs("FR")))
        assertEquals("DE - Heat 4K", best(PlaybackPrefs("DE")))
    }

    @Test
    fun fallsBackToEnglish() {
        assertEquals("EN - Heat (1995)", best(PlaybackPrefs("NO")))
    }

    @Test
    fun prefer4kPicksUhdWithinLanguage() {
        assertEquals("EN - Heat 4K", best(PlaybackPrefs("EN", prefer4k = true)))
        assertEquals("EN - Heat (1995)", best(PlaybackPrefs("EN", prefer4k = false)))
    }

    @Test
    fun subtitlesOnlyRankBelowAudio() {
        val versions = listOf("Heat VOSTFR", "MULTI - Heat").map(::Version)
        assertEquals("MULTI - Heat", best(PlaybackPrefs("FR"), versions))
        assertEquals("Heat VOSTFR", best(PlaybackPrefs("FR"), listOf(Version("Heat VOSTFR"), Version("DE - Heat"))))
    }

    @Test
    fun summaryPutsPreferredFirst() {
        val summary = VersionPicker.summary(heat, preferred = "DE")
        assertEquals(listOf("DE", "EN", "FR", "MULTI-SUB"), summary.languages)
        assertEquals(Quality.UHD, summary.quality)
        assertEquals("DE · EN · FR · MULTI-SUB  4K", summary.line)
        assertEquals("+4 versions", summary.moreVersions)
        assertNull(VersionPicker.summary(heat.take(1), "EN").moreVersions)
    }
}

class TitleDisplayTest {
    @Test
    fun usesTmdbTitleWhenMatched() {
        val shown = TitleDisplay.of("movie", "EN - Heat 4K", tmdbTitle = "Heat", tmdbYear = 1995, tmdbPoster = "p", catalogIcon = "c")
        assertEquals("Heat", shown.title)
        assertEquals(1995, shown.year)
        assertEquals("p", shown.poster)
        assertTrue(shown.matched)
        assertEquals("Heat (1995)", TitleDisplay.line(shown))
    }

    @Test
    fun fallsBackToNormalizedName() {
        val shown = TitleDisplay.of("movie", "EN - Heat (1995) [4K]", catalogIcon = "c")
        assertEquals("Heat", shown.title)
        assertEquals(1995, shown.year)
        assertEquals("c", shown.poster)
        assertFalse(shown.matched)
        assertEquals("Heat", TitleDisplay.of("movie", "MULTI-SUB Heat").title)
        assertEquals("Heat", TitleDisplay.of("movie", "Heat FR").title)
        assertEquals("IT", TitleDisplay.of("movie", "IT").title)
        assertEquals("X-Men: Apocalypse", TitleDisplay.of("movie", "X-Men: Apocalypse").title)
        assertEquals("Heat", TitleDisplay.of("movie", "Heat", tmdbTitle = "  ").title)
    }

    @Test
    fun liveChannelsKeepTheirName() {
        assertEquals("US: HBO HD", TitleDisplay.of("live", "US: HBO HD").title)
    }
}
