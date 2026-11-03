package com.piyush.mode.testing

import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.core.storage.SecretStore

class InMemorySecretStore(vararg initial: Pair<String, String>) : SecretStore {
    val values = mutableMapOf(*initial)
    override fun get(name: String): String? = values[name]
    override fun set(name: String, value: String?) {
        if (value.isNullOrBlank()) values.remove(name) else values[name] = value
    }
    override fun clear() = values.clear()
}

class InMemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun getString(name: String): String? = values[name]
    override fun putString(name: String, value: String) {
        values[name] = value
    }
    override fun clear() = values.clear()
}

/** Files in a map, with folders implied by "/" in the paths. */
class InMemoryFileStore : FileStore {
    val files = mutableMapOf<String, ByteArray>()
    override fun append(path: String, text: String) {
        files[path] = (files[path] ?: ByteArray(0)) + text.encodeToByteArray()
    }
    override fun write(path: String, bytes: ByteArray) {
        files[path] = bytes
    }
    override fun read(path: String): ByteArray? = files[path]
    override fun list(dir: String): List<String> {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        return files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct()
    }
    override fun delete(path: String) {
        files.keys.removeAll { it == path || it.startsWith("$path/") || path.isEmpty() }
    }
}
