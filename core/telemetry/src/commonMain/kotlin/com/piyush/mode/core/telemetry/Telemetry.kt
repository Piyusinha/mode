package com.piyush.mode.core.telemetry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Versioned, bounded names: user content can never become an operation or event name. */
enum class Operation(val wireName: String) {
    Task("mode.task"), TaskLock("mode.task.lock_wait"), MemoryPrepare("mode.memory.prepare"),
    Agent("mode.agent.run"), BrainBrief("mode.brain.brief"), BrainDecide("mode.brain.decide"),
    ScreenLook("mode.screen.look"), ScreenCapture("mode.screen.capture"),
    ScreenOcr("mode.screen.ocr"), ImageEncode("mode.screen.image_encode"), ModelRequest("gen_ai.chat"),
    PhoneAcquire("mode.phone.acquire"), PhoneLock("mode.phone.lock_wait"),
    PhoneConnect("mode.phone.connect"), PhoneDisconnect("mode.phone.disconnect"),
    PhoneStop("mode.phone.stop"), PhoneOp("mode.phone.operation"),
    StreamPause("mode.screen_stream.pause"), StreamResume("mode.screen_stream.resume"),
    VpnStart("mode.vpn.start"), VpnStop("mode.vpn.stop"),
    AccessToken("mode.auth.access_token"),
    PairingLoad("mode.pairing.load"), DeviceOpen("mode.device.open"), OcrWarmup("mode.ocr.warmup"),
    Reflection("mode.memory.reflect"), VoiceSpeak("mode.voice.speak"),
    VoiceTurn("mode.voice.turn"), VoiceHear("mode.voice.hear"), VoiceRecord("mode.voice.record"),
    VoiceTranscribe("mode.voice.transcribe"), VoiceSynthesize("mode.voice.synthesize"),
    /** One talking-mode screen, from how it opened (Field.Entry) to how it ended (Field.Outcome). */
    VoiceSession("mode.voice.session"),
    /** What Mode says when handed a task: written by the AI (Outcome AI) or Mode's own line (Fallback). */
    VoiceAcknowledge("mode.voice.acknowledge"),
    /** A spoken update while a spoken task works (Outcome Spoke, Skipped, Failed). */
    VoiceUpdate("mode.voice.update"),
}

enum class Event(val wireName: String) {
    FirstAction("mode.task.first_action"), AppStarted("mode.app.started"),
    /** The agent asked to give up; Field.Outcome: accepted or refused; Field.EndReason: what blocked it. */
    AgentGiveUp("mode.agent.give_up"),
    /** An order, payment, booking or send: Field.Outcome: tapped, verified, no_effect or unclear. */
    AgentCommit("mode.agent.commit"),
    /** A rung of the recovery ladder; Field.Outcome: the tier. */
    AgentRecovery("mode.agent.recovery"),
    Foreground("mode.app.foreground"), Background("mode.app.background"),
    /** Siri or Shortcuts ran "Start Mode" (listen) or "Ask Mode" (a task in words); Field.Outcome says what Mode did. */
    IntentStartListening("mode.intent.start_listening"), IntentAsk("mode.intent.ask"),
    /** The app followed a link: Siri's listen, a mic, a Live Activity tap (Field.Entry). */
    LinkOpened("mode.app.link_opened"),
    /** In a voice session: the microphone is listening (each turn's end is on mode.voice.record), a task was sent. */
    ListenStarted("mode.voice.listen_started"), TaskSent("mode.voice.task_sent"),
    /** In a voice session: no microphone or speech access; nothing understood after every try. */
    MicBlocked("mode.voice.mic_blocked"), NothingHeard("mode.voice.nothing_heard"),
    /** The user talked over Mode and it stopped mid-line; Field.Words: how many of theirs were heard. */
    BargeIn("mode.voice.barge_in"),
}

