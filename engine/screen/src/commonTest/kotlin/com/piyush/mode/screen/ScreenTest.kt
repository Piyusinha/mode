package com.piyush.mode.screen

import com.piyush.mode.ops.OpFailed
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

private val SCREEN = ScreenInfo(400.0, 800.0)

class AppNamesTest {
    private val apps = listOf(InstalledApp("Uber", "com.ubercab.UberClient"), InstalledApp("Uber Eats", "com.ubercab.UberEats"))

    @Test
    fun exactThenPrefixThenSubstring() {
        assertEquals("com.ubercab.UberClient", AppNames.resolve("uber", apps).bundleId)
        assertEquals("com.ubercab.UberEats", AppNames.resolve("Uber E", apps).bundleId)
        assertEquals("com.ubercab.UberEats", AppNames.resolve("eats", apps).bundleId)
    }

    @Test
    fun ambiguousAndMissingNamesFail() {
        assertFailsWith<OpFailed> { AppNames.resolve("Ub", listOf(InstalledApp("Ubx", "a"), InstalledApp("Uby", "b"))) }
        assertFailsWith<OpFailed> { AppNames.resolve("Maps", apps) }
        assertFailsWith<OpFailed> { AppNames.resolve("  ", apps) }
        assertTrue(AppNames.looksLikeBundleId("com.apple.Maps"))
        assertTrue(!AppNames.looksLikeBundleId("Apple Maps"))
    }
}

class ScreenJsonTest {
    @Test
    fun boundsAndCaptures() {
        val bounds = ScreenJson.bounds(SCREEN)
        assertEquals(400.0, bounds["w"]!!.jsonPrimitive.double)
        assertEquals(0, bounds["id"]!!.jsonPrimitive.int)

        val jpeg = ScreenJson.captureJpeg(ShrunkImage(byteArrayOf(1), 500, 1000), SCREEN)
        assertEquals(500, jpeg["img_px"]!!.jsonObject["w"]!!.jsonPrimitive.int)
        assertEquals("AQ==", jpeg["jpeg_b64"]!!.jsonPrimitive.content)

        val png = ScreenJson.capturePng(byteArrayOf(1, 2), SCREEN)
        assertEquals("AQI=", png["png_b64"]!!.jsonPrimitive.content)

        val private = ScreenJson.capturePrivate(SCREEN)
        assertTrue(private["private"]!!.jsonPrimitive.boolean)
        assertNull(private["jpeg_b64"])
    }

    @Test
    fun ocrBoxesBecomeCentresInPoints() {
        val box = ScreenJson.textBox(RecognizedText("Where to?", 0.9, left = 0.25, top = 0.5, width = 0.5, height = 0.1), SCREEN).jsonObject
        assertEquals(200.0, box["x"]!!.jsonPrimitive.double)
        assertEquals(440.0, box["y"]!!.jsonPrimitive.double)
        assertEquals(200.0, box["w"]!!.jsonPrimitive.double)
        assertEquals("ocr", box["source"]!!.jsonPrimitive.content)
        assertNull(box["window"])

        val tree = ScreenJson.textBox("Send", 1.0, "tree", 10.4, 20.6, 30.0, 40.0, window = "app")
        assertEquals(10.0, tree["x"]!!.jsonPrimitive.double)
        assertEquals(21.0, tree["y"]!!.jsonPrimitive.double)
        assertEquals("app", tree["window"]!!.jsonPrimitive.content)
    }
}

class ShotCacheTest {
    @Test
    fun reusesOnceWhileFresh() {
        val clock = TestTimeSource()
        val cache = ShotCache(clock)
        cache.put(byteArrayOf(7))
        assertContentEquals(byteArrayOf(7), cache.take())
        assertNull(cache.take())

        cache.put(byteArrayOf(8))
        clock += 2.seconds
        assertNull(cache.take())

        cache.put(byteArrayOf(9))
        cache.invalidate()
        assertNull(cache.take())
    }
}
