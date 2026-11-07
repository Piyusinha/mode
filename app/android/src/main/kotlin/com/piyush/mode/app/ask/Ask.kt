package com.piyush.mode.app.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.home.HomePresenter
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.design.ModeChip
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeMessageBar
import com.piyush.mode.design.ModeMessageBarWords
import com.piyush.mode.design.ModeSheet
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle

/** A new task, from the Speak button: suggestions to start from, and the message bar. */
@Composable
fun AskScreen(suggestions: List<String>, draft: String, onDraft: (String) -> Unit, isPrivate: Boolean, onTogglePrivate: () -> Unit, onSend: () -> Unit, onSpeak: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.screenEdge)) {
        ModeText(
            stringResource(R.string.ask_title), ModeTextStyle.Headline,
            Modifier.align(Alignment.CenterHorizontally).padding(bottom = ModeSpacing.l).semantics { heading() },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
            suggestions.forEach { s -> ModeChip(s, icon = ModeIcon.Sparkle, action = stringResource(R.string.common_continue), onAction = { onDraft(s) }) }
        }
        ModeMessageBar(
            draft, onDraft, isPrivate, draft.isNotBlank(),
            ModeMessageBarWords(
                stringResource(R.string.ask_prompt), stringResource(R.string.ask_add), stringResource(R.string.ask_send),
                stringResource(R.string.tab_ask), stringResource(R.string.ask_private_on), stringResource(R.string.ask_private_off),
            ),
            onTogglePrivate = onTogglePrivate, onSend = onSend, onSpeak = onSpeak, onAdd = onTogglePrivate,
            modifier = Modifier.padding(vertical = ModeSpacing.s),
        )
    }
}

/** The Ask sheet: a new task starts in a new chat, which opens once it's sent. */
@Composable
fun AskRoute(graph: ModeGraph, onSent: (String) -> Unit, onDismiss: () -> Unit) {
    val presenter: HomePresenter = rememberPresenter("ask") { graph.screens.home() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    ModeSheet(onDismiss, tall = true) {
        AskScreen(
            ui.suggestions.map { it.text }, draft, { draft = it }, isPrivate, { isPrivate = !isPrivate },
            onSend = {
                presenter.ask(draft, isPrivate)?.let { conversation ->
                    draft = ""
                    onSent(conversation)
                }
            },
            onSpeak = { AppLog.log("ask", "talking mode isn't on Android yet") },
        )
    }
}
