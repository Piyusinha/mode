package com.piyush.mode.screen

import com.piyush.mode.ops.OpFailed

/** Turns what the agent calls an app into the system's id (a bundle id, a package name). */
object AppNames {
    /** `com.ubercab.UberClient`-like: dotted, no spaces. */
    fun looksLikeBundleId(name: String): Boolean = '.' in name && ' ' !in name.trim()

    /**
     * The installed app called [name]: an exact name match (ignoring case),
     * else the only app whose name starts with it, else the only one containing it.
     */
    fun resolve(name: String, apps: List<InstalledApp>): InstalledApp {
        val wanted = name.trim().lowercase()
        if (wanted.isEmpty()) throw OpFailed("no app name given")
        val exact = apps.filter { it.name.lowercase() == wanted }
        if (exact.isNotEmpty()) return exact.first()
        for (match in listOf<(String) -> Boolean>({ it.startsWith(wanted) }, { wanted in it })) {
            val hits = apps.filter { match(it.name.lowercase()) }
            when (hits.size) {
                0 -> continue
                1 -> return hits.single()
                else -> throw OpFailed(
                    "'$name' matches several apps: ${hits.take(6).joinToString { it.name }}; name one exactly",
                )
            }
        }
        throw OpFailed("no installed app is called '$name'")
    }
}
