package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.firstrun.FirstRunPresenter
import com.piyush.mode.app.screens.firstrun.FirstRunStep
import com.piyush.mode.app.screens.firstrun.PhoneSetupGuide
import com.piyush.mode.app.screens.firstrun.PhoneSetupKind
import com.piyush.mode.app.screens.firstrun.PhoneSetupPresenter
import com.piyush.mode.app.screens.settings.ConnectChatGptPresenter
import com.piyush.mode.core.platform.Permission
import com.piyush.mode.core.platform.PermissionStatus
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import com.piyush.mode.feature.connection.domain.PhoneCheckResult
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneSetupTest {
    private fun results(engine: Boolean, locked: Boolean = false, battery: Boolean = false) = listOf(
        PhoneCheckResult(PhoneCheckId.RestrictedSettings, if (locked) CheckState.Failed("locked", null) else CheckState.Passed(null)),
        PhoneCheckResult(PhoneCheckId.Engine, if (engine) CheckState.Passed(null) else CheckState.Failed("off", null)),
        PhoneCheckResult(PhoneCheckId.Battery, if (battery) CheckState.Passed(null) else CheckState.Failed("restricted", null)),
    )

    @Test
    fun theRowsShowEachSettingAndContinueWaitsForPhoneControl() = runTest {
        val f = Fixture(this)
        f.checks.quick = results(engine = false)
        val p = PhoneSetupPresenter(backgroundScope, f.checks, f.permissions, FakeLifecycle())
        val s = p.state.awaitState { it.rows.isNotEmpty() }
        assertEquals(listOf(PhoneSetupKind.PhoneControl, PhoneSetupKind.Notifications, PhoneSetupKind.Assistant, PhoneSetupKind.Battery, PhoneSetupKind.Microphone), s.rows.map { it.kind })
        assertFalse(s.ready)
        assertFalse(s.rows.first().optional, "phone control is the one required setting")
        assertTrue(s.rows.last().later, "the microphone is asked when the user first speaks")
    }

    @Test
    fun turningOnPhoneControlOpensItsGuideWhichClosesWhenMOdeComesBackWithItOn() = runTest {
        val f = Fixture(this)
        val lifecycle = FakeLifecycle()
        f.checks.quick = results(engine = false)
        val p = PhoneSetupPresenter(backgroundScope, f.checks, f.permissions, lifecycle)
        p.state.awaitState { it.rows.isNotEmpty() }
        p.allow(PhoneSetupKind.PhoneControl)
        assertEquals(PhoneSetupGuide.PhoneControl, p.state.value.guide)
        // The user turns it on in Settings and comes back.
        f.checks.quick = results(engine = true)
        lifecycle.cameBack()
        val s = p.state.awaitState { it.ready }
        assertNull(s.guide, "the guide closes itself once the switch is on")
    }

    @Test
    fun aLockedSwitchShowsTheRestrictedSettingsGuideFirst() = runTest {
        val f = Fixture(this)
        f.checks.quick = results(engine = false, locked = true)
        val p = PhoneSetupPresenter(backgroundScope, f.checks, f.permissions, FakeLifecycle())
        p.state.awaitState { it.locked }
        p.allow(PhoneSetupKind.PhoneControl)
        assertEquals(PhoneSetupGuide.RestrictedSettings, p.state.value.guide)
        p.unlocked()
        assertEquals(PhoneSetupGuide.PhoneControl, p.state.value.guide)
    }

    @Test
    fun aSettingThisBuildCantOfferIsLeftOut() = runTest {
        val f = Fixture(this)
        f.checks.quick = results(engine = true)
        val permissions = object : com.piyush.mode.core.platform.Permissions by f.permissions {
            override suspend fun status(permission: Permission) =
                if (permission == Permission.Assistant) PermissionStatus.Unavailable else f.permissions.status(permission)
        }
        val p = PhoneSetupPresenter(backgroundScope, f.checks, permissions, FakeLifecycle())
        val s = p.state.awaitState { it.rows.isNotEmpty() }
        assertTrue(s.rows.none { it.kind == PhoneSetupKind.Assistant })
    }

    @Test
    fun notificationsAreAskedInPlace() = runTest {
        val f = Fixture(this)
        f.checks.quick = results(engine = true)
        val p = PhoneSetupPresenter(backgroundScope, f.checks, f.permissions, FakeLifecycle())
        p.state.awaitState { it.rows.isNotEmpty() }
        p.allow(PhoneSetupKind.Notifications)
        val s = p.state.awaitState { st -> st.rows.first { it.kind == PhoneSetupKind.Notifications }.status == PermissionStatus.Allowed }
        assertNull(s.guide)
        assertTrue(Permission.Notifications in f.permissions.granted)
    }

    @Test
    fun aMakersManualStepsAreRowsTheUserConfirmsAndNeverHoldUpContinue() = runTest {
        val f = Fixture(this)
        val manual = listOf(
            PhoneCheckResult(PhoneCheckId.Autostart, CheckState.Unverifiable(confirmed = false)),
            PhoneCheckResult(PhoneCheckId.Popups, CheckState.Unverifiable(confirmed = false)),
        )
        f.checks.quick = results(engine = true) + manual
        val p = PhoneSetupPresenter(backgroundScope, f.checks, f.permissions, FakeLifecycle(), f.settings)
        val s = p.state.awaitState { it.rows.isNotEmpty() }
        assertEquals(
            listOf(PhoneSetupKind.PhoneControl, PhoneSetupKind.Notifications, PhoneSetupKind.Assistant, PhoneSetupKind.Battery, PhoneSetupKind.Autostart, PhoneSetupKind.Popups, PhoneSetupKind.Microphone),
            s.rows.map { it.kind },
        )
        assertTrue(s.ready, "unconfirmed maker steps don't block Continue")
        val autostart = s.rows.first { it.kind == PhoneSetupKind.Autostart }
        assertTrue(autostart.manual && autostart.optional)
        assertEquals(PermissionStatus.NotAsked, autostart.status)

        p.allow(PhoneSetupKind.Autostart)
        assertEquals(PhoneSetupGuide.Autostart, p.state.value.guide)
        // Coming back from the maker's page proves nothing: the guide stays until the user confirms.
        f.checks.quick = results(engine = true) + manual
        p.refresh()
        assertEquals(PhoneSetupGuide.Autostart, p.state.awaitState { it.rows.isNotEmpty() }.guide)
        p.done(PhoneSetupKind.Autostart)
        assertNull(p.state.value.guide)
        assertEquals(setOf("Autostart"), f.settings.settings.value.deviceSteps, "remembered, so the checks read it as confirmed")
        f.checks.quick = results(engine = true) + listOf(
            PhoneCheckResult(PhoneCheckId.Autostart, CheckState.Unverifiable(confirmed = true)),
            PhoneCheckResult(PhoneCheckId.Popups, CheckState.Unverifiable(confirmed = false)),
        )
        p.refresh()
        val after = p.state.awaitState { st -> st.rows.first { it.kind == PhoneSetupKind.Autostart }.status == PermissionStatus.Allowed }
        assertEquals(PermissionStatus.NotAsked, after.rows.first { it.kind == PhoneSetupKind.Popups }.status)
    }

    @Test
    fun theShortcutRowClosesItsGuideOnceAndroidReadsItBack() = runTest {
        val f = Fixture(this)
        f.checks.quick = results(engine = true) + PhoneCheckResult(PhoneCheckId.Shortcut, CheckState.Unverifiable(confirmed = false))
        val p = PhoneSetupPresenter(backgroundScope, f.checks, f.permissions, FakeLifecycle(), f.settings)
        val s = p.state.awaitState { it.rows.isNotEmpty() }
        val row = s.rows.first { it.kind == PhoneSetupKind.Shortcut }
        assertTrue(row.optional && row.status == PermissionStatus.NotAsked)
        assertTrue(s.ready, "the shortcut never holds up Continue")
        p.allow(PhoneSetupKind.Shortcut)
        assertEquals(PhoneSetupGuide.Shortcut, p.state.value.guide)
        // Back from Accessibility with the button set to Mode: the guide closes by itself.
        f.checks.quick = results(engine = true) + PhoneCheckResult(PhoneCheckId.Shortcut, CheckState.Unverifiable(confirmed = true))
        p.refresh()
        assertNull(p.state.awaitState { st -> st.rows.first { it.kind == PhoneSetupKind.Shortcut }.status == PermissionStatus.Allowed }.guide)
    }

    @Test
    fun aPlainPhoneHasNoManualRows() = runTest {
        val f = Fixture(this)
        f.checks.quick = results(engine = true)
        val p = PhoneSetupPresenter(backgroundScope, f.checks, f.permissions, FakeLifecycle(), f.settings)
        val s = p.state.awaitState { it.rows.isNotEmpty() }
        assertTrue(s.rows.none { it.manual })
    }

    @Test
    fun withPhoneControlTheFirstRunHasAPhoneStepBeforeTheFirstJob() = runTest {
        val f = Fixture(this)
        val account = com.piyush.mode.feature.settings.data.ChatGptAccount(
            com.piyush.mode.llm.ChatGptAuth(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { respond("") })), com.piyush.mode.testing.InMemorySecretStore(),
        )
        val p = FirstRunPresenter(backgroundScope, f.settings, ConnectChatGptPresenter(backgroundScope, account, com.piyush.mode.testing.ScriptedModel()), f.queue, phoneStep = true)
        p.getStarted()
        p.state.awaitState { it.step == FirstRunStep.Connect && it.connected }
        p.next()
        assertEquals(FirstRunStep.Phone, p.state.awaitState { it.step == FirstRunStep.Phone }.step)
        p.next()
        assertEquals(FirstRunStep.FirstTask, p.state.awaitState { it.step == FirstRunStep.FirstTask }.step)
        p.back()
        assertEquals(FirstRunStep.Phone, p.state.awaitState { it.step == FirstRunStep.Phone }.step)
        assertEquals(4, p.totalSteps)
        assertEquals(4, p.stepNumber(FirstRunStep.FirstTask))
    }
}
