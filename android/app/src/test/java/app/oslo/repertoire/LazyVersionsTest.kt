package app.oslo.repertoire

import app.oslo.repertoire.data.VersionDetail
import app.oslo.repertoire.library.VersionTagParser
import app.oslo.repertoire.tmdb.likeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LikeWordTest {
    @Test
    fun picksLongestPlainWord() {
        assertEquals("Identity", likeWord("The Bourne Identity"))
        assertEquals("Heat", likeWord("Heat"))
        assertEquals("Runner", likeWord("Blade Runner"))
        assertEquals("Alien", likeWord("Alien Rises"))
        assertEquals("2049", likeWord("2049"))
    }

    @Test
    fun skipsArticlesShortAndAccentedWords() {
        assertEquals("Impossible", likeWord("Mission: Impossible"))
        assertEquals("Fabuleux", likeWord("Le Fabuleux destin d'Amélie Poulain"))
        assertNull(likeWord("Amélie"))
        assertNull(likeWord("Up"))
        assertNull(likeWord("初恋"))
        assertNull(likeWord("The"))
    }
}

class VersionDetailTest {
    @Test
    fun parsesVodInfo() {
        val body = """
            {"info":{"duration_secs":"6660","duration":"01:51:00","bitrate":5321,
             "video":{"codec_name":"h264","width":1920,"height":1080},
             "audio":{"codec_name":"aac","channels":6,"tags":{"language":"eng"}}},
             "movie_data":{"stream_id":1,"container_extension":"mkv"}}
        """.trimIndent()
        val detail = VersionDetail.parse(body)!!
        assertEquals(1920, detail.width)
        assertEquals(6660, detail.durationSecs)
        assertEquals("1920×1080 · H264 · AAC 5.1 · eng · 1h 51m · 5.3 Mb/s · mkv", detail.line)
    }

    @Test
    fun readsClockDurationAndSeriesEpisodes() {
        val movie = VersionDetail.parse("""{"info":{"duration":"00:42:10"}}""")!!
        assertEquals(2530, movie.durationSecs)
        val series = """
            {"info":{"name":"Show"},"episodes":{"1":[{"id":"9","container_extension":"mp4",
             "info":{"duration_secs":2700,"video":{"codec_name":"hevc","width":3840,"height":2160}}}]}}
        """.trimIndent()
        assertEquals("3840×2160 · HEVC · 45m · mp4", VersionDetail.parse(series)!!.line)
    }

    @Test
    fun toleratesJunk() {
        assertNull(VersionDetail.parse("not json"))
        assertNull(VersionDetail.parse("[]"))
        assertNull(VersionDetail.parse("""{"info":[]}"""))
        assertNull(VersionDetail.parse("""{"info":{"video":[],"audio":"x","duration":"soon"}}"""))
    }
}

class VersionTagCacheTest {
    @Test
    fun cachedMatchesParse() {
        val name = "4K-FR-HDR - The Bourne Identity (2002)"
        assertEquals(VersionTagParser.parse(name), VersionTagParser.cached(name))
        assertSame(VersionTagParser.cached(name), VersionTagParser.cached(name))
    }
}
