package com.agentsanywhere.app.watch.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.feature.sessiondetail.MessageAuthor
import com.agentsanywhere.app.feature.sessiondetail.SessionDetailState
import com.agentsanywhere.app.feature.sessiondetail.SessionRuntimeStatus
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessage
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessageKind
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.ui.WatchCaption
import com.agentsanywhere.app.watch.ui.WatchComposerButton
import com.agentsanywhere.app.watch.ui.WatchComposerGlyph
import com.agentsanywhere.app.watch.ui.WatchConversationLayout
import com.agentsanywhere.app.watch.ui.WatchEmptyGlyph
import com.agentsanywhere.app.watch.ui.WatchEmptyState
import com.agentsanywhere.app.watch.ui.WatchEntryIcon
import com.agentsanywhere.app.watch.ui.WatchErrorBanner
import com.agentsanywhere.app.watch.ui.WatchOnlineDot
import com.agentsanywhere.app.watch.ui.WatchSafeHorizontal
import com.agentsanywhere.app.watch.ui.WatchStatusNotice
import com.agentsanywhere.app.watch.ui.WatchTextField
import com.agentsanywhere.app.watch.ui.WatchThinkingIndicator
import com.agentsanywhere.app.watch.ui.entryGlyph

/**
 * Page 1 — the conversation, and the page the app lands on.
 *
 * The transcript is deliberately frame-less: no bubbles, no card backgrounds. A round
 * panel has very little room, so hierarchy comes from type size and colour instead —
 * agent replies are the only body text at full size, the user's own turns carry an accent
 * rule, and everything the agent did on the side (reasoning, commands, tool calls, file
 * changes, diagnostics) collapses to a single small grey line that can be tapped open.
 *
 * The composer is one row pinned under the transcript, so the keyboard never hides the
 * input and the round bezel never clips the row (its width is derived from its position,
 * see [WatchConversationLayout]).
 */
