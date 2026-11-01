package com.piyush.mode.coredevice

/** What the phone's hardware keyboard can do. Mirrors `native/modecore/src/keys.rs`. */
object Keys {
    /** Keys `input.keys` accepts. `delete` is backspace, as on the iPhone keyboard. */
    val NAMED = setOf(
        "return", "enter", "escape", "esc", "backspace", "delete", "tab", "space",
        "left", "right", "up", "down",
    )

    fun isNamed(name: String): Boolean = name.lowercase() in NAMED

    /** True when every character has a key on a US keyboard; anything else is pasted. */
    fun typeable(text: String): Boolean = text.all { it == '\n' || it == '\t' || it in ' '..'~' }
}
