package com.piyush.mode.core.database

/** Opens the app's database (on disk on a phone, in memory in tests). */
public fun interface DatabaseFactory {
    public fun create(): ModeDatabase
}