@Composable
fun WatchConversationPage(
    detail: SessionDetailState,
    loading: Boolean,
    composerText: String,
    busy: Boolean,
    working: Boolean,
    error: String?,
    notice: String?,
    listState: LazyListState,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalAAColors.current
    val session = detail.session
    val messages = detail.messages
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    // How far past the last entry to aim. `scrollToItem` aligns an item's *top* with the top of
    // the viewport, so landing on the newest entry would still show the beginning of a long
    // answer; overshooting by a couple of panels and letting the list clamp to its own end lands
    // on the true bottom instead.
    val tailOffset = with(LocalDensity.current) { WatchTranscriptTailDp.roundToPx() }
    val sessionId = detail.session?.id

    // Which conversation this transcript is currently parked at the bottom of.
    //
    // Picking another conversation in the workspace does not rebuild this page - the pager keeps
    // it composed and the same instance simply loads a different session - so "land on the newest
    // entry" cannot be a once-per-composition effect. It is tracked per session id here, which
    // also covers swiping back to this page after it was disposed.
    var bottomSession by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId, messages.size) {
        if (sessionId == null || messages.isEmpty()) return@LaunchedEffect
        val sessionChanged = bottomSession != sessionId
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        // Following new entries is only allowed while the user is already at the bottom;
        // scrolling back to read something must not be undone by the next message.
        val nearBottom = lastVisible < 0 || lastVisible >= messages.lastIndex - 1
        if (sessionChanged || nearBottom) {
            listState.scrollToItem(messages.lastIndex, tailOffset)
            bottomSession = sessionId
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            // The bottom padding is what pins the composer at WatchConversationLayout.ComposerTop,
            // which is the position its width was derived for. Leaving it off lets the weighted
            // transcript push the composer to the content edge instead, where the circle is far
            // narrower than the row - that is how the send button ended up under the bezel.
            .padding(
                start = WatchSafeHorizontal,
                end = WatchSafeHorizontal,
                top = WatchConversationLayout.TopPadding,
                bottom = WatchConversationLayout.BottomPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WatchConversationLayout.Gap),
    ) {
        // One line for the conversation and its state. A watch does not have the height to spend
        // a whole row on "Idle", and the runtime id that used to sit beside it said nothing the
        // user could act on.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (session != null && detail.runtime.status == SessionRuntimeStatus.Idle) {
                WatchOnlineDot()
            }
            Text(
                text = session?.title?.ifBlank { "未命名对话" } ?: "对话",
                modifier = Modifier.weight(1f),
                color = colors.ink,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (working) {
                WatchThinkingIndicator()
            } else {
                // The right-hand label is the only place a failure can be stated without
                // spending a row: a turn that failed, a connector that went away, or a live
                // socket that is retrying are all "why is nothing happening" answers.
                val link = detail.realtime
                val retrying = link.recovering || link.reconnectAttempt > 0
                Text(
                    text = when {
                        session == null -> "未选择"
                        loading -> "加载中"
                        detail.runtime.status == SessionRuntimeStatus.Error -> "运行失败"
                        detail.runtime.status == SessionRuntimeStatus.Blocked -> "已阻塞"
                        detail.runtime.status == SessionRuntimeStatus.Disconnected -> "设备离线"
                        retrying -> "重连中…"
                        link.connected -> "实时"
                        else -> session.statusLabel
                    },
                    color = when {
                        session == null || loading -> colors.muted
                        detail.runtime.status == SessionRuntimeStatus.Error -> colors.errorIcon
                        detail.runtime.status == SessionRuntimeStatus.Blocked -> colors.errorIcon
                        detail.runtime.status == SessionRuntimeStatus.Disconnected -> colors.noticeWarning
                        retrying -> colors.noticeWarning
                        link.connected -> colors.noticeSuccess
                        else -> colors.muted
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        error?.let { message -> WatchErrorBanner(message) }
        notice?.let { message -> WatchCaption(text = message, textAlign = TextAlign.Center, maxLines = 2) }

        // Whatever the runtime itself has flagged and not withdrawn: quota warnings, a lost
        // connector, a failed turn. Notices of type "notification" are the one place the server
        // states a problem in words instead of as a status code, so they are shown above the
        // transcript rather than buried in the actions page.
        detail.notices.notices
            .filter { it.openNotification }
            .take(2)
            .forEach { notification ->
                WatchStatusNotice(
                    message = notification.message?.takeIf(String::isNotBlank) ?: notification.title,
                    severity = notification.severity,
                )
            }

        if (session == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                WatchEmptyState(
                    glyph = WatchEmptyGlyph.Conversation,
                    text = "",
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (loading) {
                            // The seconds between picking a conversation and its transcript
                            // arriving used to be a blank circle; now it is visibly busy.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = colors.muted,
                                    strokeWidth = 2.dp,
                                )
                                WatchCaption(text = "正在载入对话…", textAlign = TextAlign.Center, maxLines = 1)
                            }
                        } else {
                            WatchCaption(
                                text = "还没有消息。在下面输入第一条指令。",
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(messages) { message ->
                            TimelineRow(
                                message = message,
                                expanded = message.id in expandedIds,
                                onToggle = {
                                    expandedIds = if (message.id in expandedIds) {
                                        expandedIds - message.id
                                    } else {
                                        expandedIds + message.id
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (working) {
                // Mid-turn the composer *is* the stop button. A text field here would let a
                // message be typed into a turn that is already running, which is the case the
                // old "待发送" strip existed to patch over; one control, one meaning. It is a
                // circle rather than a full-width bar: the symbol says it, and a wide slab for a
                // one-tap action is the thing this page has the least room for.
                WatchComposerButton(
                    glyph = WatchComposerGlyph.Stop,
                    onClick = onStop,
                    enabled = !busy,
                    loading = busy,
                    modifier = Modifier.width(WatchConversationLayout.ComposerHeight),
                )
            } else {
                Row(
                    modifier = Modifier
                        .width(WatchConversationLayout.ComposerWidth)
                        .height(WatchConversationLayout.ComposerHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WatchConversationLayout.ComposerGap),
                ) {
                    WatchTextField(
                        value = composerText,
                        onValueChange = onComposerChange,
                        placeholder = "输入消息…",
                        // Deliberately always editable. The runtime status is polled every few
                        // seconds, and disabling a field the watch IME is actively editing made
                        // the keyboard drop characters and stop syncing backspace.
                        enabled = true,
                        imeAction = ImeAction.Send,
                        // The keyboard's own confirm key sends, so the message does not have to
                        // be finished with the button beside the field.
                        onImeAction = onSend,
                        height = WatchConversationLayout.ComposerHeight,
                        // The row is 34dp tall, so the default 13sp text on 9dp of padding was
                        // taller than the field it sat in and the line was cut off.
                        textSize = 11.sp,
                        verticalPadding = 4.dp,
                        modifier = Modifier.weight(1f),
                    )
                    WatchComposerButton(
                        glyph = WatchComposerGlyph.Send,
                        onClick = onSend,
                        enabled = composerText.isNotBlank() && !busy,
                        loading = busy,
                        modifier = Modifier.width(WatchConversationLayout.SendButtonWidth),
                    )
                }
            }
        }
    }
}

/**
 * One transcript entry, frame-less.
 *
 * [COLLAPSED_KINDS] are the side-channel entries: they render as a single small grey line
 * that expands on tap, which keeps the actual conversation readable on a 165dp-wide panel.
 */
@Composable
private fun TimelineRow(
    message: TimelineMessage,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalAAColors.current
    val isUser = message.author == MessageAuthor.User
    val isError = message.kind == TimelineMessageKind.Error
    val collapsedLine = message.kind in COLLAPSED_KINDS && !expanded
    val bodyAlign = if (isUser) TextAlign.End else TextAlign.Start

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // A thin rule marks who is speaking, on the side the message is aligned to. Bubbles
        // would eat the round panel's width, and the user's own lines are set apart by being
        // smaller and pushed to the right instead.
        if (isError) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.errorText),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            val entry = message.kind in COLLAPSED_KINDS
            val glyph = if (entry) {
                entryGlyph(message.kind, message.title.ifBlank { message.text })
            } else {
                null
            }
            val iconTint = if (isError) colors.errorText else colors.muted
            if (collapsedLine) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    glyph?.let {
                        WatchEntryIcon(
                            glyph = it,
                            tint = iconTint,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                    Text(
                        text = "${kindLabel(message.kind)} · ${message.displayText()}",
                        modifier = Modifier.weight(1f),
                        color = colors.muted,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    glyph?.let {
                        WatchEntryIcon(
                            glyph = it,
                            tint = iconTint,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                    Text(
                        text = if (entry) kindLabel(message.kind) else authorLabel(message),
                        color = when {
                            isError -> colors.errorText
                            isUser -> colors.sessionStatusAccentText
                            entry -> colors.muted
                            else -> colors.faint
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        textAlign = if (entry) TextAlign.Start else bodyAlign,
                    )
                }
                Text(
                    text = message.displayText(),
                    color = when {
                        isError -> colors.errorText
                        entry -> colors.muted
                        else -> colors.ink
                    },
                    fontSize = when {
                        entry -> 10.sp
                        isUser -> 11.sp
                        else -> 12.sp
                    },
                    lineHeight = when {
                        entry -> 13.sp
                        isUser -> 14.sp
                        else -> 15.sp
                    },
                    textAlign = if (entry) TextAlign.Start else bodyAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (entry) {
                                Modifier.clickable(onClick = onToggle)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }

        if (isUser) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.sessionStatusAccent),
            )
        }
    }
}

/**
 * How far past the newest entry to aim when the page opens.
 *
 * `scrollToItem` puts an item's top at the top of the viewport, so aiming at the last index would
 * still show the beginning of a long answer. This overshoots by about two and a half panels; the
 * list clamps it to its own end, which is the bottom the user actually asked for.
 */
private val WatchTranscriptTailDp = 800.dp

/** Side-channel entries: collapsed to one grey line until tapped. */
private val COLLAPSED_KINDS = setOf(
    TimelineMessageKind.Reasoning,
    TimelineMessageKind.Command,
    TimelineMessageKind.ToolCall,
    TimelineMessageKind.AgentCall,
    TimelineMessageKind.FileChange,
    TimelineMessageKind.Diagnostic,
    TimelineMessageKind.Marker,
    TimelineMessageKind.Artifact,
)

private fun kindLabel(kind: TimelineMessageKind): String = when (kind) {
    TimelineMessageKind.Reasoning -> "思考"
    TimelineMessageKind.Command -> "命令"
    TimelineMessageKind.FileChange -> "文件"
    TimelineMessageKind.AgentCall -> "子代理"
    TimelineMessageKind.ToolCall -> "工具"
    TimelineMessageKind.Artifact -> "产物"
    TimelineMessageKind.Marker -> "标记"
    TimelineMessageKind.Diagnostic -> "诊断"
    else -> "记录"
}

private fun authorLabel(message: TimelineMessage): String {
    val prefix = when (message.author) {
        MessageAuthor.User -> "我"
        MessageAuthor.Agent -> "代理"
        MessageAuthor.Tool -> "工具"
    }
    val label = kindLabel(message.kind)
    return if (message.kind in COLLAPSED_KINDS) "$prefix · $label" else prefix
}

private fun TimelineMessage.displayText(): String {
    text.takeIf(String::isNotBlank)?.let { return it }
    body.takeIf(String::isNotBlank)?.let { return it }
    title.takeIf(String::isNotBlank)?.let { return it }
    command.takeIf(String::isNotBlank)?.let { return "$ $it" }
    toolError.takeIf(String::isNotBlank)?.let { return it }
    output.takeIf(String::isNotBlank)?.let { return it }
    return when (kind) {
        TimelineMessageKind.Reasoning -> reasoningSegments.lastOrNull()?.takeIf(String::isNotBlank) ?: "思考中…"
        TimelineMessageKind.FileChange -> "文件改动 ${fileChanges.size} 处"
        TimelineMessageKind.Command -> "运行命令"
        TimelineMessageKind.ToolCall -> "调用工具"
        TimelineMessageKind.AgentCall -> "子代理调用"
        TimelineMessageKind.Artifact -> "产物"
        TimelineMessageKind.Marker -> subtitle.ifBlank { "标记" }
        TimelineMessageKind.Error -> detail.ifBlank { "出错" }
        TimelineMessageKind.Diagnostic -> detail.ifBlank { "诊断" }
        TimelineMessageKind.System -> detail.ifBlank { "系统消息" }
        TimelineMessageKind.Text -> badge.ifBlank { "…" }
    }
}
