package com.agentsanywhere.app.watch.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.model.AgentDevice
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.data.WatchNewSessionState

/**
 * "New conversation" as one panel, not a wizard.
 *
 * The phone spreads this over four screens (device, runtime, directory, first message). A watch
 * has one 233dp circle and no room for a stepper, so the flow is flattened: every choice is a
 * pill that already holds a sensible default, and the only thing that has to be produced by the
 * user is the first sentence.
 *
 * The panel is an overlay drawn above the pager, not a fifth page:
 *  - a page would have to be swiped away to abandon it, and on a watch every mistaken swipe is a
 *    lost message, so abandoning is a deliberate tap on 取消;
 *  - tapping the background deliberately does *not* dismiss it either - the circle is small
 *    enough that a stray touch while scrolling the list would throw away the typed sentence;
 *  - it is full-screen with an opaque background, and the circular clip keeps that background
 *    from painting the corners the physical panel never shows.
 *
 * [scrollState] is owned by the shell rather than remembered here, because the crown is the
 * primary input and the shell routes its notches to whichever scroller the visible overlay owns.
 */
@Composable
internal fun WatchNewSessionSheet(
    state: WatchNewSessionState,
    scrollState: ScrollState,
    devices: List<AgentDevice>,
    deviceName: (String?) -> String?,
    onSelectDevice: (String) -> Unit,
    onSelectRuntime: (String) -> Unit,
    onSelectWorkspace: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAAColors.current

    // Every control goes read-only while the create request is in flight: changing the device or
    // the directory mid-request would produce a session that does not match what is on screen.
    val locked = state.creating
    val runtimesError = state.runtimesError

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(colors.canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = WatchSafeHorizontal)
                .padding(top = PanelTopPadding, bottom = PanelBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PanelGap),
        ) {
            Text(
                text = "新会话",
                modifier = Modifier.fillMaxWidth(),
                color = colors.ink,
                fontSize = 13.sp,
                // Pinned so the title never borrows Material3's 24sp default line height, which
                // would push everything below it out of the circle's top.
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )

            // -- device ------------------------------------------------------
            //
            // Only worth a list when there is a choice. With a single connector the sheet still
            // says which machine the conversation lands on, because the sidebar's device filter
            // is covered by the panel and nothing else on screen would tell the two apart.
            if (devices.size > 1) {
                PanelSectionLabel("设备")
                devices.forEach { device ->
                    WatchChoiceChip(
                        text = device.name.ifBlank { device.id },
                        selected = device.id == state.deviceId,
                        enabled = !locked,
                        onClick = { onSelectDevice(device.id) },
                    )
                }
            } else {
                deviceName(state.deviceId)?.let { name ->
                    WatchCaption(
                        text = "在 $name 上创建",
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }

            // -- runtime -----------------------------------------------------
            //
            // The list is short by construction (one row per configured runtime), so it is shown
            // as a list rather than behind a fold: picking one is one tap instead of two, and
            // there is nothing else on this panel that could grow to need the space.
            PanelSectionLabel("运行时")
            when {
                state.runtimesLoading -> WatchCaption(
                    text = "读取中…",
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )

                state.runtimes.isNotEmpty() -> state.runtimes.forEach { runtime ->
                    WatchChoiceChip(
                        text = runtime.name,
                        selected = runtime.id == state.runtimeId,
                        // A lone runtime keeps its pill - it is the answer to "who will reply" -
                        // but is not tappable, because there is nothing to switch to.
                        enabled = state.runtimes.size > 1 && !locked,
                        onClick = { onSelectRuntime(runtime.id) },
                    )
                }

                // Empty and not loading means the fetch failed or the device offers nothing; the
                // controller puts the reason in runtimesError.
                runtimesError != null -> WatchCaption(
                    text = runtimesError,
                    maxLines = 3,
                )

                else -> WatchCaption(
                    text = "这台设备上没有可用的运行时。",
                    maxLines = 2,
                )
            }

            // -- workspace ---------------------------------------------------
            //
            // The title is what the user recognises ("api", "Home directory"); the full path only
            // appears under the selected row, where it still fits and where it answers "which
            // checkout did I just pick" without costing a line per row.
            PanelSectionLabel("工作目录")
            if (state.workspaces.isEmpty()) {
                WatchCaption(text = "这台设备还没有可选的目录。", maxLines = 2)
            } else {
                state.workspaces.forEach { workspace ->
                    val selected = workspace.path == state.workspacePath
                    WatchChoiceChip(
                        text = workspace.title.ifBlank { workspace.path },
                        selected = selected,
                        enabled = !locked,
                        onClick = { onSelectWorkspace(workspace.path) },
                    )
                    if (selected && workspace.detail.isNotBlank() && workspace.detail != workspace.title) {
                        WatchCaption(
                            text = workspace.detail,
                            // Indented to the pill's own text, so the path reads as a note on
                            // that row instead of as another entry in the list.
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 11.dp),
                            maxLines = 1,
                        )
                    }
                }
            }

            // -- first message -----------------------------------------------
            //
            // Everything above has a default; this is the one field that defines the session.
            PanelSectionLabel("首句")
            WatchTextField(
                value = state.text,
                onValueChange = onTextChange,
                placeholder = "想让 agent 做什么？",
                enabled = !locked,
                height = MessageFieldHeight,
                imeAction = ImeAction.Done,
                // The keyboard's confirm key starts the session when the form is complete, the
                // same gesture the conversation composer uses to send.
                onImeAction = { if (state.canCreate) onCreate() },
            )

            // Directly above the button that produces it rather than at the top of the panel:
            // the failure happens under the user's finger, and a banner they have to scroll back
            // to read is a banner that is never read.
            state.error?.let { message -> WatchErrorBanner(message) }

            WatchPrimaryButton(
                text = if (state.creating) "正在创建…" else "开始",
                onClick = onCreate,
                enabled = state.canCreate,
                height = PrimaryActionHeight,
                cornerRadius = PrimaryActionHeight / 2,
                icon = Icons.Default.PlayArrow,
            )
            WatchSecondaryButton(
                text = "取消",
                onClick = onDismiss,
                // The controller refuses to close while the create request is in flight, so the
                // button goes dim rather than staying bright and doing nothing.
                enabled = !locked,
                height = SecondaryActionHeight,
                cornerRadius = SecondaryActionHeight / 2,
                icon = Icons.Default.Close,
            )
        }
    }
}

