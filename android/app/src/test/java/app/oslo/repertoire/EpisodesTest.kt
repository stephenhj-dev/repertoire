package app.oslo.repertoire

import app.oslo.repertoire.series.Episode
import app.oslo.repertoire.series.EpisodeMerge
import app.oslo.repertoire.series.NextUp
import app.oslo.repertoire.series.ProviderSeries
import app.oslo.repertoire.series.Season
import app.oslo.repertoire.series.SeriesProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodesTest {
    private fun ep(s: Int, n: Int, id: String? = "$s$n", title: String? = null) = Episode(s, n, title = title, streamId = id)

    @Test
    fun mergePrefersTmdbTextKeepsProviderStream() {
        val tmdb = listOf(Episode(1, 1, "Pilot", "It begins", 58, "https://img/still.jpg"), Episode(1, 3, "Third"))
        val provider = listOf(Episode(1, 1, "S01E01", "plot", 60, null, streamId = "901"), Episode(1, 2, "Second", streamId = "902"))
        val merged = EpisodeMerge.merge(tmdb, provider)
        assertEquals(listOf(1 to 1, 1 to 2, 1 to 3), merged.map { it.season to it.number })
        assertEquals(Episode(1, 1, "Pilot", "It begins", 58, "https://img/still.jpg", null, "901"), merged[0])
        assertEquals("902", merged[1].streamId)
        assertNull(merged[2].streamId)
    }

    @Test
    fun seasonsPutSpecialsLastAndAddProviderOnlySeasons() {
        val tmdb = listOf(Season(0, "Specials", 2), Season(1, "Season 1", 8), Season(2, "Season 2", 0))
        val seasons = EpisodeMerge.seasons(tmdb, listOf(ep(1, 1), ep(3, 1), ep(3, 2)))
        assertEquals(listOf(1, 3, 0), seasons.map { it.number })
        assertEquals(2, seasons[1].episodeCount)
    }

    @Test
    fun resumeOffersNextEpisodeAcrossSeasons() {
        val episodes = listOf(ep(1, 1), ep(1, 2), ep(2, 1), ep(2, 2, id = null), ep(2, 3))
        val action = NextUp.action(episodes, SeriesProgress(1, 2))!!
        assertEquals("Resume S1 E2", action.label)
        assertEquals(2 to 1, action.next!!.season to action.next!!.number)
        // Skips episodes the provider doesn't have.
        assertEquals(2 to 3, NextUp.following(episodes, SeriesProgress(2, 1))!!.let { it.season to it.number })
        assertNull(NextUp.following(episodes, SeriesProgress(2, 3)))
    }

    @Test
    fun withoutProgressPlaysFirstRegularEpisode() {
        val episodes = listOf(ep(0, 1), ep(1, 2), ep(1, 1))
        val action = NextUp.action(episodes, null)!!
        assertEquals("Play S1 E1", action.label)
        assertEquals("12", action.next!!.streamId)
        assertNull(NextUp.action(listOf(ep(1, 1, id = null)), null))
    }

    @Test
    fun progressOnAMissingEpisodeMovesOn() {
        val action = NextUp.action(listOf(ep(1, 1), ep(1, 3)), SeriesProgress(1, 2))!!
        assertEquals("Next episode · S1 E3", action.label)
    }

    @Test
    fun initialSeasonFollowsProgress() {
        val seasons = listOf(Season(1, "S1", 3), Season(2, "S2", 3), Season(0, "Specials", 1))
        assertEquals(2, NextUp.initialSeason(seasons, SeriesProgress(2, 1)))
        assertEquals(1, NextUp.initialSeason(seasons, SeriesProgress(7, 1)))
        assertEquals(1, NextUp.initialSeason(seasons, null))
    }

    @Test
    fun parsesProviderSeriesInfo() {
        val body = """
            {"seasons":[],"info":{"name":"Dark"},
             "episodes":{"1":[{"id":"501","episode_num":"1","title":"Dark - S01E01 - Secrets","container_extension":"mkv","season":1,
                               "info":{"plot":"A boy vanishes.","duration_secs":"3060","movie_image":"https://x/1.jpg"}},
                              {"id":"502","episode_num":2,"title":"EN - Dark S01E02","info":{"duration":"00:44:30"}}],
                         "2":[{"id":"601","title":"Episode 1","info":[]}]}}
        """.trimIndent()
        val episodes = ProviderSeries.parse(body, "Dark")
        assertEquals(listOf("501", "502", "601"), episodes.map { it.streamId })
        assertEquals("Secrets", episodes[0].title)
        assertEquals(51, episodes[0].runtimeMinutes)
        assertNull(episodes[1].title)
        assertEquals(45, episodes[1].runtimeMinutes)
        assertEquals(2 to 1, episodes[2].season to episodes[2].number)
        assertEquals(emptyList<Episode>(), ProviderSeries.parse("nope"))
        assertEquals(emptyList<Episode>(), ProviderSeries.parse("""{"episodes":[]}"""))
    }

    @Test
    fun cleansEpisodeTitles() {
        assertEquals("The Title", ProviderSeries.cleanTitle("Show Name - S01E02 - The Title", "Show Name"))
        assertEquals("Ozymandias", ProviderSeries.cleanTitle("Ozymandias", "Breaking Bad"))
        assertNull(ProviderSeries.cleanTitle("Breaking Bad", "Breaking Bad"))
        assertNull(ProviderSeries.cleanTitle("EN - Show S10E12", "Show"))
        assertEquals("Season's End", ProviderSeries.cleanTitle("S2E10: Season's End", null))
    }
}
