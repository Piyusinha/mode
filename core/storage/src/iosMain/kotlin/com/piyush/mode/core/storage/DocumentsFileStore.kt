@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.piyush.mode.core.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData
import platform.Foundation.writeToFile
import platform.posix.memcpy

/** A [FileStore] under Documents/[folder]: shared with the Files app and Finder. */
public class DocumentsFileStore(folder: String) : FileStore {
    public val root: String by lazy {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        "$docs/$folder"
    }
    private val files = NSFileManager.defaultManager

    override fun append(path: String, text: String) {
        val full = full(path)
        val data = text.encodeToByteArray().toNSData()
        if (!files.fileExistsAtPath(full)) {
            ensureDir(full)
            data.writeToFile(full, atomically = true)
            return
        }
        val handle = NSFileHandle.fileHandleForWritingAtPath(full) ?: return
        handle.seekToEndOfFile()
        handle.writeData(data)
        handle.closeFile()
    }

    override fun write(path: String, bytes: ByteArray) {
        val full = full(path)
        ensureDir(full)
        bytes.toNSData().writeToFile(full, atomically = true)
    }

    override fun read(path: String): ByteArray? = NSData.dataWithContentsOfFile(full(path))?.let { data ->
        val size = data.length.toInt()
        ByteArray(size).also { out -> if (size > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    override fun list(dir: String): List<String> =
        files.contentsOfDirectoryAtPath(full(dir), null)?.map { it as String }?.filterNot { it.startsWith(".") }.orEmpty()

    override fun delete(path: String) {
        files.removeItemAtPath(full(path), null)
    }


    private fun full(path: String) = if (path.isEmpty()) root else "$root/$path"

    private fun ensureDir(file: String) {
        files.createDirectoryAtPath(file.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
}
