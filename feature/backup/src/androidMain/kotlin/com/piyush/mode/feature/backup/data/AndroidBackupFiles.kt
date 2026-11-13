package com.piyush.mode.feature.backup.data

import java.io.File

/**
 * Files by absolute path; [tempDir] is the app's cache folder, where a new backup
 * waits until the system picker copies it where the user chose.
 */
public class AndroidBackupFiles(cacheDir: File) : BackupFiles {
    override val tempDir: String = File(cacheDir, "backup").absolutePath

    override fun read(path: String): ByteArray? = File(path).takeIf { it.isFile }?.readBytes()

    override fun write(path: String, bytes: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, ".${file.name}.partial")
        partial.writeBytes(bytes)
        if (!partial.renameTo(file)) {
            file.delete()
            check(partial.renameTo(file)) { "Could not write the backup file." }
        }
    }

    override fun delete(path: String) {
        File(path).delete()
    }
}
