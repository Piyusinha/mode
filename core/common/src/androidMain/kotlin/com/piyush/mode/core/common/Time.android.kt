package com.piyush.mode.core.common

import java.time.LocalDate
import java.time.ZoneId

public actual fun startOfTodayMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
