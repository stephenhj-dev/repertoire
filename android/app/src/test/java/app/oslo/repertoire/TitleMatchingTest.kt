package app.oslo.repertoire

import app.oslo.repertoire.tmdb.LibraryEntry
import app.oslo.repertoire.tmdb.LibraryIndex
import app.oslo.repertoire.tmdb.ParsedTitle
import app.oslo.repertoire.tmdb.SearchCandidate
import app.oslo.repertoire.tmdb.TitleNormalizer
import app.oslo.repertoire.tmdb.TmdbRef
import app.oslo.repertoire.tmdb.pickSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleNormalizerTest {
    private fun parsed(raw: String) = TitleNormalizer.parse(raw)

    @Test
    fun stripsLanguageAndProviderPrefixes() {
        assertEquals(ParsedTitle("The Matrix", 1999), parsed("EN - The Matrix (1999)"))
        assertEquals(ParsedTitle("Oppenheimer", 2023), parsed("|EN| Oppenheimer (2023)"))
        assertEquals(ParsedTitle("Inception", null), parsed("EN: Inception"))
        assertEquals(ParsedTitle("Avatar", 2009), parsed("[MULTI] Avatar (2009)"))
        assertEquals(ParsedTitle("Squid Game", null), parsed("NF - Squid Game"))
        assertEquals(ParsedTitle("Dune: Part Two", 2024), parsed("4K - EN - Dune: Part Two (2024)"))
        assertEquals(ParsedTitle("Le Fabuleux Destin d'Amélie Poulain", 2001), parsed("FR | Le Fabuleux Destin d'Amélie Poulain (2001)"))
    }

    @Test
    fun keepsTitlesThatLookLikePrefixes() {
        assertEquals("X-Men: Apocalypse", parsed("X-Men: Apocalypse").title)
        assertEquals("X-MEN: Apocalypse", parsed("X-MEN: Apocalypse").title)
        assertEquals("JFK: Revisited", parsed("JFK: Revisited").title)
        assertEquals("Alien: Romulus", parsed("Alien: Romulus (2024)").title)
        assertEquals("F1", parsed("F1").title)
    }

    @Test
    fun stripsQualityTags() {
        assertEquals(ParsedTitle("Dune", 2021), parsed("Dune (2021) [4K]"))
        assertEquals(ParsedTitle("Oppenheimer", 2023), parsed("Oppenheimer (2023) FHD"))
        assertEquals(ParsedTitle("Top Gun Maverick", 2022), parsed("Top Gun Maverick 2022 1080p"))
        assertEquals(ParsedTitle("Interstellar", null), parsed("Interstellar 4K HDR"))
        assertEquals(ParsedTitle("Gladiator", 2000), parsed("Gladiator (2000) (MULTI-SUB)"))
        assertEquals(ParsedTitle("Movie Name", 2020), parsed("Movie.Name.2020.HDR"))
    }

    @Test
    fun yearsAreOnlyTakenWhenPlausible() {
        assertEquals(ParsedTitle("1917", 2019), parsed("1917 (2019)"))
        assertEquals(ParsedTitle("2012", null), parsed("2012"))
        assertEquals(ParsedTitle("Blade Runner 2049", null), parsed("Blade Runner 2049"))
        assertEquals(ParsedTitle("Blade Runner 2049", 2017), parsed("EN - Blade Runner 2049 (2017)"))
    }

    @Test
    fun stripsSeasonMarkersFromShows() {
        assertEquals("Squid Game", parsed("NF - Squid Game S01").title)
        assertEquals("The Bear", parsed("EN - The Bear Season 2").title)
    }

    @Test
    fun keysFoldCaseAccentsPunctuationAndArticles() {
        assertEquals("matrix", TitleNormalizer.key("The Matrix"))
        assertEquals("amelie", TitleNormalizer.key("Amélie"))
        assertEquals("fast and furious", TitleNormalizer.key("Fast & Furious"))
        assertEquals("schindlers list", TitleNormalizer.key("Schindler’s List"))
        assertEquals(TitleNormalizer.key("Spider-Man: No Way Home"), TitleNormalizer.key("Spider Man No Way Home"))
        assertEquals(2021, TitleNormalizer.yearOf("2021-10-22"))
        assertNull(TitleNormalizer.yearOf(""))
    }
}

