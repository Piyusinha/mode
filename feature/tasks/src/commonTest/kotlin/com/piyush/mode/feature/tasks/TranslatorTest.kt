package com.piyush.mode.feature.tasks

import com.piyush.mode.feature.settings.domain.SpeechLanguage
import com.piyush.mode.feature.tasks.presentation.Translator
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslatorTest {
    private var calls = 0
    private val model = LlmClient { _, _, _ ->
        calls++
        ChatResult(ToolCall("english", buildJsonObject {
            put("english", JsonPrimitive("Turn on Dark Mode")); put("language", JsonPrimitive("Hindi"))
        }), "", Usage(100, 10, 0.0))
    }
    private val quick = { "gpt-6.1-terra@low" to ModelProvider.ChatGpt }

    @Test
    fun aRequestInHindiIsWorkedOnInEnglishAndAnsweredInHindi() = runTest {
        val r = Translator(model, quick).toEnglish("डार्क मोड चालू करो", SpeechLanguage.Auto)
        assertEquals("Turn on Dark Mode", r.english)
        assertEquals("Hindi", r.language)
    }

    @Test
    fun englishRequestsDontWaitForATranslation() = runTest {
        val r = Translator(model, quick).toEnglish("Turn on dark mode, haan", SpeechLanguage.EnglishIndia)
        assertEquals("Turn on dark mode, haan", r.english)
        assertNull(r.language)
        assertEquals(0, calls)
    }

    @Test
    fun aChosenLanguageIsAlwaysTranslatedAndWithoutAModelItStaysAsSaid() = runTest {
        val r = Translator(model, { null }).toEnglish("dark mode chalu karo", SpeechLanguage.Hindi)
        assertEquals("dark mode chalu karo", r.english)
        assertEquals("Hindi", r.language)
        assertTrue(Translator.looksForeign("ડાર્ક મોડ ચાલુ કરો"))
        assertFalse(Translator.looksForeign("Order my usual from Swiggy"))
    }
}
