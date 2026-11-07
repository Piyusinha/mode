package com.piyush.mode.app.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.piyush.mode.app.R
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeIconImage
import com.piyush.mode.design.ModeIconSize
import com.piyush.mode.design.ModeOptionList
import com.piyush.mode.design.ModeSheet
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextField
import com.piyush.mode.design.ModeTextStyle

/** What the runner expects for a yes/no question, "always", and "I've entered it". */
private object Answer {
    const val YES = "yes"
    const val NO = "no"
    const val ALWAYS = "always"
    const val DONE = "done"
}

/** Mode's question: yes/no before a risky tap, a choice to tap (or type), your turn (a PIN), or a free answer. */
@Composable
fun QuestionSheet(question: PendingQuestion, onAnswer: (String) -> Unit, onStop: () -> Unit) {
    val c = Mode.colors
    var answer by rememberSaveable(question.id) { mutableStateOf("") }
    ModeSheet(onDismiss = {}, dismissible = false) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(ModeSpacing.xl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
            val tone = if (question.yourTurn) c.warning else c.tint
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                ModeIconImage(if (question.yourTurn) ModeIcon.Lock else ModeIcon.Waiting, ModeIconSize.small, tone)
                ModeText(stringResource(if (question.yourTurn) R.string.question_your_turn else R.string.question_title), ModeTextStyle.Subheadline, color = tone)
            }
            ModeText(question.text, ModeTextStyle.Title2)
            val stop = @Composable { ModeButton(stringResource(R.string.question_stop_task), onStop, Modifier.align(Alignment.CenterHorizontally), role = ModeButtonRole.Destructive) }
            when {
                question.yourTurn -> {
                    ModeText(stringResource(R.string.question_your_turn_note), ModeTextStyle.Callout, color = c.textSecondary)
                    ModeButton(stringResource(R.string.question_entered), { onAnswer(Answer.DONE) }, fullWidth = true)
                    stop()
                }
                question.yesNo -> {
                    ModeButton(stringResource(R.string.question_yes), { onAnswer(Answer.YES) }, fullWidth = true)
                    if (question.allowAlways) ModeButton(stringResource(R.string.question_always), { onAnswer(Answer.ALWAYS) }, Modifier.fillMaxWidth(), role = ModeButtonRole.Secondary)
                    ModeButton(stringResource(R.string.question_no), { onAnswer(Answer.NO) }, Modifier.fillMaxWidth(), role = ModeButtonRole.Secondary)
                    if (question.allowAlways) ModeText(stringResource(R.string.question_always_note), ModeTextStyle.Footnote, color = c.textSecondary)
                }
                question.options.isNotEmpty() -> {
                    ModeOptionList(question.options, question.recommended, stringResource(R.string.question_recommended), onAnswer)
                    ModeTextField(stringResource(R.string.question_something_else), stringResource(R.string.question_something_else), answer, { answer = it })
                    if (answer.isNotEmpty()) ModeButton(stringResource(R.string.question_send), { onAnswer(answer) }, fullWidth = true)
                    stop()
                }
                else -> {
                    ModeTextField(stringResource(R.string.question_answer_prompt), stringResource(R.string.question_answer_prompt), answer, { answer = it })
                    ModeButton(stringResource(R.string.question_send), { onAnswer(answer) }, fullWidth = true, enabled = answer.isNotBlank())
                    stop()
                }
            }
        }
    }
}
