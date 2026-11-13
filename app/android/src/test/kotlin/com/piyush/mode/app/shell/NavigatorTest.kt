package com.piyush.mode.app.shell

import com.piyush.mode.app.screens.common.Presenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavigatorTest {
    private class Probe : Presenter<Int>(CoroutineScope(Dispatchers.Unconfined), 0) {
        val open: Boolean get() = scope.isActive
    }

    @Test
    fun eachTabKeepsItsOwnStack() {
        val nav = Navigator(PresenterStore())
        nav.push(Route.Conversation("a"))
        nav.select(AppTab.Tasks)
        nav.push(Route.TaskDebug("t"))
        assertEquals(listOf<Route>(Route.TaskDebug("t")), nav.stack(AppTab.Tasks).map { it.route })
        nav.select(AppTab.Home)
        assertEquals(listOf<Route>(Route.Conversation("a")), nav.stack(AppTab.Home).map { it.route })
    }

    @Test
    fun tappingTheTabShowingGoesBackToItsFirstPage() {
        val nav = Navigator(PresenterStore())
        nav.push(Route.Settings)
        nav.push(Route.Voice)
        nav.select(AppTab.Home)
        assertTrue(nav.stack(AppTab.Home).isEmpty())
        assertFalse(nav.pop())
    }

    @Test
    fun poppingAPageClosesItsPresenters() {
        val store = PresenterStore()
        val nav = Navigator(store)
        nav.push(Route.Voice)
        val owner = nav.stack(AppTab.Home).single().id
        val probe = store.get(owner, "voice") { Probe() }
        assertSame(probe, store.get(owner, "voice") { Probe() }, "one presenter per page, kept across recompositions")
        nav.pop()
        assertFalse(probe.open)
    }

    @Test
    fun aDeepLinkOpensOverTheTabsFirstPage() {
        val nav = Navigator(PresenterStore())
        nav.push(Route.Settings)
        nav.open(AppTab.Home, Route.Conversation("c"))
        assertEquals(listOf<Route>(Route.Conversation("c")), nav.stack(AppTab.Home).map { it.route })
    }
}
