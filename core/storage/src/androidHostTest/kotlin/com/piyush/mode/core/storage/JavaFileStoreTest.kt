package com.piyush.mode.core.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JavaFileStoreTest {
    private val root: File = Files.createTempDirectory("mode-files").toFile()
    private val store = JavaFileStore(root)

    @Test
    fun appendsWritesReadsListsAndDeletes() {
        store.append("events.log", "one\n")
        store.append("events.log", "two\n")
        assertEquals("one\ntwo\n", store.read("events.log")!!.decodeToString())

        store.write("runs/r1/step-1.jpg", byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), store.read("runs/r1/step-1.jpg"))
        assertEquals(listOf("r1"), store.list("runs"))
        assertEquals(emptyList(), store.list("missing"))

        store.delete("runs/r1")
        assertNull(store.read("runs/r1/step-1.jpg"))
        store.delete("")
        assertEquals(emptyList(), store.list(""))
    }

    @Test
    fun writesReplaceWholeFiles() {
        store.write("a.txt", "long text".encodeToByteArray())
        store.write("a.txt", "short".encodeToByteArray())
        assertEquals("short", store.read("a.txt")!!.decodeToString())
        assertEquals(listOf("a.txt"), store.list(""))
    }

    @Test
    fun staysInsideItsFolder() {
        assertFailsWith<IllegalArgumentException> { store.read("../outside") }
    }
}
