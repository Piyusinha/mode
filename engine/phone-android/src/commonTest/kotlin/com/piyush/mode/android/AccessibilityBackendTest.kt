package com.piyush.mode.android

import com.piyush.mode.ops.Op
import com.piyush.mode.ops.OpFailed
import com.piyush.mode.ops.Unsupported
import com.piyush.mode.screen.InstalledApp
import com.piyush.mode.screen.RecognizedText
import com.piyush.mode.screen.ShrunkImage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A 1080×2400 px phone at 3× (360×800 dp) with a hand-built tree. */
private class FakeHands(
    var windowsNow: List<WindowSnapshot> = emptyList(),
    val apps: List<InstalledApp> = emptyList(),
    var shot: Shot = Shot.Image(byteArrayOf(1, 2, 3), 1080, 2400),
) : AccessibilityHands {
    override val density = 3f
    override val screenPx = 1080 to 2400
    override val topInsetPx = 120
    val calls = mutableListOf<String>()
    val shotQueue = mutableListOf<Shot>()
    var setTextWorks = true

    override fun windows(displayId: Int) = windowsNow
    override suspend fun screenshot(displayId: Int): Shot {
        calls += "shot"
        return if (shotQueue.isNotEmpty()) shotQueue.removeAt(0) else shot
    }
    override suspend fun gesture(stroke: Stroke): Boolean {
        calls += "gesture ${stroke.points.first()} -> ${stroke.points.last()} ${stroke.durationMs}ms"
        return true
    }
    override fun global(action: GlobalAction): Boolean { calls += "global $action"; return true }
    override fun focusedEditable(displayId: Int): NodeSnapshot? = windowsNow.firstNotNullOfOrNull { w -> w.root?.let { find(it) { n -> n.editable && n.focused } } }
    override fun setText(node: NodeSnapshot, text: String): Boolean { calls += "setText ${node.id} '$text'"; return setTextWorks }
    override fun paste(node: NodeSnapshot, text: String): Boolean { calls += "paste ${node.id} '$text'"; return true }
    override fun imeEnter(node: NodeSnapshot): Boolean { calls += "enter ${node.id}"; return true }
    override fun launch(packageName: String, fresh: Boolean, displayId: Int): Boolean { calls += "launch $packageName fresh=$fresh"; return true }
    override fun installedApps() = apps.also { calls += "apps" }
    override fun currentPackage(displayId: Int) = windowsNow.firstOrNull { it.kind == WindowKind.App }?.packageName
    override fun openUrl(url: String): Boolean { calls += "url $url"; return url.startsWith("https://") }

    private fun find(n: NodeSnapshot, p: (NodeSnapshot) -> Boolean): NodeSnapshot? =
        if (p(n)) n else n.children.firstNotNullOfOrNull { find(it, p) }
}

private fun node(id: Int, text: String? = null, l: Int, t: Int, r: Int, b: Int, vararg children: NodeSnapshot, desc: String? = null, hint: String? = null,
                 editable: Boolean = false, focused: Boolean = false, visible: Boolean = true, password: Boolean = false, showingHint: Boolean = false) =
    NodeSnapshot(id, text, desc, hint, "android.widget.TextView", PxRect(l, t, r, b), visible, editable = editable, focused = focused, password = password, showingHint = showingHint, children = children.toList())

private fun app(root: NodeSnapshot, pkg: String = "com.example.app") = WindowSnapshot(WindowKind.App, pkg, PxRect(0, 0, 1080, 2400), root)
private fun ime(root: NodeSnapshot) = WindowSnapshot(WindowKind.Ime, "com.google.android.inputmethod.latin", PxRect(0, 1500, 1080, 2400), root)

private fun backend(hands: FakeHands, ocr: List<RecognizedText>? = null) = AccessibilityBackend(
    hands, images = { _, maxSide -> ShrunkImage(byteArrayOf(9), maxSide / 2, maxSide) }, reader = ocr?.let { texts -> com.piyush.mode.screen.TextReader { texts } },
)

class AccessibilityBackendTest {

