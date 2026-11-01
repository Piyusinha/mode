package com.piyush.mode.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class PlanItemTest {
    @Test
    fun numberedItemsWithOrWithoutDoneMarks() {
        assertEquals(
            listOf(PlanItem("Turn off dark mode", true), PlanItem("Set brightness to half", false), PlanItem("Open YouTube", false)),
            PlanItem.parse("1. [done] turn off dark mode\n2) Set brightness to half.\n\n- [ ] Open YouTube"),
        )
    }
}
