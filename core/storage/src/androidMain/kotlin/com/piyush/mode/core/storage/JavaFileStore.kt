package com.piyush.mode.core.storage

import java.io.File

/** A [FileStore] under one folder on disk (the app's private files on Android). Paths are relative to it. */
public class JavaFileStore(public val root: File) : FileStore {
    override fun append(path: String, text: String) {
        val file = full(path)
        file.parentFile?.mkdirs()
        file.appendText(text)
    }

    override fun write(path: String, bytes: ByteArray) {
        val file = full(path)
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, ".${file.name}.partial")
        partial.writeBytes(bytes)
        if (!partial.renameTo(file)) {
            file.delete()
            check(partial.renameTo(file)) { "Could not write $path." }
        }
    }

    override fun read(path: String): ByteArray? = full(path).takeIf { it.isFile }?.readBytes()

    override fun list(dir: String): List<String> =
        full(dir).list()?.filterNot { it.startsWith(".") }?.sorted().orEmpty()

    override fun delete(path: String) {
        full(path).deleteRecursively()
    }

    private fun full(path: String): File {
        val file = if (path.isEmpty()) root else File(root, path)
        require(file.canonicalPath.startsWith(root.canonicalPath)) { "a path must stay inside the store" }
        return file
    }
}
