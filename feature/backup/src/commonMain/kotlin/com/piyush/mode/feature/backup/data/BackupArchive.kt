package com.piyush.mode.feature.backup.data

import com.piyush.mode.feature.backup.domain.BackupHeader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * The file, byte for byte:
 *
 * ```
 * MODE-BACKUP\n                 the magic line: anything else is not a Mode backup
 * {"format":"mode-backup",…}\n  the header, one line of UTF-8 JSON, readable without the password
 * <payload>                     the rows and settings as UTF-8 JSON; when locked, AES-256-GCM of that JSON
 *                               (ciphertext and 16-byte tag), with the header line's bytes as associated data
 * ```
 *
 * An unlocked payload is checked against the header's SHA-256; a locked one by its GCM tag, which also covers
 * the header.
 */
internal class BackupArchive(val header: BackupHeader, val headerBytes: ByteArray, val payload: ByteArray) {
    fun bytes(): ByteArray = MAGIC + headerBytes + NEWLINE + payload

    companion object {
        val MAGIC: ByteArray = "MODE-BACKUP\n".encodeToByteArray()
        private val NEWLINE = byteArrayOf('\n'.code.toByte())
        /** A header is a few hundred bytes; past this it isn't one. */
        private const val MAX_HEADER = 64 * 1024

        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun headerBytes(header: BackupHeader): ByteArray = json.encodeToString(BackupHeader.serializer(), header).encodeToByteArray()

        /**
         * The header and payload of [bytes], or null when they aren't a Mode backup. A header from a newer
         * version still reads (its [BackupHeader.newer] says so) as long as it has the format and version.
         */
        fun read(bytes: ByteArray): BackupArchive? {
            if (bytes.size < MAGIC.size || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
            val start = MAGIC.size
            val end = (start until minOf(bytes.size, start + MAX_HEADER)).firstOrNull { bytes[it] == NEWLINE[0] } ?: return null
            val headerBytes = bytes.copyOfRange(start, end)
            val obj = runCatching { json.parseToJsonElement(headerBytes.decodeToString()).jsonObject }.getOrNull() ?: return null
            if ((obj["format"] as? JsonPrimitive)?.content != BackupHeader.FORMAT) return null
            val version = (obj["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
            val header = runCatching { json.decodeFromJsonElement(BackupHeader.serializer(), obj) }.getOrNull()
                ?: if (version > BackupHeader.VERSION) newerStub(obj, version) else return null
            return BackupArchive(header.copy(fileBytes = bytes.size.toLong()), headerBytes, bytes.copyOfRange(end + 1, bytes.size))
        }

        /** A later version's header this one can't fully read: enough to say so. */
        private fun newerStub(obj: JsonObject, version: Int) = BackupHeader(
            version = version,
            createdAtMs = (obj["createdAtMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0,
            deviceName = (obj["deviceName"] as? JsonPrimitive)?.content.orEmpty(),
            appVersion = (obj["appVersion"] as? JsonPrimitive)?.content.orEmpty(),
            counts = com.piyush.mode.feature.backup.domain.ItemCounts(),
            hasSignIns = false,
            locked = false,
        )
    }
}
