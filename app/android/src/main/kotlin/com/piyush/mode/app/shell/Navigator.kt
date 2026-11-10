package com.piyush.mode.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import com.piyush.mode.app.screens.common.Presenter

/** One page on a tab's stack; [id] owns the presenters the page made. */
data class Entry(val id: String, val route: Route)

/**
 * Where the user is: the tab showing and one back stack per tab, as on the iPhone. Popping a page
 * closes the presenters it made; tapping the tab that's showing goes back to its first page.
 */
class Navigator(private val presenters: PresenterStore) {
    var tab: AppTab by mutableStateOf(AppTab.Home)
        private set

    private val stacks = AppTab.entries.associateWith { mutableStateListOf<Entry>() }
    private var next = 0

    /** A link that arrived before the main tabs were showing (the first job's chat, a notification). */
    var pending: Route? by mutableStateOf(null)

    /**
     * The open voice session (a mic, the assistant), or a spoken question that came forward with none open. A
     * session lives here, not on its screen, so the screen can shrink into the session bar and come back.
     */
    var talk: TalkSession? by mutableStateOf(null)
        private set

    /** The voice screen is up (else an open session shows as the session bar). */
    var talkOpen: Boolean by mutableStateOf(false)
    private var talks = 0

    /**
     * Opens talking mode. A session that's already open comes back up when it's this chat's (or no chat was
     * named, or it has none yet); otherwise it ends and a new one takes its place.
     */
    fun talk(request: com.piyush.mode.app.talk.TalkRequest) {
        val current = talk
        if (current != null && !current.request.forQuestion) {
            // A question in the open session's task: its screen comes up to ask it.
            if (request.forQuestion) { talkOpen = true; return }
            val chat = presenter(current)?.state?.value?.chat
            if (request.conversationId == null || chat == null || chat == request.conversationId) { talkOpen = true; return }
            presenter(current)?.endNow()
        }
        endTalk()
        talk = TalkSession(talks++, request)
        talkOpen = true
    }

    /** The session is over (End, ×, "that's all"), or the question's screen closed: the screen and the bar go. */
    fun endTalk() {
        val current = talk ?: return
        talk = null
        talkOpen = false
        presenter(current)?.dismiss()
        presenters.release(current.owner)
    }

    private fun presenter(session: TalkSession): com.piyush.mode.app.screens.settings.ListenPresenter? =
        presenters.find(session.owner, TalkSession.PRESENTER)

    fun stack(tab: AppTab): List<Entry> = stacks.getValue(tab)

    fun push(route: Route) {
        stacks.getValue(tab).add(Entry("page${next++}", route))
    }

    /** Back on the tab showing: false when it's already on its first page. */
    fun pop(): Boolean {
        val stack = stacks.getValue(tab)
        if (stack.isEmpty()) return false
        presenters.release(stack.removeAt(stack.lastIndex).id)
        return true
    }

    private fun popAll() {
        while (pop()) continue
    }

    fun select(tab: AppTab) {
        if (tab == this.tab) {
            popAll()
        } else {
            this.tab = tab
        }
    }

    /** Back to Home's first page with every stack empty (after Reset Mode). */
    fun reset() {
        endTalk()
        AppTab.entries.forEach { stacks.getValue(it).clear() }
        tab = AppTab.Home
        pending = null
    }

    /** Opens [route] on [tab] over its first page (a deep link). */
    fun open(tab: AppTab, route: Route) {
        this.tab = tab
        popAll()
        push(route)
    }
}

/** One opening of talking mode: what opened it, and the owner of its presenter (each opening has its own). */
class TalkSession(val id: Int, val request: com.piyush.mode.app.talk.TalkRequest) {
    val owner: String = "talk$id"

    /** Mode greeted once: the screen coming back from the session bar doesn't start the session over. */
    var begun: Boolean = false

    companion object {
        const val PRESENTER = "talk"
    }
}

/**
 * The presenters the pages made, by owner (a page's [Entry.id], or a fixed owner like the first run),
 * kept across rotation and closed when their page goes.
 */
class PresenterStore {
    private val held = mutableMapOf<String, MutableMap<String, Presenter<*>>>()

    @Suppress("UNCHECKED_CAST")
    fun <P : Presenter<*>> get(owner: String, key: String, create: () -> P): P =
        held.getOrPut(owner) { mutableMapOf() }.getOrPut(key, create) as P

    /** The presenter [owner] already made under [key], if it has. */
    @Suppress("UNCHECKED_CAST")
    fun <P : Presenter<*>> find(owner: String, key: String): P? = held[owner]?.get(key) as? P

    fun release(owner: String) {
        held.remove(owner)?.values?.forEach { it.close() }
    }

    fun releaseAll() {
        held.keys.toList().forEach(::release)
    }
}

/** Survives rotation: the navigator and the presenters, closed when the activity finishes. */
class ShellViewModel : ViewModel() {
    val presenters: PresenterStore = PresenterStore()
    val navigator: Navigator = Navigator(presenters)

    override fun onCleared() {
        presenters.releaseAll()
    }
}

private val LocalPresenters = staticCompositionLocalOf<PresenterStore> { error("no PresenterStore") }
private val LocalOwner = staticCompositionLocalOf { "root" }

/** Gives the pages inside [owner] their own presenters. */
@Composable
fun PresenterOwner(store: PresenterStore, owner: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPresenters provides store, LocalOwner provides owner, content = content)
}

/** The page's presenter, made once and kept until the page goes. */
@Composable
fun <P : Presenter<*>> rememberPresenter(key: String, create: () -> P): P {
    val store = LocalPresenters.current
    val owner = LocalOwner.current
    return remember(owner, key) { store.get(owner, key, create) }
}
