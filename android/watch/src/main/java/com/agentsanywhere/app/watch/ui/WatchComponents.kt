package com.agentsanywhere.app.watch.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.ui.designsystem.LocalAAColors

/**
 * Compact building blocks for a 466px round panel.
 *
 * The usable canvas is only about 165dp wide, so Material's default text field
 * (56dp tall, large horizontal padding) and button metrics do not fit; these are
 * deliberately smaller while staying above 36dp for touch.
 */

@Composable
fun WatchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    onImeAction: (() -> Unit)? = null,
    height: Dp? = null,
    textSize: TextUnit = 13.sp,
    verticalPadding: Dp = 9.dp,
) {
    val colors = LocalAAColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = colors.ink, fontSize = textSize),
        cursorBrush = SolidColor(colors.sessionStatusAccent),
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
            onNext = { onImeAction?.invoke() },
            onSend = { onImeAction?.invoke() },
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                // The confirm key of the watch keyboard arrives as a raw Enter key event on some
                // IMEs instead of as an editor action, which is why "done" used to do nothing
                // and the message had to be sent with the button beside the field.
                val action = onImeAction
                if (action != null && event.type == KeyEventType.KeyUp && event.isConfirmKey()) {
                    action()
                    true
                } else {
                    false
                }
            },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Only safe when the height above made the constraint bounded; without
                    // it the field sits in a scrolling column and must wrap its content.
                    .then(if (height != null) Modifier.fillMaxHeight() else Modifier)
                    .clip(shape)
                    .background(if (enabled) colors.raisedSurface else colors.matchedAccountSurface)
                    .border(
                        width = 1.dp,
                        color = if (focused) colors.sessionStatusAccent else colors.subtle,
                        shape = shape,
                    )
                    .padding(horizontal = 10.dp, vertical = verticalPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.faint,
                        fontSize = textSize,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * Default height for a full-width action: a pill, not a slab.
 *
 * Everything on this screen is 165dp wide at most, so a 38dp bar dominated whatever it sat
 * under. Smaller and rounder keeps the same tap target while letting the content stay the
 * loudest thing on the panel.
 */
private val WatchActionHeight = 32.dp

@Composable
fun WatchPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = WatchActionHeight,
    cornerRadius: Dp = height / 2,
    icon: ImageVector? = null,
) {
    val interactive = enabled && !loading
    WatchButtonSurface(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = interactive,
        loading = loading,
        height = height,
        cornerRadius = cornerRadius,
        background = LocalAAColors.current.primaryAction,
        contentColor = LocalAAColors.current.onPrimaryAction,
        icon = icon,
    )
}

@Composable
fun WatchSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = WatchActionHeight,
    cornerRadius: Dp = height / 2,
    icon: ImageVector? = null,
) {
    val interactive = enabled && !loading
    WatchButtonSurface(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = interactive,
        loading = loading,
        height = height,
        cornerRadius = cornerRadius,
        background = LocalAAColors.current.secondaryActionSurface,
        contentColor = LocalAAColors.current.ink,
        icon = icon,
    )
}

/**
 * Compact fully-rounded button for secondary actions that sit side by side.
 *
 * The ellipse ends are what make two of them read as a single row on a round panel, so the
 * caller is expected to size the row with [WatchRound.maxRowWidth] rather than letting it
 * span the full content width.
 */
@Composable
fun WatchPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalAAColors.current
    Box(
        modifier = modifier
            .height(WatchLoginLayout.PillRowHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) colors.secondaryActionSurface else colors.subtle)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) colors.ink else colors.faint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Enter, in the shapes a watch keyboard's confirm key arrives in.
 *
 * The centre button is deliberately not included: on this watch it doubles as the crown press,
 * and sending a message on a press the user meant as navigation would be worse than making them
 * tap the send button.
 */
private fun KeyEvent.isConfirmKey(): Boolean =
    key == Key.Enter || key == Key.NumPadEnter

/**
 * The two roles the composer button takes: send what was typed, or stop the running turn. Both
 * are drawn as symbols rather than words - at 12sp a Chinese label needed a third of the row,
 * which is width the input field can use, and an arrow and a stop square are read faster anyway.
 */
enum class WatchComposerGlyph { Send, Stop }

/**
 * The one action that lives beside the transcript.
 *
 * The two roles share a shape because they share the place on screen.
 */