/** Only these fields may cross the telemetry boundary. No arbitrary attribute keys or log bodies. */
enum class Field(val wireName: String) {
    Model("gen_ai.request.model"), Provider("gen_ai.provider.name"), Effort("mode.model.effort"),
    InputTokens("gen_ai.usage.input_tokens"), OutputTokens("gen_ai.usage.output_tokens"),
    CachedTokens("gen_ai.usage.cache_read.input_tokens"), CacheWriteTokens("mode.usage.cache_write_tokens"),
    Outcome("mode.outcome"), ErrorType("error.type"), Cancelled("mode.cancelled"),
    Operation("mode.phone.operation"), TaskId("mode.task.id"), Source("mode.task.source"),
    BuiltInVpn("mode.vpn.built_in"), DurationMs("mode.duration_ms"),
    /** How talking mode opened: siri, ask_mic, chat_mic, question, link. */
    Entry("mode.voice.entry"),
    /** Why listening stopped: quiet, settled, limit, done, cancelled, failed. */
    EndReason("mode.voice.end_reason"),
    /** How many words were heard (never the words themselves). */
    Words("mode.voice.words"),
}

typealias Fields = Map<Field, String>

/** A W3C carrier for native bridges; SDK types stay private to this module. */
class TraceContext(val traceparent: String, internal val local: Any? = null)

interface TraceSpan {
    val context: TraceContext?
    fun fields(fields: Fields) {}
    fun event(event: Event, fields: Fields = emptyMap()) {}
    /** Error messages and exception stacks deliberately never enter the export. */
    fun failed(type: String) {}
    fun end() {}

    object None : TraceSpan { override val context: TraceContext? = null }
}

interface Telemetry {
    fun start(operation: Operation, parent: TraceContext? = null, fields: Fields = emptyMap()): TraceSpan
    fun event(event: Event, parent: TraceContext? = null, fields: Fields = emptyMap()) {}

    /** The newest spans and events in memory (null: this telemetry keeps none). */
    val recent: RecentTelemetry? get() = null

    object None : Telemetry {
        override fun start(operation: Operation, parent: TraceContext?, fields: Fields): TraceSpan = TraceSpan.None
    }
}

/** Coroutine inheritance, including dispatcher switches, without thread locals or global current spans. */
internal class ActiveTrace(val owner: Telemetry, val trace: TraceContext) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ActiveTrace>
}

suspend fun Telemetry.currentContext(): TraceContext? =
    currentCoroutineContext()[ActiveTrace]?.takeIf { it.owner === this }?.trace

/** Telemetry errors cannot replace business results or cancellation. */
suspend fun <T> Telemetry.trace(
    operation: Operation,
    fields: Fields = emptyMap(),
    parent: TraceContext? = null,
    block: suspend (TraceSpan) -> T,
): T {
    val span = runCatching { start(operation, parent ?: currentContext(), fields) }.getOrDefault(TraceSpan.None)
    try {
        val context = span.context
        return if (context == null) block(span) else withContext(ActiveTrace(this, context)) { block(span) }
    } catch (e: CancellationException) {
        runCatching { span.fields(mapOf(Field.Cancelled to "true")) }
        throw e
    } catch (e: Throwable) {
        runCatching { span.failed(e::class.simpleName ?: "Exception") }
        throw e
    } finally {
        runCatching { span.end() }
    }
}

/** Explicit entry point for Swift/Rust/another process carrying a traceparent. */
suspend fun <T> Telemetry.withParent(parent: TraceContext, block: suspend () -> T): T =
    withContext(ActiveTrace(this, parent)) { block() }

/** Measure acquisition alone while keeping Mutex.withLock's cancellation-safe ownership. */
suspend fun <T> Telemetry.withLock(mutex: Mutex, operation: Operation, block: suspend () -> T): T {
    val waiting = runCatching { start(operation, currentContext()) }.getOrDefault(TraceSpan.None)
    var ended = false
    try {
        return mutex.withLock {
            runCatching { waiting.end() }
            ended = true
            block()
        }
    } finally {
        if (!ended) runCatching { waiting.end() }
    }
}

/** Strict value validation supplements the field allowlist. Never truncate rejected content into a log. */
internal fun Field.safe(value: String): String? {
    if (value.length > 160 || value.contains("bearer", ignoreCase = true) || value.startsWith("sk-")) return null
    return when (this) {
        Field.InputTokens, Field.OutputTokens, Field.CachedTokens, Field.CacheWriteTokens, Field.DurationMs, Field.Words ->
            value.toLongOrNull()?.takeIf { it >= 0 }?.toString()
        Field.Cancelled, Field.BuiltInVpn -> value.takeIf { it == "true" || it == "false" }
        Field.Effort -> value.takeIf { it in setOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "auto") }
        else -> value.takeIf { it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._:/+\\-]{0,159}")) }
    }
}
