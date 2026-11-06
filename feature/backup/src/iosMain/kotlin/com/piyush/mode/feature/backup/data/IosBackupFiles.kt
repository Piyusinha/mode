@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.piyush.mode.feature.backup.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/** Files by absolute path; [tempDir] is the app's tmp folder (iOS clears it when space runs low). */
public class IosBackupFiles : BackupFiles {
    private val files = NSFileManager.defaultManager

    override val tempDir: String get() = NSTemporaryDirectory().trimEnd('/')

    override fun read(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.let { data ->
        val size = data.length.toInt()
        ByteArray(size).also { out -> if (size > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    override fun write(path: String, bytes: ByteArray) {
        files.createDirectoryAtPath(path.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.convert()) }
        check(data.writeToFile(path, atomically = true)) { "Could not write the backup file." }
    }

    override fun delete(path: String) {
        files.removeItemAtPath(path, null)
    }
}

public actual fun platformBackupFiles(): BackupFiles = IosBackupFiles()
