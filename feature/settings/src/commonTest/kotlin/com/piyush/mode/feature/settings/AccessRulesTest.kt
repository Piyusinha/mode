package com.piyush.mode.feature.settings

import com.piyush.mode.feature.settings.domain.AccessRules
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessRulesTest {
    private val words = listOf("just do it", "full access")

    @Test
    fun aWordAtTheStartOrEndGivesThatTaskFullAccess() {
        assertEquals(AccessRules.ForTask("Order my usual from Swiggy", true), AccessRules.forTask("just do it, order my usual from Swiggy", words))
        assertEquals(AccessRules.ForTask("Order my usual from Swiggy", true), AccessRules.forTask("Order my usual from Swiggy, just do it.", words))
        assertEquals(AccessRules.ForTask("Book a cab home", true), AccessRules.forTask("Full access: book a cab home", words))
    }

    @Test
    fun aWordInsideTheTaskOrAloneDoesNot() {
        assertEquals(false, AccessRules.forTask("tell Sonali to just do it tomorrow", words).fullAccess)
        assertEquals(false, AccessRules.forTask("just do it", words).fullAccess)
        assertEquals(AccessRules.ForTask("auto to Bellandur", false), AccessRules.forTask("auto to Bellandur", words))
        // The user's own word, even one that is a ride elsewhere, works when they chose it.
        assertEquals(true, AccessRules.forTask("auto order water", listOf("auto")).fullAccess)
    }
}
