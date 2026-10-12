package app.oslo.repertoire

import app.oslo.repertoire.repertoire.BodyOfWork
import app.oslo.repertoire.repertoire.WorkItem
import app.oslo.repertoire.tmdb.CreditGroup
import app.oslo.repertoire.tmdb.FilmCredit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyOfWorkTest {
    private fun credit(id: Int, date: String?, type: String = "movie", episodes: Int? = null, votes: Int = 10) =
        FilmCredit(type, id, "T$id", null, date, null, emptyList(), episodes, 1.0, votes)

    private fun item(id: Int, date: String, available: Boolean) = WorkItem(credit(id, date), if (available) listOf("EN - T$id") else emptyList())

    @Test
    fun mainCreditsFollowTheDepartmentAndSkipGuestSpots() {
        val groups = mapOf(
            CreditGroup.Acting to listOf(
                credit(3, "2001-05-01"),
                credit(1, "1990-01-01"),
                credit(9, "1995-01-01", type = "tv", episodes = 1), // guest spot
                credit(8, "1998-01-01", type = "tv", episodes = 40), // series regular
                credit(7, null), // announced
            ),
            CreditGroup.Directing to listOf(credit(5, "2010-01-01")),
        )
        assertEquals(listOf(1, 8, 3), BodyOfWork.mainCredits(groups, "Acting").map { it.id })
        assertEquals(listOf(5), BodyOfWork.mainCredits(groups, "Directing").map { it.id })
        // Directors keep TV they directed, whatever the episode count.
        val director = mapOf(CreditGroup.Directing to listOf(credit(5, "2010-01-01"), credit(6, "2012-01-01", type = "tv", episodes = 1)))
        assertEquals(listOf(5, 6), BodyOfWork.mainCredits(director, "Directing").map { it.id })
    }

    @Test
    fun progressCountsAndNextIsEarliestUnwatchedAvailable() {
        val items = listOf(item(1, "1970", true), item(2, "1972", false), item(3, "1974", true), item(4, "1979", true))
        val p = BodyOfWork.progress(items, setOf("movie:1", "movie:2"))
        assertEquals(2, p.watched)
        assertEquals(4, p.total)
        assertEquals(3, p.available)
        assertEquals(1, p.availableWatched)
        assertEquals(3, p.next?.credit?.id)
        assertEquals("2 of 4 watched", p.label)
        assertFalse(p.completed)
    }

    @Test
    fun completedWhenEveryAvailableTitleIsWatched() {
        val items = listOf(item(1, "1970", true), item(2, "1972", false), item(3, "1974", true))
        val done = BodyOfWork.progress(items, setOf("movie:1", "movie:3"))
        assertTrue(done.completed)
        assertNull(done.next)
        val before = BodyOfWork.progress(items, setOf("movie:1"))
        assertTrue(BodyOfWork.justCompleted(before, done))
        assertFalse(BodyOfWork.justCompleted(done, done))
        assertFalse(BodyOfWork.justCompleted(null, done))
        // Nothing available: never "completed".
        assertFalse(BodyOfWork.progress(listOf(item(2, "1972", false)), emptySet()).completed)
    }

    @Test
    fun collagePicksBestKnownAndKeepsOrder() {
        fun w(id: Int, votes: Int, poster: String? = "/p$id.jpg") =
            WorkItem(FilmCredit("movie", id, "T$id", null, "19${10 + id}-01-01", poster, emptyList(), null, 1.0, votes), emptyList())
        val items = listOf(w(1, 5), w(2, 900), w(3, 50, poster = null), w(4, 700), w(5, 10), w(6, 800))
        assertEquals(listOf(2, 4, 6), BodyOfWork.collage(items, 3).map { it.credit.id })
    }
}
