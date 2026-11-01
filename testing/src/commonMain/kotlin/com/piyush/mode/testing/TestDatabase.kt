package com.piyush.mode.testing

import com.piyush.mode.core.database.ModeDatabase

/** A fresh in-memory database with the full schema and foreign keys on. */
expect fun testDatabase(): ModeDatabase
