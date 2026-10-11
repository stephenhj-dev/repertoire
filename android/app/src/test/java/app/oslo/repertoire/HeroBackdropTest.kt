package app.oslo.repertoire

import app.oslo.repertoire.tmdb.Filmography
import app.oslo.repertoire.tmdb.TmdbCombinedCredits
import app.oslo.repertoire.tmdb.TmdbCredit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroBackdropTest {
    private fun credit(id: Int, votes: Int, backdrop: String?, media: String = "movie", job: String? = null, character: String? = null, genres: List<Int> = emptyList()) =
        TmdbCredit(id = id, mediaType = media, backdropPath = backdrop, voteCount = votes, job = job, department = if (job == "Director") "Directing" else null, character = character, genreIds = genres)

    @Test
    fun actorsGetTheirMostVotedFilm() {
        val credits = TmdbCombinedCredits(
            cast = listOf(credit(1, 500, "/a.jpg"), credit(2, 30_000, "/b.jpg"), credit(3, 90_000, null), credit(4, 99_000, "/tv.jpg", media = "tv")),
            crew = listOf(credit(5, 80_000, "/directed.jpg", job = "Director")),
        )
        assertEquals("/b.jpg", Filmography.heroBackdrop(credits, "Acting"))
    }

    @Test
    fun directorsGetWhatTheyDirected() {
        val credits = TmdbCombinedCredits(
            cast = listOf(credit(1, 90_000, "/cameo.jpg")),
            crew = listOf(credit(5, 10_000, "/directed.jpg", job = "Director"), credit(6, 50_000, "/produced.jpg", job = "Producer")),
        )
        assertEquals("/directed.jpg", Filmography.heroBackdrop(credits, "Directing"))
    }

    @Test
    fun fallsBackToAnythingButTalkShowsAndNothingIsNull() {
        val talk = credit(9, 1_000, "/talk.jpg", media = "tv", genres = listOf(10767))
        val self = credit(10, 1_000, "/self.jpg", media = "tv", character = "Himself")
        val series = credit(11, 100, "/series.jpg", media = "tv")
        assertEquals("/series.jpg", Filmography.heroBackdrop(TmdbCombinedCredits(cast = listOf(talk, self, series)), "Acting"))
        assertNull(Filmography.heroBackdrop(TmdbCombinedCredits(cast = listOf(talk, self)), "Acting"))
        assertNull(Filmography.heroBackdrop(TmdbCombinedCredits(), null))
    }
}
