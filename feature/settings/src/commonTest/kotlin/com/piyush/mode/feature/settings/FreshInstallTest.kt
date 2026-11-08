package com.piyush.mode.feature.settings

import com.piyush.mode.core.storage.FreshInstall
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FreshInstallTest {
    @Test fun aFreshInstallDropsTheLastInstallsSignIns() {
        val secrets = InMemorySecretStore("chatgpt.refresh" to "old")
        val prefs = InMemoryKeyValueStore()
        assertTrue(FreshInstall.clearLeftovers(prefs, secrets))
        assertNull(secrets.get("chatgpt.refresh"))
        // Only once: what is signed in after that stays.
        secrets.set("claude.token", "new")
        assertFalse(FreshInstall.clearLeftovers(prefs, secrets))
        assertEquals("new", secrets.get("claude.token"))
    }

    @Test fun anUpdateKeepsSignIns() {
        val secrets = InMemorySecretStore("chatgpt.refresh" to "mine")
        val prefs = InMemoryKeyValueStore().apply { putString("settings.provider", "chatgpt") }
        assertFalse(FreshInstall.clearLeftovers(prefs, secrets))
        assertEquals("mine", secrets.get("chatgpt.refresh"))
    }
}
