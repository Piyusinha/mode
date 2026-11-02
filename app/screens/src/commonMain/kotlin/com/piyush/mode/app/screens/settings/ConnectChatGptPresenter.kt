package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ResponsesClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

public sealed interface ChatGptPhase {
    public data object Idle : ChatGptPhase
    /** The ChatGPT page is open; the app waits for it to come back. */
    public data object Waiting : ChatGptPhase
    /** Signed in; checking the plan works (its models, one call). */
    public data object Checking : ChatGptPhase
    public data class Connected(val models: List<String>, val check: String) : ChatGptPhase
    public data class Failed(val message: String, val notEligible: Boolean) : ChatGptPhase
}

public data class ConnectChatGptUiState(
    val phase: ChatGptPhase = ChatGptPhase.Idle,
    val connected: Boolean = false,
)

/** "Continue with ChatGPT": sign in, then prove the plan works with one call that must use a tool. */
public class ConnectChatGptPresenter(
    parent: CoroutineScope,
    private val account: ChatGptAccount,
    private val llm: LlmClient,
    /** Signed in: ChatGPT becomes the provider in use. */
    private val onConnected: () -> Unit = {},
) : Presenter<ConnectChatGptUiState>(parent, ConnectChatGptUiState(connected = account.connected.value)) {

    init {
        scope.launch { account.connected.collect { c -> mutableState.value = mutableState.value.copy(connected = c) } }
    }

    /** The loopback address to listen on while the page is open, and its port. */
    public val redirectUri: String get() = account.redirectUri
    public val loopbackPort: Int get() = com.piyush.mode.llm.ChatGptAuth.LOOPBACK_PORT

    /** The ChatGPT page to open. */
    public fun start(): String {
        mutableState.value = mutableState.value.copy(phase = ChatGptPhase.Waiting)
        return account.beginSignIn()
    }

    /** The loopback received the sign-in's answer. */
    public fun finish(callbackUrl: String) {
        mutableState.value = mutableState.value.copy(phase = ChatGptPhase.Checking)
        scope.launch {
            val phase = try {
                account.finishSignIn(callbackUrl)
                onConnected()
                check()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.error("chatgpt", "sign-in or check failed", e)
                ChatGptPhase.Failed(e.message ?: "ChatGPT sign-in failed", notEligible = e.message == ResponsesClient.NOT_ELIGIBLE)
            }
            mutableState.value = mutableState.value.copy(phase = phase)
        }
    }

    /** The page was closed before it came back. */
    public fun cancelled() {
        account.cancelSignIn()
        if (mutableState.value.phase == ChatGptPhase.Waiting) mutableState.value = mutableState.value.copy(phase = ChatGptPhase.Idle)
    }

    /** Checks an account already signed in. */
    public fun recheck() {
        mutableState.value = mutableState.value.copy(phase = ChatGptPhase.Checking)
        scope.launch {
            mutableState.value = mutableState.value.copy(
                phase = runCatching { check() }.getOrElse { ChatGptPhase.Failed(it.message ?: "check failed", it.message == ResponsesClient.NOT_ELIGIBLE) },
            )
        }
    }

    public fun signOut() {
        account.signOut()
        mutableState.value = mutableState.value.copy(phase = ChatGptPhase.Idle)
    }

    private suspend fun check(): ChatGptPhase {
        val models = account.models().sorted()
        AppLog.log("chatgpt", "plan models: ${models.joinToString()}")
        val model = models.firstOrNull { it.startsWith("gpt") } ?: models.first()
        val reply = llm.chat(model, listOf(ChatMessage("system", "Answer with the done tool."), ChatMessage("user", "Say ready.")), CHECK_TOOL)
        val check = "$model answered ${reply.toolCall?.name?.let { "with the $it tool" } ?: "in text (no tool)"}, ${reply.usage.promptTokens} tokens in"
        AppLog.log("chatgpt", "check: $check")
        return ChatGptPhase.Connected(models, check)
    }

    private companion object {
        val CHECK_TOOL: JsonArray = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "done")
                    put("description", "Finish with a short message.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") { putJsonObject("message") { put("type", "string") } }
                        putJsonArray("required") { add(JsonPrimitive("message")) }
                    }
                }
            })
        }
    }
}