@Composable
fun WatchComposerButton(
    glyph: WatchComposerGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = LocalAAColors.current
    val interactive = enabled && !loading
    val tint = if (interactive) colors.onPrimaryAction else colors.faint
    Box(
        modifier = modifier
            .height(WatchConversationLayout.ComposerHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (interactive) colors.primaryAction else colors.subtle)
            .clickable(enabled = interactive, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = colors.muted,
                strokeWidth = 2.dp,
            )
        } else {
            when (glyph) {
                WatchComposerGlyph.Send -> Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )

                WatchComposerGlyph.Stop -> Canvas(modifier = Modifier.size(12.dp)) {
                    // The universal stop mark: a filled square. Nothing in the core icon set is
                    // both a square and not a rounded "checkbox" glyph.
                    drawRoundRect(
                        color = tint,
                        cornerRadius = CornerRadius(size.minDimension * 0.16f, size.minDimension * 0.16f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchButtonSurface(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    height: Dp,
    cornerRadius: Dp,
    background: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    icon: ImageVector? = null,
) {
    val colors = LocalAAColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(if (enabled) background else colors.subtle)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = colors.muted,
                strokeWidth = 2.dp,
            )
        } else {
            val tint = if (enabled) contentColor else colors.faint
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    text = text,
                    color = tint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun WatchErrorBanner(message: String, modifier: Modifier = Modifier) {
    val colors = LocalAAColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.errorSurface)
            .border(1.dp, colors.errorBorder, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // A red exclamation, because "something failed" is the one state on this watch that
            // has to survive being glanced at.
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = colors.errorIcon,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = message,
                color = colors.errorText,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
fun WatchNoticeText(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier.fillMaxWidth(),
        color = LocalAAColors.current.noticeSuccess,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        textAlign = TextAlign.Center,
    )
}

/** A single line of secondary text, e.g. the current model or workspace. */
@Composable
fun WatchCaption(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        modifier = modifier,
        color = LocalAAColors.current.muted,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = maxLines,
        textAlign = textAlign,
    )
}

@Composable
fun WatchPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAAColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = colors.ink,
            fontSize = 15.sp,
            // Pinned so the header has an exact height; WatchLoginLayout depends on it.
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        if (!subtitle.isNullOrBlank()) {
            WatchCaption(text = subtitle, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/**
 * One line the server wants the user to see: a quota warning, a lost connector, a failed turn.
 *
 * Deliberately a strip rather than a card - it sits above the transcript and must not push the
 * conversation down - and severity only changes the colour, because on this screen a red line is
 * read before anything else on the page.
 */
@Composable
fun WatchStatusNotice(
    message: String,
    severity: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAAColors.current
    val error = severity.equals("error", ignoreCase = true) || severity.equals("critical", ignoreCase = true)
    val warning = severity.equals("warning", ignoreCase = true) || severity.equals("warn", ignoreCase = true)
    val tint = when {
        error -> colors.errorIcon
        warning -> colors.noticeWarning
        else -> colors.muted
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.raisedSurface)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(
            text = message,
            color = if (error) colors.errorText else colors.inkSoft,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            maxLines = 2,
        )
    }
}

/** Tappable pill used for model choices and other single-select lists. */
@Composable
fun WatchChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalAAColors.current
    // A stadium, not a rounded rectangle: on a round panel the eye reads a flat-ended row as
    // another block of content, and every other control on this screen is already a pill.
    val shape = RoundedCornerShape(percent = 50)
    val interaction = remember { MutableInteractionSource() }
    val background = when {
        selected && enabled -> colors.primaryAction
        selected -> colors.subtle
        enabled -> colors.secondaryActionSurface
        else -> colors.canvas
    }
    val contentColor = when {
        selected && enabled -> colors.onPrimaryAction
        enabled -> colors.ink
        else -> colors.faint
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                color = contentColor,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 2,
            )
            if (selected) {
                Text(
                    text = "✓",
                    color = contentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Page indicator, drawn once by the shell so it stays put while pages slide.
 *
 * The size and colour are animated: a page switch then reads as a transition rather than the
 * dot blinking from one position to the next.
 */
@Composable
fun WatchPageDots(
    current: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAAColors.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val size by animateDpAsState(
                targetValue = if (active) 6.dp else 4.dp,
                label = "watchPageDotSize",
            )
            val color by animateColorAsState(
                targetValue = if (active) colors.sessionStatusAccent else colors.faint,
                label = "watchPageDotColor",
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * Standard page body: header, then scrolling content inside the round-panel safe inset.
 *
 * The page indicator is deliberately not part of a page any more - the shell draws it in a
 * strip reserved above the pager, so sliding between pages cannot move it or leave it
 * half-rendered.
 */
@Composable
fun WatchPageScaffold(
    title: String,
    subtitle: String? = null,
    scrollable: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAAColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(horizontal = WatchSafeHorizontal, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        WatchPageHeader(title = title, subtitle = subtitle)
        content()
    }
}

/**
 * Label and value pair used by the account page.
 *
 * [compact] is the secondary form: the actions page uses it for the runtime and status
 * lines so they read as background information next to the buttons, which are the
 * primary content of that page.
 */
@Composable
fun WatchInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    online: Boolean? = null,
    dense: Boolean = false,
) {
    val colors = LocalAAColors.current
    val shape = RoundedCornerShape(10.dp)
    val labelSize = when {
        dense -> 7.sp
        compact -> 8.sp
        else -> 10.sp
    }
    val valueSize = when {
        dense -> 9.sp
        compact -> 10.sp
        else -> 12.sp
    }
    val vertical = when {
        dense -> 3.dp
        compact -> 4.dp
        else -> 7.dp
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.raisedSurface)
            .padding(horizontal = 10.dp, vertical = vertical),
        verticalArrangement = Arrangement.spacedBy(if (compact || dense) 1.dp else 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (online != null) {
                // Green is the one colour on this page that carries a fact the words cannot:
                // whether the runtime behind the session can actually be reached right now.
                Box(
                    modifier = Modifier
                        .size(if (dense) 4.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (online) colors.noticeSuccess else colors.faint),
                )
            }
            Text(
                text = label,
                color = colors.faint,
                fontSize = labelSize,
                maxLines = 1,
            )
        }
        Text(
            text = value,
            color = if (compact || dense) colors.inkSoft else colors.ink,
            fontSize = valueSize,
            fontWeight = if (compact || dense) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
        )
    }
}
