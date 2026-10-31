@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.coredevice

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile

/**
 * The remote-pairing file that lets Mode reach this phone's developer services.
 * `tools/make-pairing.sh` puts it in Mode's Documents; it can also be dropped
 * there through Finder or the Files app.
 */
object PairingFile {
    const val NAME = "mode.rppairing"

    val path: String
        get() {
            val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
            return "$docs/$NAME"
        }

    /** The file's bytes, or null when it has not been installed yet. */
    fun load(): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()?.takeIf { it.isNotEmpty() }
}
