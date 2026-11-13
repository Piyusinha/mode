package com.piyush.mode.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A sheet over the page: it slides up from the bottom with a handle, the page dims behind it. [tall]
 * takes most of the screen (Ask); otherwise it fits its content. [dismissible] false keeps it until
 * an answer (Mode's question).
 */
@Composable
public fun ModeSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, tall: Boolean = false, dismissible: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val c = Mode.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().clickable(indication = null, interactionSource = null) { if (dismissible) onDismiss() }.statusBarsPadding(),
            Alignment.BottomCenter,
        ) {
            AnimatedVisibility(shown, enter = slideInVertically(tween(ModeMotion.standardMs)) { it }, exit = slideOutVertically { it }) {
                val shape = RoundedCornerShape(topStart = ModeRadius.sheet, topEnd = ModeRadius.sheet)
                Column(
                    modifier.fillMaxWidth().then(if (tall) Modifier.fillMaxHeight(TALL) else Modifier)
                        .background(c.page, shape).clickable(indication = null, interactionSource = null) {}
                        .navigationBarsPadding().imePadding(),
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = ModeSpacing.s), Alignment.Center) {
                        Box(Modifier.size(ModeSize.sheetHandle, ModeSize.sheetHandleHeight).background(c.ring, Capsule))
                    }
                    content()
                }
            }
        }
    }
}

/** A labelled text field in a white rounded box ([secure] hides what's typed). */
@Composable
public fun ModeTextField(label: String, prompt: String, text: String, onText: (String) -> Unit, modifier: Modifier = Modifier, secure: Boolean = false) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.field)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
        ModeText(label, ModeTextStyle.Footnote, Modifier.padding(start = ModeSpacing.xs), color = c.textSecondary)
        Box(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.fieldHeight).modeShadow(shape).background(c.field, shape).padding(horizontal = ModeSpacing.l),
            Alignment.CenterStart,
        ) {
            if (text.isEmpty()) ModeText(prompt, ModeTextStyle.Callout, color = c.textSecondary)
            BasicTextField(
                text, onText, Modifier.fillMaxWidth().semantics { contentDescription = label },
                textStyle = ModeTextStyle.Callout.style(c.textPrimary), singleLine = true, cursorBrush = SolidColor(c.tint),
                visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (secure) KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default,
            )
        }
    }
}

/** A white chip with an icon, a line and an optional action ("Continue"). */
@Composable
public fun ModeChip(text: String, modifier: Modifier = Modifier, icon: ModeIcon? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Mode.colors
    Row(
        modifier.defaultMinSize(minHeight = ModeSize.touch).background(c.card, Capsule).padding(horizontal = ModeSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) ModeIconImage(icon, ModeIconSize.small, c.tint)
        ModeText(text, ModeTextStyle.Footnote, Modifier.weight(1f, fill = false), maxLines = 2)
        if (action != null && onAction != null) {
            Box(
                Modifier.defaultMinSize(minHeight = ModeSize.touch).clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onAction),
                Alignment.Center,
            ) { ModeText(action, ModeTextStyle.CaptionEmphasis, color = c.tint) }
        }
    }
}

/** The choices of a question: one card each, the recommended one ringed and tagged. */
@Composable
public fun ModeOptionList(options: List<String>, recommended: String?, recommendedLabel: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        options.forEach { option ->
            val picked = option == recommended
            val shape = RoundedCornerShape(ModeRadius.chip)
            val (press, alpha) = rememberPress()
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.rowMin).alpha(alpha).background(c.card, shape)
                    .then(if (picked) Modifier.border(ModeSize.separator * 2, c.tint, shape) else Modifier)
                    .clickable(press, indication = null, role = Role.Button) { onPick(option) }.padding(horizontal = ModeSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeText(option, ModeTextStyle.Body, Modifier.weight(1f))
                if (picked) {
                    ModeText(
                        recommendedLabel, ModeTextStyle.CaptionEmphasis,
                        Modifier.background(c.tokens.tagGreen, Capsule).padding(horizontal = ModeSpacing.s, vertical = ModeSpacing.xxs),
                        color = c.tokens.tagGreenText,
                    )
                }
                ModeChevron()
            }
        }
    }
}

private const val TALL = 0.92f
