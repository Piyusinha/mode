package com.piyush.mode.feature.backup.data

/** Files by absolute path: the backup Mode writes for the share sheet, and the one picked to restore. */
public interface BackupFiles {
    /** Where a new backup is written before it is shared (the app's temporary folder). */
    public val tempDir: String

    public fun read(path: String): ByteArray?
    public fun write(path: String, bytes: ByteArray)
    public fun delete(path: String)
}

/** True when [path] is inside [BackupFiles.tempDir]: only such copies are Mode's to delete. */
internal fun BackupFiles.isTemp(path: String): Boolean {
    // iOS names the same folder /private/var/… or /var/… depending on who asks.
    fun plain(p: String) = p.removePrefix("/private")
    val dir = plain(tempDir.trimEnd('/'))
    return dir.isNotEmpty() && plain(path).startsWith("$dir/") && !path.contains("/../")
}
