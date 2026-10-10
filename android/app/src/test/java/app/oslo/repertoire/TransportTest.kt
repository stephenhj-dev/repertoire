package app.oslo.repertoire

import app.oslo.repertoire.remote.NavTransport
import app.oslo.repertoire.remote.Support
import app.oslo.repertoire.remote.adbKeyCommand
import app.oslo.repertoire.remote.navigationTransport
import app.oslo.repertoire.remote.remoteSupport
import org.junit.Assert.assertEquals
import org.junit.Test

class TransportTest {
    @Test
    fun pairedShieldKeepsRemoteV2EvenBeforeAnyCheck() {
        assertEquals(NavTransport.RemoteV2, navigationTransport(Support.Unknown, paired = true, adb = Support.Unknown))
        assertEquals(NavTransport.RemoteV2, navigationTransport(Support.Yes, paired = true, adb = Support.Yes))
        assertEquals(NavTransport.RemoteV2, navigationTransport(Support.Yes, paired = true, adb = Support.No))
    }

    @Test
    fun fireTvFallsBackToAdbKeys() {
        assertEquals(NavTransport.Adb, navigationTransport(Support.No, paired = false, adb = Support.Yes))
        assertEquals(NavTransport.Adb, navigationTransport(Support.No, paired = true, adb = Support.Yes))
        assertEquals(NavTransport.Adb, navigationTransport(Support.No, paired = false, adb = Support.Unknown))
    }

    @Test
    fun unpairedGoogleTvUsesAdbUntilPaired() {
        assertEquals(NavTransport.Adb, navigationTransport(Support.Yes, paired = false, adb = Support.Yes))
        assertEquals(NavTransport.RemoteV2, navigationTransport(Support.Yes, paired = false, adb = Support.No))
        assertEquals(NavTransport.RemoteV2, navigationTransport(Support.Unknown, paired = false, adb = Support.No))
    }

    @Test
    fun nothingAvailable() {
        assertEquals(NavTransport.None, navigationTransport(Support.No, paired = false, adb = Support.No))
    }

    @Test
    fun advertisedRemoteBeatsAnInconclusiveProbe() {
        assertEquals(Support.Yes, remoteSupport(advertised = true, probed = Support.Unknown))
        assertEquals(Support.No, remoteSupport(advertised = false, probed = Support.No))
        assertEquals(Support.Unknown, remoteSupport(advertised = false, probed = Support.Unknown))
    }

    @Test
    fun supportSurvivesStorage() {
        Support.entries.forEach { assertEquals(it, Support.parse(it.name)) }
        assertEquals(Support.Unknown, Support.parse(null))
        assertEquals(Support.Unknown, Support.parse("garbage"))
    }

    @Test
    fun adbKeyCommands() {
        assertEquals("input keyevent 22", adbKeyCommand(22))
        assertEquals("input keyevent --longpress 23", adbKeyCommand(23, longPress = true))
    }
}
