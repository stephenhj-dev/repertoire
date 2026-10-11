package app.oslo.repertoire

import app.oslo.repertoire.watchlist.PlayOrder
import app.oslo.repertoire.watchlist.QueueItem
import app.oslo.repertoire.watchlist.WatchlistOrder
import app.oslo.repertoire.watchlist.WatchlistShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistOrderTest {
    @Test
    fun movesDownAndUp() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), WatchlistOrder.move(list, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), WatchlistOrder.move(list, 3, 0))
        assertEquals(list, WatchlistOrder.move(list, 1, 1))
    }

    @Test
    fun clampsAndIgnoresBadIndexes() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), WatchlistOrder.move(list, 0, 99))
        assertEquals(list, WatchlistOrder.move(list, 5, 0))
    }

    @Test
    fun movesByKey() {
        val list = listOf(1L to "a", 2L to "b", 3L to "c")
        assertEquals(listOf(2L, 3L, 1L), WatchlistOrder.moveByKey(list, { it.first }, 1L, 3L).map { it.first })
        assertEquals(list, WatchlistOrder.moveByKey(list, { it.first }, 9L, 3L))
    }
}

class PlayOrderTest {
    private val items = listOf(
        QueueItem("Watched", listOf("EN - A"), watched = true),
        QueueItem("Missing", emptyList()),
        QueueItem("Heat", listOf("EN - Heat")),
        QueueItem("Missing too", emptyList()),
        QueueItem("Seen", listOf("EN - Seen"), watched = true),
        QueueItem("Alien", listOf("EN - Alien", "FR - Alien")),
    )

    @Test
    fun firstSkipsWatchedAndMissing() {
        assertEquals(2, PlayOrder.first(items))
    }

    @Test
    fun nextSkipsWatchedAndMissing() {
        assertEquals(5, PlayOrder.next(items, 2))
        assertNull(PlayOrder.next(items, 5))
        assertEquals(1, PlayOrder.remaining(items, 2))
    }

    @Test
    fun nothingPlayable() {
        assertNull(PlayOrder.first(listOf(QueueItem("x", emptyList()), QueueItem("y", listOf("y"), watched = true))))
        assertNull(PlayOrder.first(emptyList()))
    }
}

class WatchlistShareTest {
    private val list = WatchlistShare.SharedList(
        name = "Michael Mann · Directing",
        items = listOf(
            WatchlistShare.SharedItem("movie", 949, "Heat", 1995, "/heat.jpg"),
            WatchlistShare.SharedItem("show", 1399, "Game of Thrones — “Winter”", 2011),
            WatchlistShare.SharedItem("movie", 0, "Obscure", catalogName = "EN - Obscure Film"),
        ),
    )

    @Test
    fun roundTrips() {
        val code = WatchlistShare.encode(list)
        assertTrue(code.startsWith(WatchlistShare.PREFIX))
        val payload = code.removePrefix(WatchlistShare.PREFIX)
        assertTrue(payload.none { it == '+' || it == '/' || it == '=' })
        assertEquals(list, WatchlistShare.decode(code))
    }

    @Test
    fun findsTheCodeInsideAMessage() {
        val message = WatchlistShare.message(list) + "\nsent from my phone"
        assertEquals(list, WatchlistShare.decode("Look: $message"))
    }

    @Test
    fun rejectsJunk() {
        assertNull(WatchlistShare.decode("hello"))
        assertNull(WatchlistShare.decode(WatchlistShare.PREFIX))
        assertNull(WatchlistShare.decode(WatchlistShare.PREFIX + "!!!"))
        assertNull(WatchlistShare.decode(WatchlistShare.PREFIX + "bm90IGpzb24"))
    }

    @Test
    fun dropsInvalidItems() {
        val bad = WatchlistShare.SharedList(name = "x", items = listOf(WatchlistShare.SharedItem("live", 1, "Channel"), WatchlistShare.SharedItem("movie", 0, "No id")))
        assertEquals(emptyList<WatchlistShare.SharedItem>(), WatchlistShare.decode(WatchlistShare.encode(bad))?.items)
    }
}
