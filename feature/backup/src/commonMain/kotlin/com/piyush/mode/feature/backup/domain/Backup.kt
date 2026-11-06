package com.piyush.mode.feature.backup.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** How many of each kind a backup holds, or a restore added. */
@Serializable
public data class ItemCounts(
    val chats: Int = 0,
    val messages: Int = 0,
    val memories: Int = 0,
    val tasks: Int = 0,
    val repeats: Int = 0,
    /** The run index (history rows). Not shown on its own: it goes with tasks. */
    val runs: Int = 0,
)

/** How a locked payload was sealed; everything here may be public. */
@Serializable
public data class BackupLock(
    val kdf: String = KDF,
    val iterations: Int,
    /** Base64. */
    val salt: String,
    val cipher: String = CIPHER,
    /** Base64, 12 bytes. */
    val nonce: String,
    /** Base64: tells a wrong password from a damaged file before decrypting. */
    val check: String,
) {
    public companion object {
        public const val KDF: String = "PBKDF2-HMAC-SHA256"
        public const val CIPHER: String = "AES-256-GCM"
    }
}

/**
 * The part of a backup readable without its password, so a restore can show what's in it first.
 * Stored as one line of JSON after the magic line (see `BackupArchive`).
 */
@Serializable
public data class BackupHeader(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAtMs: Long,
    val deviceName: String,
    val appVersion: String,
    val counts: ItemCounts,
    val hasSignIns: Boolean,
    /** "ChatGPT", "OpenRouter", "ElevenLabs" … */
    val signInNames: List<String> = emptyList(),
    val locked: Boolean,
    /** Present when [locked]. */
    val lock: BackupLock? = null,
    /** Unlocked: SHA-256 (hex) of the payload. A locked payload is checked by its GCM tag instead. */
    val payloadSha256: String? = null,
    /** The file's size, filled in when it is read (not stored). */
    @Transient val fileBytes: Long = 0,
) {
    /** Made by a later Mode in a form this one can't read. */
    val newer: Boolean get() = version > VERSION

    public companion object {
        public const val FORMAT: String = "mode-backup"
        public const val VERSION: Int = 1
        public const val EXTENSION: String = "modebackup"
    }
}

/** A backup just written, ready for the share sheet. */
public data class BackupFile(val path: String, val name: String, val bytes: Long, val header: BackupHeader)

/** What a restore did: per kind, rows added and rows already on this iPhone (left as they were). */
public data class RestoreReport(
    val added: ItemCounts,
    val skipped: ItemCounts,
    /** Sign-ins put back, for providers this iPhone had none for. */
    val restoredSignIns: List<String>,
    /** Sign-ins in the backup left alone because this iPhone already has one. */
    val keptSignIns: List<String> = emptyList(),
    val settingsApplied: Int = 0,
    /** Repeats that were added: their next time and reminder need setting on this iPhone. */
    val addedRepeatIds: List<String> = emptyList(),
)

/** Why a backup couldn't be made or restored. */
public enum class BackupFailure {
    /** Not a Mode backup at all. */
    NotABackup,
    /** Made by a newer Mode: update to restore it. */
    NewerVersion,
    /** The password doesn't open this backup (or none was given for a locked one). */
    WrongPassword,
    /** A Mode backup, but damaged, changed, or its contents don't fit. Nothing was written. */
    Unreadable,
    /** The backup file could not be written. */
    CouldNotSave,
}

public class BackupException(public val failure: BackupFailure, cause: Throwable? = null) :
    Exception("Backup: $failure", cause)
