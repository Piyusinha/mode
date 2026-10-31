package com.piyush.mode.coredevice

import com.piyush.mode.ops.Op
import com.piyush.mode.ops.OpFailed
import com.piyush.mode.ops.Unsupported
import com.piyush.mode.ops.wireName
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/** Records what the backend asked of the phone. */
private class FakeSession(
    private val apps: List<InstalledApp> = emptyList(),
) : DeviceSession {
    val calls = mutableListOf<String>()
    var listCalls = 0
    var shots = 0

    override fun touch(x1: Int, y1: Int, x2: Int, y2: Int, holdMs: Long, travelMs: Long) {
        calls += "touch $x1,$y1 -> $x2,$y2 hold=$holdMs travel=$travelMs"
    }

    override fun button(button: HardwareButton) { calls += "button $button" }
    override fun key(name: String) { calls += "key $name" }
    override fun typeAscii(text: String) { calls += "type $text" }
    override fun pasteText(text: String) { calls += "paste $text" }
    override fun screenshotPng(): ByteArray = byteArrayOf(1, 2, 3).also { shots++ }
    override fun launch(bundleId: String) { calls += "launch $bundleId" }
    override fun listApps(): List<InstalledApp> = apps.also { listCalls++ }
    override fun close() { calls += "close" }
}

private val SCREEN = ScreenInfo(widthPt = 402.0, heightPt = 874.0)

private fun backend(
    session: DeviceSession = FakeSession(),
    text: List<RecognizedText> = emptyList(),
    clock: TimeSource = TimeSource.Monotonic,
) = CoreDeviceBackend(
    session, SCREEN,
    reader = { text },
    images = { _, maxSide -> ShrunkImage(byteArrayOf(9), maxSide / 2, maxSide) },
    clock = clock,
)

class CoreDeviceBackendTest {

    @Test
    fun tapMapsPointsOntoTheTouchscreen() = runTest {
        val phone = FakeSession()
        backend(phone).send(Op.Tap(x = 201.0, y = 437.0))
        assertEquals(listOf("touch 32768,32768 -> 32768,32768 hold=50 travel=0"), phone.calls)
    }

    @Test
    fun cornersClampToTheScreen() = runTest {
        val phone = FakeSession()
        backend(phone).send(Op.Tap(x = -5.0, y = 2000.0))
        assertEquals("touch 0,65535 -> 0,65535 hold=50 travel=0", phone.calls.single())
    }

    @Test
    fun longPressHoldsForItsDuration() = runTest {
        val phone = FakeSession()
        backend(phone).send(Op.LongPress(x = 0.0, y = 0.0, duration = 0.8))
        assertTrue(phone.calls.single().endsWith("hold=800 travel=0"))
    }

    @Test
    fun scrollingDownMovesTheFingerUp() = runTest {
        val phone = FakeSession()
        backend(phone).send(Op.Scroll(x = 201.0, y = 600.0, dy = -400.0))
        // 400 pt at 800 pt/s is half a second.
        val (from, to) = SCREEN.toTouch(201.0, 600.0) to SCREEN.toTouch(201.0, 200.0)
        assertEquals("touch ${from.first},${from.second} -> ${to.first},${to.second} hold=50 travel=500", phone.calls.single())
    }

    @Test
    fun asciiIsTypedAndEverythingElsePasted() = runTest {
        val phone = FakeSession()
        val b = backend(phone)
        b.send(Op.TypeText("India Gate"))
        b.send(Op.TypeText("इंडिया गेट"))
        b.send(Op.TypeText("café"))
        assertEquals(listOf("type India Gate", "paste इंडिया गेट", "paste café"), phone.calls)
    }

    @Test
    fun namedKeysOnly() = runTest {
        val phone = FakeSession()
        val b = backend(phone)
        b.send(Op.PressKey("Return"))
        assertEquals(listOf("key return"), phone.calls)
        assertFailsWith<Unsupported> { b.send(Op.PressKey("cmd+a")) }
    }

    @Test
    fun launchResolvesNamesFromTheInstalledApps() = runTest {
        val phone = FakeSession(
            apps = listOf(InstalledApp("Uber", "com.ubercab.UberClient"), InstalledApp("Uber Eats", "com.ubercab.UberEats")),
        )
        val b = backend(phone)
        assertEquals(JsonPrimitive("com.ubercab.UberClient"), b.send(Op.LaunchApp("uber")))
        assertEquals(JsonPrimitive("com.ubercab.UberEats"), b.send(Op.LaunchApp("Uber Eats")))
        assertEquals(JsonPrimitive("com.apple.Preferences"), b.send(Op.LaunchApp("com.apple.Preferences")))
        assertEquals(listOf("launch com.ubercab.UberClient", "launch com.ubercab.UberEats", "launch com.apple.Preferences"), phone.calls)
        assertEquals(1, phone.listCalls, "the app list is cached")
    }