    @Test
    fun boundsAreDpWithTheTopInset() = runTest {
        val b = backend(FakeHands()).send(Op.ScreenBounds).jsonObject
        assertEquals(360.0, b["w"]!!.jsonPrimitive.double)
        assertEquals(800.0, b["h"]!!.jsonPrimitive.double)
        assertEquals(40.0, b["top_inset"]!!.jsonPrimitive.double)
    }

    @Test
    fun treeTextComesInReadingOrderInDpWithItsWindow() = runTest {
        val hands = FakeHands(listOf(
            app(node(1, null, 0, 0, 1080, 2400,
                node(2, "Send", 600, 900, 900, 1000),
                node(3, "Hello", 0, 300, 300, 360),
                node(4, null, 0, 600, 1080, 700, desc = "Search"),
                node(5, "hidden", 0, 0, 100, 100, visible = false),
            )),
            ime(node(9, null, 0, 1500, 1080, 2400, node(10, "q", 0, 1600, 100, 1700))),
        ))
        val boxes = (backend(hands).send(Op.ScreenText()) as JsonArray).map { it.jsonObject }
        assertEquals(listOf("Hello", "Search", "Send", "q"), boxes.map { it["text"]!!.jsonPrimitive.content })
        val hello = boxes[0]
        assertEquals(50.0, hello["x"]!!.jsonPrimitive.double)
        assertEquals(110.0, hello["y"]!!.jsonPrimitive.double)
        assertEquals("tree", hello["source"]!!.jsonPrimitive.content)
        assertEquals("app", hello["window"]!!.jsonPrimitive.content)
        assertEquals("ime", boxes[3]["window"]!!.jsonPrimitive.content)
    }

    @Test
    fun aContainerRepeatingItsChildIsOneBoxAndPasswordsAreLeftOut() = runTest {
        val hands = FakeHands(listOf(app(node(1, "Pay", 0, 0, 1080, 200,
            node(2, "Pay", 0, 0, 1080, 200),
            node(3, "secret", 0, 300, 500, 400, password = true),
        ))))
        val boxes = (backend(hands).send(Op.ScreenText()) as JsonArray).map { it.jsonObject["text"]!!.jsonPrimitive.content }
        assertEquals(listOf("Pay"), boxes)
    }

    @Test
    fun theAppWindowCapsAtEightyButKeysAreExtra() {
        val many = (1..100).map { node(it, "row $it", 0, it * 20, 500, it * 20 + 10) }
        val keys = (1..30).map { node(200 + it, "k$it", it * 30, 1600, it * 30 + 20, 1650) }
        val boxes = TreeText.boxes(listOf(app(node(0, null, 0, 0, 1080, 2400, *many.toTypedArray())), ime(node(199, null, 0, 1500, 1080, 2400, *keys.toTypedArray()))), 3f)
        assertEquals(80, boxes.count { it.window == "app" })
        assertEquals(30, boxes.count { it.window == "ime" })
    }

    @Test
    fun captureShrinksAndTextReusesTheShot() = runTest {
        val hands = FakeHands(listOf(app(node(1, "Hi", 0, 0, 100, 100))))
        val b = backend(hands)
        val c = b.send(Op.ScreenCapture(maxSize = 1000)).jsonObject
        assertEquals("CQ==", c["jpeg_b64"]!!.jsonPrimitive.content)
        assertEquals(500, c["img_px"]!!.jsonObject["w"]!!.jsonPrimitive.int)
        b.send(Op.ScreenText())
        assertEquals(1, hands.calls.count { it == "shot" }, "the tree needs no second shot")
    }

    @Test
    fun aSecureScreenIsPrivate() = runTest {
        val hands = FakeHands(shot = Shot.Secure)
        val c = backend(hands).send(Op.ScreenCapture(maxSize = 1000)).jsonObject
        assertTrue(c["private"]!!.jsonPrimitive.boolean)
        assertNull(c["jpeg_b64"])
        assertEquals(360.0, c["bounds"]!!.jsonObject["w"]!!.jsonPrimitive.double)
    }

