package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.voice.Heard
import com.piyush.mode.feature.settings.voice.Voices
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

public data class ListenUiState(
    /** listening, typing, or sent. */
    val phase: String = "listening",
    val transcript: String = "",
)

/** Opens the microphone, hears one task, says what it will do, then hands it to the queue. */
public class ListenPresenter(
    parent: CoroutineScope,
    private val voices: Voices,
    private val queue: TaskQueue,
) : Presenter<ListenUiState>(parent, ListenUiState()) {
    private var job: Job? = null

    public fun begin() {
        if (job?.isActive == true) return
        job = scope.launch {
            mutableState.value = ListenUiState("listening")
            try {
                while (true) {
                    when (val heard = voices.hear()) {
                        Heard.Silence -> Unit
                        is Heard.Words -> {
                            mutableState.value = ListenUiState("sent", heard.text)
                            voices.speak("I'll ${heard.text}")
                            queue.submit(heard.text, TaskSource.Voice, null, false)
                            return@launch
                        }
                        Heard.Nothing -> {
                            mutableState.value = ListenUiState("typing")
                            return@launch
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    public fun submitTyped(text: String) {
        val task = text.trim()
        if (task.isEmpty()) return
        scope.launch {
            voices.speak("I'll $task")
            queue.submit(task, TaskSource.App, null, false)
            mutableState.value = ListenUiState("sent", task)
        }
    }

    public fun stop() {
        job?.cancel()
        job = null
    }
}
