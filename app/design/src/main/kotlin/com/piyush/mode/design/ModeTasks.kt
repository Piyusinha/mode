package com.piyush.mode.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A pill switch between a few views (Scheduled / Done): the picked one on a white lifted pill. */
@Composable
public fun <T> ModePillSegments(
    options: List<Pair<T, String>>,
    selection: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().height(if (compact) ModeSize.segmentsCompactHeight else ModeSize.segmentsHeight)
            .background(c.cardRaised, Capsule).padding(ModeSize.segmentsInset),
        horizontalArrangement = Arrangement.spacedBy(if (compact) ModeSpacing.xxs else ModeSize.segmentsInset),
    ) {
        options.forEach { (option, title) ->
            val picked = option == selection
            val fill by animateColorAsState(if (picked) c.card else c.card.copy(alpha = 0f), label = "segment")
            Box(
                Modifier.weight(1f).fillMaxHeight().then(if (picked) Modifier.modeShadow(Capsule) else Modifier).background(fill, Capsule)
                    .clickable(role = Role.Tab, indication = null, interactionSource = null) { onSelect(option) }
                    .semantics { selected = picked },
                Alignment.Center,
            ) {
                ModeText(
                    title, if (picked) ModeTextStyle.SubheadlineEmphasis else ModeTextStyle.Subheadline,
                    color = if (picked || compact) c.textPrimary else c.textSecondary, maxLines = 1,
                )
            }
        }
    }
}

/** One choice on an action sheet. */
public data class ModeSheetAction(val title: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * A short list of choices over the page (Pause or Delete a repeat), with Cancel under it: a white card
 * near the bottom, the way the canvas draws Android's action sheets.
 */
@Composable
public fun ModeActionSheet(title: String, actions: List<ModeSheetAction>, cancel: String, onDismiss: () -> Unit) {
    val c = Mode.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = null, onClick = onDismiss), Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(ModeSpacing.screenEdge)
                    .clickable(indication = null, interactionSource = null) {},
                verticalArrangement = Arrangement.spacedBy(ModeSpacing.s),
            ) {
                val shape = RoundedCornerShape(ModeRadius.card)
                Column(Modifier.fillMaxWidth().background(c.card, shape).padding(horizontal = ModeSpacing.l)) {
                    ModeText(
                        title, ModeTextStyle.Footnote, Modifier.fillMaxWidth().padding(vertical = ModeSpacing.m),
                        color = c.textSecondary, align = TextAlign.Center,
                    )
                    actions.forEach { action ->
                        ModeHairline()
                        SheetRow(action.title, if (action.destructive) c.danger else c.textPrimary) {
                            onDismiss()
                            action.onClick()
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().background(c.card, shape)) {
                    SheetRow(cancel, c.textPrimary, emphasis = true, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SheetRow(title: String, color: androidx.compose.ui.graphics.Color, emphasis: Boolean = false, onClick: () -> Unit) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.rowMin)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick),
        Alignment.Center,
    ) { ModeText(title, if (emphasis) ModeTextStyle.Headline else ModeTextStyle.Body, color = color.copy(alpha = color.alpha * alpha)) }
}

/** A short text to edit over the page (a lesson in a chat): a title, the field, and Save. */
@Composable
public fun ModeTextSheet(title: String, initial: String, prompt: String, save: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Mode.colors
    var text by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = null, onClick = onDismiss), Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().background(c.page, RoundedCornerShape(topStart = ModeRadius.sheet, topEnd = ModeRadius.sheet))
                    .clickable(indication = null, interactionSource = null) {}
                    .navigationBarsPadding().imePadding().padding(ModeSpacing.screenEdge),
                verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
            ) {
                ModeText(title, ModeTextStyle.Title2)
                Box(Modifier.fillMaxWidth().background(c.field, RoundedCornerShape(ModeRadius.field)).padding(ModeSpacing.l)) {
                    if (text.isEmpty()) ModeText(prompt, ModeTextStyle.Body, color = c.textSecondary)
                    androidx.compose.foundation.text.BasicTextField(
                        text, { text = it }, Modifier.fillMaxWidth(), textStyle = ModeTextStyle.Body.style(c.textPrimary),
                        minLines = SHEET_MIN_LINES, maxLines = SHEET_MAX_LINES, cursorBrush = androidx.compose.ui.graphics.SolidColor(c.tint),
                    )
                }
                ModeButton(save, { onSave(text); onDismiss() }, fullWidth = true, enabled = text.isNotBlank())
            }
        }
    }
}

private const val SHEET_MIN_LINES = 3
private const val SHEET_MAX_LINES = 8
