package app.oslo.tivimate.guide

import app.oslo.tivimate.guide.data.PortalAuthException
import app.oslo.tivimate.guide.data.PortalFormatException
import app.oslo.tivimate.guide.data.PortalHttpException
import app.oslo.tivimate.guide.data.favoriteKey
import app.oslo.tivimate.guide.data.monogram
import app.oslo.tivimate.guide.data.portalErrorMessage
import app.oslo.tivimate.guide.data.portalHost
import app.oslo.tivimate.guide.data.splitDisplayName
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontendLogicTest {
    private val portal = "line.example.com:8080/"

    @Test
    fun forbiddenPointsAtTheServerUrl() {
        val text = portalErrorMessage(PortalHttpException(403), portal)
        assertTrue(text, "403" in text)
        assertTrue(text, "server URL" in text)
        assertTrue(text, "line.example.com" in text)
    }

    @Test
    fun otherPortalFailuresAreExplained() {
        assertTrue("401" in portalErrorMessage(PortalHttpException(401), portal))
        assertTrue("404" in portalErrorMessage(PortalHttpException(404), portal))
        assertTrue("502" in portalErrorMessage(PortalHttpException(502), portal))
        assertTrue("username" in portalErrorMessage(PortalAuthException(), portal))
        assertTrue("Xtream" in portalErrorMessage(PortalFormatException("x"), portal))
        assertTrue("Couldn't find" in portalErrorMessage(UnknownHostException("x"), portal))
        assertTrue("port" in portalErrorMessage(ConnectException("x"), portal))
        assertTrue("too long" in portalErrorMessage(SocketTimeoutException("x"), portal))
        assertEquals("boom", portalErrorMessage(IllegalStateException("boom"), portal))
    }

    @Test
    fun messagesNeverCarryCredentials() {
        val text = portalErrorMessage(PortalHttpException(403), "http://user:secret@host.tv:80")
        assertFalse(text, "secret" in text)
        assertEquals("host.tv", portalHost("http://user:secret@host.tv:80"))
        assertEquals("host.tv", portalHost("host.tv/player_api.php"))
    }

    @Test
    fun liveNamesSplitIntoTagAndTitle() {
        assertEquals("US", splitDisplayName("US: HBO HD").tag)
        assertEquals("HBO HD", splitDisplayName("US: HBO HD").title)
        assertEquals("CA EN", splitDisplayName("CA EN: TVA").tag)
        assertEquals("4K", splitDisplayName("4K: SKY SPORT").tag)
        assertEquals("IN-PREM", splitDisplayName("IN-PREM: Sony HD").tag)
        assertNull(splitDisplayName("Local Channel").tag)
        assertNull(splitDisplayName("Alien: Romulus", "movie").tag)
        assertNull(splitDisplayName("Star Wars: Andor").tag)
        assertEquals("Alien: Romulus", splitDisplayName("Alien: Romulus", "movie").title)
    }

    @Test
    fun monogramsAndFavoriteKeys() {
        assertEquals("HH", monogram("HBO HD"))
        assertEquals("B", monogram("BBC"))
        assertEquals("TV", monogram("  "))
        assertEquals("live\tUS: HBO", favoriteKey("live", "US: HBO"))
    }
}
