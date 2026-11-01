package com.piyush.mode.core.common

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

public actual fun startOfTodayMillis(): Long =
    (NSCalendar.currentCalendar.startOfDayForDate(NSDate()).timeIntervalSince1970 * 1000).toLong()
