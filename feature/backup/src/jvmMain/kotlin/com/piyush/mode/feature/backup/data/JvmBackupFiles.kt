package com.piyush.mode.feature.backup.data

import java.io.File

/** java.io files; [tempDir] defaults to the JVM's temporary folder. */
public class JvmBackupFiles(override val tempDir: String = System.getProperty("java.io.tmpdir").trimEnd('/')) : BackupFiles {
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
