package app.oslo.repertoire

import app.oslo.repertoire.data.assignSections
import app.oslo.repertoire.data.classifyName
import app.oslo.repertoire.data.countryLabel
import app.oslo.repertoire.data.likeNeedle
import app.oslo.repertoire.data.rank
import app.oslo.repertoire.remote.pairingSecret
import java.math.BigInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideLogicTest {
    @Test
    fun countryPrefixes() {
        val us = classifyName("US: HBO HD")
        assertEquals("US", us.country)
        assertEquals("United States", countryLabel(us.country!!))
        assertEquals(false, us.banner)

        val uk = classifyName("UK: BBC ONE")
        assertEquals("UK", uk.country)
        assertEquals("United Kingdom", countryLabel("UK"))

        val canada = classifyName("CA EN: TVA")
        assertEquals("CA", canada.country)

        val india = classifyName("IN-PREM: Sony HD")
        assertEquals("IN", india.country)

        val arabic = classifyName("AR: STARZPLAY HD")
        assertEquals("AR", arabic.country)
        assertEquals("Arabic", countryLabel("AR"))

        val latin = classifyName("LA: TELE ARUBA")
        assertEquals("Latin America", countryLabel(latin.country!!))
    }

    @Test
    fun packagesAreNotCountries() {
        val fourK = classifyName("4K: SKY SPORT")
        assertNull(fourK.country)
        assertEquals("4K", fourK.groupCode)

        val day = classifyName("24/7: CARTOON")
        assertNull(day.country)
        assertEquals("24/7", day.groupCode)

        val vip = classifyName("VIP: TNT SPORT 1")
        assertEquals("VIP", vip.groupCode)

        assertNull(classifyName("Local Channel").country)
        assertNull(classifyName("Local Channel").groupCode)
    }

    @Test
    fun bannersTakeTheFollowingSection() {
        val rows = assignSections(
            listOf(
                classifyName("##### NEWS #####"),
                classifyName("US: HBO HD"),
                classifyName("##### SPORT #####"),
                classifyName("UK: BBC ONE"),
            ),
        )
        assertEquals(true, rows[0].banner)
        assertEquals("US", rows[0].country)
        assertEquals("UK", rows[2].country)
        assertEquals("NEWS", rows[1].section)
        assertEquals("SPORT", rows[3].section)
        assertEquals("US", rows[1].country)
    }

    @Test
    fun searchRankMatchesTheTile() {
        assertEquals(0, rank("US: HBO HD", "us: hbo hd"))
        assertEquals(1, rank("US: HBO HD", "US: HB"))
        assertEquals(2, rank("US: HBO HD", "hbo"))
        assertNull(rank("US: HBO HD", "espn"))
        assertNull(rank("US: HBO HD", " "))
        assertEquals("100\\%", likeNeedle("100%"))
    }

    @Test
    fun pairingSecretMatchesTheShieldProtocol() {
        val digest = pairingSecret(
            BigInteger("ABCD", 16),
            BigInteger("10001", 16),
            BigInteger("1234", 16),
            BigInteger("10001", 16),
            "48beef",
        )
        assertArrayEquals(
            hex("489e0ab169beffbee524d3adb0cc6bea247051d6d7fc81efbfb97ea3c9a51238"),
            digest,
        )
    }

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
