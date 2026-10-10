package app.oslo.repertoire

import app.oslo.repertoire.tmdb.CreditGroup
import app.oslo.repertoire.tmdb.Filmography
import app.oslo.repertoire.tmdb.TmdbCombinedCredits
import app.oslo.repertoire.tmdb.TmdbCredit
import app.oslo.repertoire.tmdb.TmdbVideo
import app.oslo.repertoire.tmdb.pickTrailer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmographyTest {
    private fun movie(id: Int, title: String, date: String?, job: String? = null, department: String? = null, character: String? = null, votes: Int = 0) =
        TmdbCredit(id = id, mediaType = "movie", title = title, releaseDate = date, job = job, department = department, character = character, voteCount = votes, posterPath = "/$id.jpg")

    private fun tv(id: Int, name: String, date: String?, character: String? = null, genres: List<Int> = emptyList(), episodes: Int? = null) =
        TmdbCredit(id = id, mediaType = "tv", name = name, firstAirDate = date, character = character, genreIds = genres, episodeCount = episodes)

    // A director who also writes, produces and acts a little (think Nolan or Tarantino).
    private val credits = TmdbCombinedCredits(
        cast = listOf(
            movie(10, "Pulp Fiction", "1994-09-10", character = "Jimmie"),
            movie(11, "Reservoir Dogs", "1992-01-21", character = "Mr. Brown"),
            tv(90, "The Tonight Show", "1992-05-25", character = "Self", genres = listOf(10767)),
            tv(91, "Some Talk", "2001-01-01", character = "Himself - Guest"),
            tv(92, "ER", "1994-09-19", character = "Dr. X", episodes = 1),
            tv(92, "ER", "1994-09-19", character = "Dr. Y", episodes = 2),
        ),
        crew = listOf(
            movie(10, "Pulp Fiction", "1994-09-10", job = "Director", department = "Directing", votes = 27000),
            movie(10, "Pulp Fiction", "1994-09-10", job = "Screenplay", department = "Writing"),
            movie(10, "Pulp Fiction", "1994-09-10", job = "Story", department = "Writing"),
            movie(11, "Reservoir Dogs", "1992-01-21", job = "Director", department = "Directing", votes = 14000),
            movie(12, "Once Upon a Time in Hollywood", "2019-07-24", job = "Director", department = "Directing", votes = 13000),
            movie(13, "The Movie Critic", null, job = "Director", department = "Directing"),
            movie(14, "Kill Bill: Vol. 1", "2003-10-10", job = "Director", department = "Directing", votes = 18000),
            movie(15, "Hostel", "2005-09-17", job = "Executive Producer", department = "Production"),
            movie(16, "From Dusk Till Dawn", "1996-01-19", job = "Second Unit Director", department = "Directing"),
            movie(17, "Some Film", "2010-01-01", job = "Thanks", department = "Crew"),
        ),
    )

    @Test
    fun splitsIntoGroups() {
        val groups = Filmography.build(credits)
        assertEquals(listOf(CreditGroup.Directing, CreditGroup.Acting, CreditGroup.Writing, CreditGroup.Production, CreditGroup.Crew), groups.keys.toList())
        assertEquals(listOf("Second Unit Director"), groups.getValue(CreditGroup.Crew).single().roles)
        assertFalse(groups.values.flatten().any { it.title == "Some Film" })
    }

    @Test
    fun directingIsChronologicalWithUndatedLast() {
        val directing = Filmography.build(credits).getValue(CreditGroup.Directing)
        assertEquals(
            listOf("Reservoir Dogs", "Pulp Fiction", "Kill Bill: Vol. 1", "Once Upon a Time in Hollywood", "The Movie Critic"),
            directing.map { it.title },
        )
        val newest = Filmography.sort(directing, newestFirst = true)
        assertEquals(listOf("Once Upon a Time in Hollywood", "Kill Bill: Vol. 1", "Pulp Fiction", "Reservoir Dogs", "The Movie Critic"), newest.map { it.title })
    }

    @Test
    fun deduplicatesAndMergesRoles() {
        val groups = Filmography.build(credits)
        val writing = groups.getValue(CreditGroup.Writing)
        assertEquals(1, writing.size)
        assertEquals("Screenplay · Story", writing.single().roleLine)
        val er = groups.getValue(CreditGroup.Acting).single { it.title == "ER" }
        assertEquals(listOf("Dr. X", "Dr. Y"), er.roles)
        assertEquals(2, er.episodeCount)
    }

    @Test
    fun dropsTalkShowsAndSelfAppearances() {
        val acting = Filmography.build(credits).getValue(CreditGroup.Acting).map { it.title }
        assertEquals(listOf("Reservoir Dogs", "Pulp Fiction", "ER"), acting)
    }

    @Test
    fun primaryGroupAndKnownFor() {
        val groups = Filmography.build(credits)
        assertEquals(CreditGroup.Directing, Filmography.primaryGroup(groups, "Directing"))
        assertEquals(CreditGroup.Acting, Filmography.primaryGroup(groups, "Acting"))
        assertEquals(CreditGroup.Directing, Filmography.primaryGroup(groups, "Sound"))
        val known = Filmography.knownFor(groups, limit = 2).map { it.title }
        assertEquals(listOf("Pulp Fiction", "Kill Bill: Vol. 1"), known)
        assertTrue(Filmography.build(TmdbCombinedCredits()).isEmpty())
    }

    @Test
    fun picksYoutubeTrailers() {
        val videos = listOf(
            TmdbVideo("a", "Vimeo", "Trailer", official = true),
            TmdbVideo("b", "YouTube", "Teaser", official = true),
            TmdbVideo("c", "YouTube", "Trailer", official = false),
            TmdbVideo("d", "YouTube", "Trailer", official = true),
        )
        assertEquals("d", pickTrailer(videos)?.key)
        assertEquals("b", pickTrailer(videos.take(2))?.key)
        assertNull(pickTrailer(videos.take(1)))
    }
}