class CatalogMatchingTest {
    private val catalog = LibraryIndex(
        listOf(
            LibraryEntry(1, "movie", "EN - Dune (2021)"),
            LibraryEntry(2, "movie", "4K - Dune (2021) [HDR]"),
            LibraryEntry(3, "movie", "EN - Dune (1984)"),
            LibraryEntry(4, "movie", "EN - Heat", tmdbId = 949),
            LibraryEntry(5, "movie", "EN - The Thing (2011)", tmdbId = 60935),
            LibraryEntry(6, "show", "EN - The Bear"),
            LibraryEntry(7, "movie", "Amélie (2001)"),
            LibraryEntry(8, "movie", "EN - Gattaca", year = 1997),
        ),
    )

    @Test
    fun matchesByTmdbIdFirst() {
        val found = catalog.find(TmdbRef("movie", 949, "Heat", year = 1995))
        assertEquals(listOf(4L), found.map { it.id })
        // Same id even if the name differs.
        assertEquals(listOf(4L), catalog.find(TmdbRef("movie", 949, "Something else")).map { it.id })
    }

    @Test
    fun matchesByTitleAndYearAndOrdersVersions() {
        val found = catalog.find(TmdbRef("movie", 438631, "Dune", year = 2021))
        assertEquals(listOf(1L, 2L), found.map { it.id })
        assertEquals(listOf(3L), catalog.find(TmdbRef("movie", 841, "Dune", year = 1984)).map { it.id })
    }

    @Test
    fun rejectsADifferentTmdbIdWithTheSameName() {
        // The 1982 film is not the 2011 prequel the portal tagged.
        assertTrue(catalog.find(TmdbRef("movie", 1091, "The Thing", year = 1982)).isEmpty())
    }

    @Test
    fun usesOriginalTitlesKindsAndStoredYears() {
        assertEquals(listOf(7L), catalog.find(TmdbRef("movie", 194, "Amelie", "Le Fabuleux Destin d'Amélie Poulain", 2001)).map { it.id })
        assertEquals(listOf(6L), catalog.find(TmdbRef("tv", 136315, "The Bear", year = 2022)).map { it.id })
        assertTrue(catalog.find(TmdbRef("movie", 1, "The Bear", year = 2022)).isEmpty())
        assertEquals(listOf(8L), catalog.find(TmdbRef("movie", 782, "Gattaca", year = 1998)).map { it.id })
        assertTrue(catalog.find(TmdbRef("movie", 9, "Gattaca", year = 2005)).isEmpty())
    }

    @Test
    fun picksTheRightSearchResult() {
        val results = listOf(
            SearchCandidate(841, "Dune", year = 1984, popularity = 20.0),
            SearchCandidate(438631, "Dune", year = 2021, popularity = 90.0),
            SearchCandidate(693134, "Dune: Part Two", year = 2024, popularity = 95.0),
        )
        assertEquals(438631, pickSearchResult(ParsedTitle("Dune", 2021), results)?.id)
        assertEquals(841, pickSearchResult(ParsedTitle("Dune", 1984), results)?.id)
        assertEquals(438631, pickSearchResult(ParsedTitle("Dune", null), results)?.id)
        assertEquals(693134, pickSearchResult(ParsedTitle("Dune Part Two", 2024), results)?.id)
        assertNull(pickSearchResult(ParsedTitle("Arrival", 2016), results))
        // Year-guided prefix match: portal says "Dune" for 2024's sequel.
        assertEquals(693134, pickSearchResult(ParsedTitle("Dune", 2024), results)?.id)
    }
}
