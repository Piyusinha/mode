package com.piyush.mode.adb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelperProtocolTest {
    @Test
    fun startsTheHelperFromModesApk() {
        assertEquals(
            "CLASSPATH='/data/app/~~x/app.heymode.mode-y/base.apk' app_process / com.piyush.mode.helper.Helper",
            HelperCommand.of("/data/app/~~x/app.heymode.mode-y/base.apk"),
        )
    }

    @Test
    fun anApkPathCantBreakOutOfTheQuotes() {
        assertFailsWith<IllegalArgumentException> { HelperCommand.of("/data/app/a'; reboot; 'b") }
        assertFailsWith<IllegalArgumentException> { HelperCommand.of("") }
    }

    @Test
    fun readsTheHelpersAnswers() {
        assertEquals(7, HelperAnswer.displayId("display 7"))
        assertNull(HelperAnswer.displayId("error no display"))
        assertTrue(HelperAnswer.ok("started"))
        assertFalse(HelperAnswer.ok("error SecurityException: denied"))
        assertEquals("start 7 com.android.settings/.Settings", HelperCommand.start(7, "com.android.settings/.Settings"))
    }
}