/**
 * Top inset of the panel.
 *
 * Deeper than a page's because the panel has no page indicator above it to hold the title back
 * from the narrow top of the circle.
 */
private val PanelTopPadding = 14.dp

/**
 * Room left under the last row.
 *
 * Two reasons, both about the bottom of a round panel: the keyboard, when it opens over the
 * input, must not leave the field sitting on the bezel; and a row that ends lower than this is
 * inside the part of the circle that is only ~120dp wide, so a full-width pill would be cut on
 * both sides.
 */
private val PanelBottomPadding = 24.dp

/** Vertical rhythm between the panel's rows. Matches the pages so the overlay feels native. */
private val PanelGap = 5.dp

/** Rank the two actions: the create pill is the tallest control on the panel. */
private val PrimaryActionHeight = 34.dp
private val SecondaryActionHeight = 26.dp

/**
 * Height of the first-message field.
 *
 * Taller than a one-line field so it reads as the place to write a sentence rather than as an
 * identifier box. [WatchTextField] is single-line, so this is breathing room, not wrapping.
 */
private val MessageFieldHeight = 46.dp

/**
 * Section label.
 *
 * Small and faint on purpose: on this panel the labels are background and the pills under them
 * are the content. [lineHeight] is set explicitly because Material3's default would add 24sp of
 * leading to a 10sp label and break the rhythm.
 */
@Composable
private fun PanelSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        color = LocalAAColors.current.faint,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.6.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}