    @Test
    fun aThrottledShotIsRetriedOnce() = runTest {
        val hands = FakeHands()
        hands.shotQueue += Shot.Throttled(10)
        val c = backend(hands).send(Op.ScreenCapture(maxSize = 1000)).jsonObject
        assertTrue("jpeg_b64" in c)
        assertEquals(2, hands.calls.count { it == "shot" })
        hands.shotQueue += listOf(Shot.Throttled(10), Shot.Throttled(10))
        assertFailsWith<OpFailed> { backend(hands).send(Op.ScreenCapture(maxSize = 1000)) }
    }

    @Test
    fun ocrFillsInWhenTheAppWindowHasNoText() = runTest {
        val hands = FakeHands(listOf(app(node(1, null, 0, 0, 1080, 2400))))
        val boxes = backend(hands, ocr = listOf(RecognizedText("PLAY", 0.9, 0.25, 0.5, 0.5, 0.1))).send(Op.ScreenText()) as JsonArray
        val play = boxes.single().jsonObject
        assertEquals("ocr", play["source"]!!.jsonPrimitive.content)
        assertEquals(180.0, play["x"]!!.jsonPrimitive.double)
        assertEquals(1, hands.calls.count { it == "shot" })
    }

    @Test
    fun gesturesLandInPixels() = runTest {
        val hands = FakeHands()
        val b = backend(hands)
        b.send(Op.Tap(100.0, 200.0))
        b.send(Op.Scroll(180.0, 400.0, dy = -400.0))
        assertEquals("gesture (300.0, 600.0) -> (300.0, 600.0) 50ms", hands.calls[0])
        assertEquals("gesture (540.0, 1200.0) -> (540.0, 0.0) 500ms", hands.calls[1])
    }

    @Test
    fun typingGoesAfterWhatTheFieldHoldsNotItsHint() = runTest {
        val hands = FakeHands(listOf(app(node(1, null, 0, 0, 1080, 2400, node(2, "Search", 0, 0, 500, 100, hint = "Search", editable = true, focused = true)))))
        backend(hands).send(Op.TypeText("oats"))
        assertEquals("setText 2 'oats'", hands.calls.single())
        hands.windowsNow = listOf(app(node(1, null, 0, 0, 1080, 2400, node(2, "Hel", 0, 0, 500, 100, editable = true, focused = true))))
        backend(hands).send(Op.TypeText("lo"))
        assertEquals("setText 2 'Hello'", hands.calls.last())
    }

    @Test
    fun anEmptyFieldShowingItsHintTakesTheTextAlone() = runTest {
        // WhatsApp's compose field reports "Message" as its text while empty, and no hint: the phone says it is
        // showing its hint. Typing must not give "Messagetesting"; delete has nothing to remove.
        val hands = FakeHands(listOf(app(node(1, null, 0, 0, 1080, 2400, node(2, "Message", 0, 0, 500, 100, editable = true, focused = true, showingHint = true)))))
        val b = backend(hands)
        b.send(Op.TypeText("testing"))
        assertEquals("setText 2 'testing'", hands.calls.single())
        b.send(Op.PressKey("delete"))
        assertEquals(1, hands.calls.size, "nothing to delete: ${'$'}{hands.calls}")
    }