    @Test
    fun launchFailsClearlyForUnknownOrAmbiguousNames() = runTest {
        val phone = FakeSession(apps = listOf(InstalledApp("Uber", "a.b"), InstalledApp("Uber Eats", "a.c")))
        val b = backend(phone)
        assertFailsWith<OpFailed> { b.send(Op.LaunchApp("Ola")) }
        assertFailsWith<OpFailed> { b.send(Op.LaunchApp("Ub")) }
    }

    @Test
    fun screenTextIsInPointsWithTapReadyCentres() = runTest {
        val b = backend(
            text = listOf(
                RecognizedText("Where to?", 0.9, left = 0.25, top = 0.5, width = 0.5, height = 0.1),
                RecognizedText("blur", 0.1, 0.0, 0.0, 0.1, 0.1),
            ),
        )
        val boxes = b.send(Op.ScreenText()) as JsonArray
        val box = boxes.single().jsonObject
        assertEquals("Where to?", box.getValue("text").jsonPrimitive.content)
        assertEquals("ocr", box.getValue("source").jsonPrimitive.content)
        assertEquals(201.0, box.getValue("x").jsonPrimitive.double)
        assertEquals(481.0, box.getValue("y").jsonPrimitive.double) // (0.5 + 0.05) * 874 = 480.7
        assertEquals(201.0, box.getValue("w").jsonPrimitive.double)
    }

    @Test
    fun captureShrinksOnlyWhenAsked() = runTest {
        val b = backend()
        val full = b.send(Op.ScreenCapture()).jsonObject
        assertEquals("AQID", full.getValue("png_b64").jsonPrimitive.content)
        val small = b.send(Op.ScreenCapture(maxSize = 1000)).jsonObject
        assertEquals(1000, small.getValue("img_px").jsonObject.getValue("h").jsonPrimitive.int)
        assertTrue("jpeg_b64" in small)
    }

    @Test
    fun whatCoreDeviceCannotDoIsUnsupported() = runTest {
        val phone = FakeSession()
        val b = backend(phone)
        for (op in listOf(Op.Tree, Op.CurrentApp, Op.OpenUrl("https://example.com"), Op.LaunchApp("Uber", fresh = true))) {
            assertFailsWith<Unsupported>(op.wireName) { b.send(op) }
        }
        assertTrue(phone.calls.isEmpty())
    }

    @Test
    fun opsListsExactlyWhatSendAnswers() = runTest {
        val everyOp = listOf(
            Op.Tree, Op.ScreenText(), Op.ScreenCapture(), Op.ScreenBounds,
            Op.Tap(0.0, 0.0), Op.LongPress(0.0, 0.0), Op.Drag(0.0, 0.0, 1.0, 1.0),
            Op.Scroll(0.0, 0.0, dy = -1.0), Op.TypeText("hi"), Op.PressKey("return"),
            Op.Home, Op.LaunchApp("a.b"), Op.CurrentApp, Op.OpenUrl("https://example.com"),
            Op.SessionState,
        )
        val b = backend()
        val answered = everyOp.filter { runCatching { b.send(it) }.exceptionOrNull() !is Unsupported }
        assertEquals(b.ops(), answered.map { it.wireName }.toSet())
    }

    @Test
    fun oneLookTakesOneScreenshot() = runTest {
        val phone = FakeSession()
        val b = backend(phone)
        b.send(Op.ScreenCapture(maxSize = 1000))
        b.send(Op.ScreenText())
        assertEquals(1, phone.shots)
    }

    @Test
    fun anActionOrTimeMeansANewScreenshot() = runTest {
        val phone = FakeSession()
        val clock = TestTimeSource()
        val b = backend(phone, clock = clock)
        b.send(Op.ScreenText())
        b.send(Op.Tap(1.0, 1.0))
        b.send(Op.ScreenText())
        assertEquals(2, phone.shots, "a tap changes the screen")
        clock += 2.seconds
        b.send(Op.ScreenText())
        assertEquals(3, phone.shots, "an old screenshot is not reused")
    }
}
