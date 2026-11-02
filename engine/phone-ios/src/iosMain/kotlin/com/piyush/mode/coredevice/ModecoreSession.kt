@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.coredevice

import cnames.structs.McSession
import com.piyush.mode.coredevice.native.mc_bytes_free
import com.piyush.mode.coredevice.native.mc_button
import com.piyush.mode.coredevice.native.mc_key_named
import com.piyush.mode.coredevice.native.mc_launch
import com.piyush.mode.coredevice.native.mc_launch_fresh
import com.piyush.mode.coredevice.native.mc_list_apps
import com.piyush.mode.coredevice.native.mc_paste_text
import com.piyush.mode.coredevice.native.mc_screenshot
import com.piyush.mode.coredevice.native.mc_session_close
import com.piyush.mode.coredevice.native.mc_session_open_rppairing
import com.piyush.mode.coredevice.native.mc_session_pause_stream
import com.piyush.mode.coredevice.native.mc_session_resume_stream
import com.piyush.mode.coredevice.native.mc_string_free
import com.piyush.mode.coredevice.native.mc_touch
import com.piyush.mode.coredevice.native.mc_type_ascii
import com.piyush.mode.ops.OpFailed
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** [DeviceSession] over `native/modecore`'s C API. */
class ModecoreSession private constructor(
    private var handle: CPointer<McSession>?,
    /** True when opening had to mount the developer disk image (the phone had restarted). */
    val mountedDiskImage: Boolean,
) : DeviceSession {

    override fun touch(x1: Int, y1: Int, x2: Int, y2: Int, holdMs: Long, travelMs: Long) = check(
        mc_touch(
            live(), x1.convert(), y1.convert(), x2.convert(), y2.convert(),
            holdMs.coerceIn(0, UInt.MAX_VALUE.toLong()).convert(),
            travelMs.coerceIn(0, UInt.MAX_VALUE.toLong()).convert(),
        ),
    )

    override fun button(button: HardwareButton) = check(mc_button(live(), button.ordinal.convert()))

    override fun key(name: String) = check(mc_key_named(live(), name))

    override fun typeAscii(text: String) = check(mc_type_ascii(live(), text))

    override fun pasteText(text: String) = check(mc_paste_text(live(), text))

    override fun screenshotPng(): ByteArray = memScoped {
        val out = alloc<CPointerVar<UByteVar>>()
        val len = alloc<ULongVar>()
        check(mc_screenshot(live(), out.ptr, len.ptr))
        val bytes = out.value ?: throw OpFailed("the phone sent an empty screenshot")
        try {
            bytes.reinterpret<ByteVar>().readBytes(len.value.toInt())
        } finally {
            mc_bytes_free(bytes, len.value.convert())
        }
    }

    override fun launch(bundleId: String, fresh: Boolean) =
        check(if (fresh) mc_launch_fresh(live(), bundleId) else mc_launch(live(), bundleId))

    override fun listApps(): List<InstalledApp> = memScoped {
        val out = alloc<CPointerVar<ByteVar>>()
        check(mc_list_apps(live(), out.ptr))
        val json = out.value?.let { ptr -> ptr.toKString().also { mc_string_free(ptr) } } ?: "[]"
        Json.parseToJsonElement(json).jsonArray.map {
            val app = it.jsonObject
            InstalledApp(app.getValue("name").jsonPrimitive.content, app.getValue("bundle_id").jsonPrimitive.content)
        }
    }

    override fun pauseStream() = check(mc_session_pause_stream(live()))

    override fun resumeStream() = check(mc_session_resume_stream(live()))

    override fun close() {
        handle?.let(::mc_session_close)
        handle = null
    }

    private fun live(): CPointer<McSession> = handle ?: throw OpFailed("the phone session is closed")

    companion object {
        /**
         * Connects to the phone's remote-pairing service at [host]:[port] (an IP
         * literal) with a remote-pairing file, and opens the screen stream.
         *
         * If the phone has no developer disk image mounted (it restarted) and
         * [diskImageDir] names one, it is mounted first; that needs internet for
         * Apple's signature and takes several seconds.
         *
         * Blocks for about a second; call it off the main thread.
         */
        fun open(
            host: String,
            port: Int,
            hostname: String,
            pairingFile: ByteArray,
            diskImageDir: String? = null,
        ): ModecoreSession {
            require(pairingFile.isNotEmpty()) { "the pairing file is empty" }
            return memScoped {
                val out = alloc<CPointerVar<McSession>>()
                val mounted = alloc<UByteVar>()
                val error = pairingFile.usePinned { pinned ->
                    mc_session_open_rppairing(
                        host, port.convert(), hostname,
                        pinned.addressOf(0).reinterpret(), pairingFile.size.convert(),
                        diskImageDir, out.ptr, mounted.ptr,
                    )
                }
                check(error)
                ModecoreSession(
                    out.value ?: throw OpFailed("the phone session did not open"),
                    mountedDiskImage = mounted.value.toInt() != 0,
                )
            }
        }
    }
}

/** Turns modecore's error string (NULL means success) into an [OpFailed]. */
private fun check(error: CPointer<ByteVar>?) {
    if (error == null) return
    val message = error.toKString()
    mc_string_free(error)
    throw OpFailed(message)
}
