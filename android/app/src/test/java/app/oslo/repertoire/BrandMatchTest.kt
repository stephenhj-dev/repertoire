package app.oslo.repertoire

import app.oslo.repertoire.setup.BrandMatch
import app.oslo.repertoire.setup.StepKind
import app.oslo.repertoire.setup.TvGuides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandMatchTest {
    private fun id(manufacturer: String? = null, model: String? = null, name: String? = null) =
        BrandMatch.suggest(manufacturer, model, name)?.id

    @Test
    fun matchesAdbManufacturerAndModel() {
        assertEquals("shield", id("NVIDIA", "SHIELD Android TV"))
        assertEquals("fire", id("Amazon", "AFTKA"))
        assertEquals("google", id("Google", "Chromecast"))
        assertEquals("google", id("Google", "Google TV Streamer"))
        assertEquals("onn", id("onn", "onn. 4K Streaming Box"))
        assertEquals("xiaomi", id("Xiaomi", "MIBOX4"))
        assertEquals("sony", id("Sony", "BRAVIA 4K VH2"))
        assertEquals("tcl-hisense", id("TCL", "Smart TV Pro"))
        assertEquals("tcl-hisense", id("Hisense", "HAT4KDTV"))
        assertEquals("philips", id("TPVision", "QM16XE"))
        assertEquals("philips", id("Philips", "TV"))
    }

    @Test
    fun matchesMdnsNamesWhenAdbIsUnknown() {
        assertEquals("shield", id(name = "Living room SHIELD"))
        assertEquals("google", id(name = "Bedroom Chromecast"))
        assertEquals("fire", id(name = "Oslo's Fire TV Stick 4K"))
        assertEquals("sony", id(name = "BRAVIA XR"))
        assertEquals("onn", id(name = "onn. Streaming Device 4K pro"))
    }

    @Test
    fun fireTvEditionSetsGetTheFireTvGuide() {
        // A TCL or Toshiba Fire TV set reports Amazon models.
        assertEquals("fire", id("TCL", "AFTDCT31"))
    }

    @Test
    fun unknownFallsBackToGeneric() {
        assertNull(id("Rockchip", "rk3328"))
        assertNull(id())
        assertEquals("generic", BrandMatch.guideFor("Rockchip", "rk3328").id)
        // Words inside other words don't match ("onn" in "connect", "tcl" in "article").
        assertNull(id(name = "Connected box"))
    }

    @Test
    fun everyGuideEndsWithAnAllowAndTestStep() {
        TvGuides.all.forEach { guide ->
            assertEquals(guide.id, StepKind.Test, guide.steps.last().kind)
            assertTrue(guide.id, guide.steps.any { it.kind == StepKind.Allow })
            assertTrue(guide.id, guide.steps.first().kind == StepKind.DeveloperOptions)
        }
        assertEquals(TvGuides.all.size, TvGuides.all.map { it.id }.toSet().size)
    }
}
