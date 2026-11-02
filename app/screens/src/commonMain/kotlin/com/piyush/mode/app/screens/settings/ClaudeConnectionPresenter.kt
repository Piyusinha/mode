package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.llm.ClaudeOAuthClient
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelConnectionCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Account UI state is separate from model preferences, ready for a dedicated connection screen. */
public data class ClaudeConnectionUiState(
    val connected: Boolean = false,
    val signingIn: Boolean = false,
    val awaitingCode: Boolean = false,
    val error: String? = null,
    val checking: Boolean = false,
    val check: String? = null,
)

public class ClaudeConnectionPresenter(
    parent: CoroutineScope,
    private val account: ClaudeAccount,
    private val client: LlmClient?,
    private val onConnected: () -> Unit = {},
) : Presenter<ClaudeConnectionUiState>(parent, ClaudeConnectionUiState(connected = account.connected.value)) {
    private var work: Job? = null

    init {
        scope.launch { account.connected.collect { c -> mutableState.update { it.copy(connected = c) } } }
    }

    public fun start(): String? {
        if (state.value.signingIn) return null
        work?.cancel()
        val url = account.start()
        mutableState.value = ClaudeConnectionUiState(connected = account.connected.value, awaitingCode = true)
        return url
    }

    public fun finish(code: String) {
        if (state.value.signingIn || !state.value.awaitingCode || code.isBlank()) return
        work?.cancel()
        mutableState.update { it.copy(signingIn = true, checking = false, error = null) }
        work = scope.launch {
            try {
                withTimeout(TIMEOUT_MS) { account.finish(code) }
                onConnected()
                mutableState.value = ClaudeConnectionUiState(connected = true)
            } catch (e: TimeoutCancellationException) {
                mutableState.update { it.copy(signingIn = false, error = "Claude sign-in timed out. Start again.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(signingIn = false, error = e.message ?: "Claude sign-in failed. Start again.") }
            }
        }
    }

    public fun cancel() {
        work?.cancel()
        account.cancel()
        mutableState.value = ClaudeConnectionUiState(connected = account.connected.value)
    }

    public fun disconnect() {
        cancel()
        account.signOut()
        mutableState.value = ClaudeConnectionUiState()
    }

    public fun check(image: ByteArray) {
        val llm = client ?: return
        if (state.value.signingIn || state.value.checking || !account.connected.value) return
        mutableState.update { it.copy(checking = true, check = null, error = null) }
        work = scope.launch {
            val result = try {
                if (withTimeout(TIMEOUT_MS) { ModelConnectionCheck.verify(llm, ClaudeOAuthClient.MAIN_MODEL, image) })
                    "Connected: Claude read the test image and returned a valid action. Billing allowance is not verified."
                else "Claude replied, but the image/action check did not pass. Try again."
            } catch (e: TimeoutCancellationException) {
                "Claude did not answer within 30 seconds. Try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Claude connection check failed."
            }
            mutableState.update { it.copy(checking = false, check = result.takeIf { account.connected.value }) }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