    @Test
    fun aTextFieldIsMarkedAmongTheScreenTexts() = runTest {
        val hands = FakeHands(listOf(app(node(1, null, 0, 0, 1080, 2400,
            node(2, "testing Android mod", 0, 300, 600, 360),
            node(3, "testing", 0, 600, 500, 700, editable = true, focused = true),
        ))))
        val boxes = (backend(hands).send(Op.ScreenText()) as JsonArray).map { it.jsonObject }
        assertEquals(listOf("testing Android mod", "testing"), boxes.map { it["text"]!!.jsonPrimitive.content })
        assertEquals(null, boxes[0]["field"])
        assertEquals(true, boxes[1]["field"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun typingWithoutAFocusedFieldFailsAndPasteIsTheFallback() = runTest {
        val hands = FakeHands(listOf(app(node(1, "Nothing", 0, 0, 100, 100))))
        assertFailsWith<OpFailed> { backend(hands).send(Op.TypeText("x")) }
        hands.windowsNow = listOf(app(node(2, null, 0, 0, 500, 100, editable = true, focused = true)))
        hands.setTextWorks = false
        backend(hands).send(Op.TypeText("नमस्ते"))
        assertEquals("paste 2 'नमस्ते'", hands.calls.last())
    }

    @Test
    fun keysAreReturnAndDeleteOnly() = runTest {
        val hands = FakeHands(listOf(app(node(2, "abc", 0, 0, 500, 100, editable = true, focused = true))))
        val b = backend(hands)
        b.send(Op.PressKey("return"))
        b.send(Op.PressKey("delete"))
        assertEquals(listOf("enter 2", "setText 2 'ab'"), hands.calls)
        assertFailsWith<Unsupported> { b.send(Op.PressKey("tab")) }
    }

    @Test
    fun navigationAndPanelsAreGlobalActions() = runTest {
        val hands = FakeHands()
        val b = backend(hands)
        listOf(Op.Home, Op.Back, Op.Recents, Op.OpenNotifications, Op.OpenQuickSettings).forEach { b.send(it) }
        assertEquals(listOf("global Home", "global Back", "global Recents", "global Notifications", "global QuickSettings"), hands.calls)
    }

    @Test
    fun modesOwnOverlayIsNeverReadAsPartOfTheScreen() = runTest {
        val overlay = WindowSnapshot(WindowKind.Overlay, "app.heymode.mode", PxRect(0, 1600, 1080, 2400), node(9, "Stop", 0, 1700, 300, 1800))
        val hands = FakeHands(listOf(app(node(1, "Wi-Fi", 0, 200, 600, 300)), overlay))
        val texts = (backend(hands).send(Op.ScreenText()) as kotlinx.serialization.json.JsonArray)
            .map { it.jsonObject["text"]!!.jsonPrimitive.content }
        assertEquals(listOf("Wi-Fi"), texts, "the overlay's Stop isn't something on the screen to tap")
    }

    @Test
    fun onAHiddenDisplayBackAndHomeStayThereAndPanelsAreRefused() = runTest {
        val hands = FakeHands()
        val pressed = mutableListOf<GlobalAction>()
        val b = AccessibilityBackend(
            hands, images = { _, maxSide -> ShrunkImage(byteArrayOf(9), maxSide / 2, maxSide) }, displayId = 7,
            displayKeys = { pressed += it; true },
        )
        b.send(Op.Back)
        b.send(Op.Home)
        assertEquals(listOf(GlobalAction.Back, GlobalAction.Home), pressed)
        assertFailsWith<OpFailed> { b.send(Op.OpenQuickSettings) }
        assertFailsWith<OpFailed> { b.send(Op.OpenNotifications) }
        assertFailsWith<OpFailed> { b.send(Op.Recents) }
        assertTrue(hands.calls.none { it.startsWith("global") }, "the user's screen is never touched")
    }

    @Test
    fun appsLaunchByNameOrPackageAndTheCurrentOneIsKnown() = runTest {
        val hands = FakeHands(listOf(app(node(1, "x", 0, 0, 10, 10), pkg = "com.whatsapp")), apps = listOf(InstalledApp("WhatsApp", "com.whatsapp"), InstalledApp("Uber", "com.ubercab")))
        val b = backend(hands)
        assertEquals(JsonPrimitive("com.whatsapp"), b.send(Op.LaunchApp("whatsapp")))
        assertEquals(JsonPrimitive("com.ubercab"), b.send(Op.LaunchApp("com.ubercab", fresh = true)))
        assertEquals(listOf("apps", "launch com.whatsapp fresh=false", "launch com.ubercab fresh=true"), hands.calls)
        assertEquals(JsonPrimitive("com.whatsapp"), b.send(Op.CurrentApp))
        assertFailsWith<OpFailed> { b.send(Op.LaunchApp("Maps")) }
    }

    @Test
    fun urlsOpenOrFailHonestly() = runTest {
        val hands = FakeHands()
        val b = backend(hands)
        assertEquals(JsonNull, b.send(Op.OpenUrl("https://example.com")))
        assertFailsWith<OpFailed> { b.send(Op.OpenUrl("zomato://x")) }
        assertFailsWith<Unsupported> { b.send(Op.Tree) }
    }
}
