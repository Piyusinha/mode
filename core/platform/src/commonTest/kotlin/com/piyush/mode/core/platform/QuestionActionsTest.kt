package com.piyush.mode.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestionActionsTest {
    private val words = QuestionWords("Yes", "No", "Always", "I've entered it", "Reply", "Something else")

    @Test
    fun yesNoAndAlwaysSendWhatTheRunnerExpects() {
        assertEquals(listOf("yes", "no"), QuestionActions.of(QuestionKind.YesNo, emptyList(), words).map { it.answer })
        assertEquals(listOf("yes", "always", "no"), QuestionActions.of(QuestionKind.YesNoAlways, emptyList(), words).map { it.answer })
        assertEquals(listOf("done"), QuestionActions.of(QuestionKind.YourTurn, emptyList(), words).map { it.answer })
    }

    @Test
    fun fewChoicesAreButtonsAndManyEndInATypedReply() {
        assertEquals(listOf("A", "B"), QuestionActions.of(QuestionKind.Choice, listOf("A", "B"), words).map { it.title })
        val many = QuestionActions.of(QuestionKind.Choice, listOf("A", "B", "C", "D"), words)
        assertEquals(3, many.size)
        assertTrue(many.last().typed)
        assertEquals(listOf("A", "B"), many.dropLast(1).map { it.answer })
    }

    @Test
    fun aFreeAnswerIsTyped() {
        assertTrue(QuestionActions.of(QuestionKind.Text, emptyList(), words).single().typed)
    }
}
