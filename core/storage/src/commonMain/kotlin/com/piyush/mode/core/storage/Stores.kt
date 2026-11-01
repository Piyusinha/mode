package com.piyush.mode.core.storage

/** Small secrets such as API keys: encrypted, on this device only (the Keychain on iOS). */
public interface SecretStore {
    public fun get(name: String): String?

    /** Stores [value], or removes the entry when it is null or blank. */
    public fun set(name: String, value: String?)
}

/** Plain settings (UserDefaults on iOS). */
public interface KeyValueStore {
    public fun getString(name: String): String?
    public fun putString(name: String, value: String)
}

/**
 * Files under one folder (on iOS: Documents/logs, visible in the Files app
 * and Finder). Paths are relative to that folder.
 */
public interface FileStore {
    public fun append(path: String, text: String)
    public fun write(path: String, bytes: ByteArray)
    public fun read(path: String): ByteArray?

    /** Names directly inside [dir], or empty. */
    public fun list(dir: String): List<String>
    public fun delete(path: String)
}
