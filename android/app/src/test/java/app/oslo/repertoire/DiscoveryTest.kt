package app.oslo.repertoire

import app.oslo.repertoire.remote.ResolvedService
import app.oslo.repertoire.remote.TvServiceTypes
import app.oslo.repertoire.remote.decodeServiceName
import app.oslo.repertoire.remote.isFireTv
import app.oslo.repertoire.remote.mergeDevices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {
    private fun service(type: String, name: String, host: String, port: Int, vararg txt: Pair<String, String>) =
        ResolvedService(type, name, host, port, txt.toMap())

    @Test
    fun shieldKeepsItsNameAndGetsTheCastModel() {
        val devices = mergeDevices(
            listOf(
                service(TvServiceTypes.REMOTE, "SHIELD", "192.168.1.50", 6466),
                service(TvServiceTypes.CAST, "SHIELD-Android-TV-abc", "192.168.1.50", 8009, "fn" to "SHIELD", "md" to "SHIELD Android TV"),
            ),
        )
        assertEquals(1, devices.size)
        val shield = devices[0]
        assertEquals("SHIELD", shield.name)
        assertEquals("SHIELD Android TV", shield.model)
        assertEquals(6466, shield.remotePort)
        assertTrue(shield.advertisesRemote)
        assertFalse(shield.fireTv)
    }

    @Test
    fun findsEveryAndroidTvNotOnlyShields() {
        val devices = mergeDevices(
            listOf(
                service(TvServiceTypes.REMOTE, "Living Room TV", "192.168.1.20", 6466),
                service(TvServiceTypes.CAST, "Chromecast-x", "192.168.1.20", 8009, "fn" to "Living Room TV", "md" to "Chromecast"),
                service(TvServiceTypes.REMOTE, "BRAVIA 4K GB", "192.168.1.30", 6466),
                service(TvServiceTypes.REMOTE, "Google\\032TV\\032Streamer", "192.168.1.40", 6466),
                service(TvServiceTypes.ADB_TLS, "adb-4C1F0001-AbCdEf", "192.168.1.40", 37123),
            ),
        )
        assertEquals(listOf("BRAVIA 4K GB", "Google TV Streamer", "Living Room TV"), devices.map { it.name })
        val streamer = devices.first { it.host == "192.168.1.40" }
        assertEquals(37123, streamer.adbTlsPort)
        assertTrue(streamer.advertisesAdb)
        assertEquals("Chromecast", devices.first { it.host == "192.168.1.20" }.model)
    }

    @Test
    fun castOnlyDevicesAndThePhoneAreFilteredOut() {
        val devices = mergeDevices(
            listOf(
                service(TvServiceTypes.CAST, "Kitchen-speaker", "192.168.1.51", 8009, "fn" to "Kitchen speaker", "md" to "Google Nest Mini"),
                service(TvServiceTypes.ADB_TLS, "adb-R5GL66J1H7B-xyz", "192.168.1.10", 41000),
                service(TvServiceTypes.REMOTE, "", "", 6466),
            ),
            ownAddresses = setOf("192.168.1.10"),
        )
        assertTrue(devices.isEmpty())
    }

    @Test
    fun fireTvComesFromWhisperplay() {
        val devices = mergeDevices(
            listOf(service("_amzn-wplay._tcp", "abc123", "192.168.1.60", 4070, "n" to "Bedroom Fire TV")),
        )
        val fire = devices.single()
        assertEquals("Bedroom Fire TV", fire.name)
        assertEquals("Fire TV", fire.model)
        assertTrue(fire.fireTv)
        assertNull(fire.remotePort)
    }

    @Test
    fun hostWithoutNameFallsBackToAddress() {
        val devices = mergeDevices(listOf(service(TvServiceTypes.ADB, "", "192.168.1.70", 5555)))
        assertEquals("192.168.1.70", devices.single().name)
        assertTrue(devices.single().advertisesAdb)
    }

    @Test
    fun serviceNamesAreUnescaped() {
        assertEquals("Living Room TV", decodeServiceName("Living\\032Room\\032TV"))
        assertEquals("A.B", decodeServiceName("A\\.B"))
        assertEquals("SHIELD", decodeServiceName("SHIELD"))
    }

    @Test
    fun fireTvIsRecognisedFromAdbProperties() {
        assertTrue(isFireTv("Amazon", "AFTKA"))
        assertTrue(isFireTv("", "AFTMM"))
        assertFalse(isFireTv("NVIDIA", "SHIELD Android TV"))
        assertFalse(isFireTv("Google", "Chromecast"))
    }
}
