package com.piyush.mode.adb

/**
 * Reads Wireless debugging's "Pair with device" window from the text on screen: its six-digit code and the port
 * after the address ("192.168.1.24:37125", "[fe80::1]:37125"). Only digits and an address, so it works in every
 * language.
 */
public object PairingWindow {
    /**
     * [port] is the first address's port (the pairing window's); [ports] every port on screen, in order. The
     * Wireless debugging page shows the connect port ("IP address & Port") under the pairing window, so a
     * port seen here that isn't the pairing one is where Mode connects after.
     */
    public data class Seen(val code: String?, val port: Int?, val ports: List<Int> = listOfNotNull(port))

    private val CODE = Regex("""^\d{6}$""")
    private val ADDRESS = Regex("""^(?:\d{1,3}(?:\.\d{1,3}){3}|\[[0-9a-fA-F:.%\w]+]):(\d{2,5})$""")

    public fun read(texts: List<String>): Seen {
        val clean = texts.map { it.trim() }
        val code = clean.firstOrNull { CODE.matches(it) }
        val ports = clean.mapNotNull { ADDRESS.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.filter { it in 1..65535 }.distinct()
        return Seen(code, ports.firstOrNull(), ports)
    }

    /** What the user typed in the notification: the six digits, whatever spaces or dashes came with them. */
    public fun code(typed: String): String? = typed.filter { it.isDigit() }.takeIf { it.length == CODE_LENGTH }

    private const val CODE_LENGTH = 6
}
