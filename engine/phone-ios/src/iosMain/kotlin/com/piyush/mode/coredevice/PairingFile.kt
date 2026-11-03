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
 * Mode Setup (desktop/) and `tools/make-pairing.sh` put it in Mode's Documents as [NAME]; iLoader's
 * pairing manager writes [ILOADER_NAME] (its combined lockdown + remote pairing plist, whose extra
 * keys the reader ignores). It can also be dropped there through Finder or the Files app.
 */
object PairingFile {
    const val NAME = "mode.rppairing"
    const val ILOADER_NAME = "pairingFile.plist"

    private val docs: String
        get() = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

    val path: String
        get() = "$docs/$NAME"

    /** The file's bytes, or null when it has not been installed yet. Mode's own name wins. */
    fun load(): ByteArray? = listOf(NAME, ILOADER_NAME).firstNotNullOfOrNull { name ->
        NSData.dataWithContentsOfFile("$docs/$name")?.toByteArray()?.takeIf { it.isNotEmpty() }
    }
}
