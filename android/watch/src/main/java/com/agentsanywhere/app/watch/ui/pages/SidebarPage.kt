package com.agentsanywhere.app.watch.ui.pages

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.model.AgentDevice
import com.agentsanywhere.app.model.AgentProject
import com.agentsanywhere.app.model.AgentSession
import com.agentsanywhere.app.model.SessionStatus
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.ui.WatchCaption
import com.agentsanywhere.app.watch.ui.WatchErrorBanner
import com.agentsanywhere.app.watch.ui.WatchPageScaffold
import com.agentsanywhere.app.watch.ui.WatchPrimaryButton
import com.agentsanywhere.app.watch.ui.WatchSecondaryButton

/** Group id for the conversations that no working directory claims. */
internal const val SIDEBAR_UNFILED_GROUP_ID = "__unfiled__"

/** One collapsible sidebar row together with the conversations filed under it. */
internal data class SidebarGroup(
    val id: String,
    val title: String,
    val detail: String,
    val sessions: List<AgentSession>,
    // Set only when the page is labelling rows with the device they live on, so a row drawn
    // from the old call site is unchanged.
    val deviceName: String = "",
)

/**
 * Files every conversation under its working directory.
 *
 * The sidebar used to render the directories as a *filter* and then dump every matching
 * conversation at the bottom of the page, so the row that was tapped and the list it changed
 * were never next to each other. Grouping first keeps the two together, and being a plain
 * function it can be tested without a watch.
 *
 * Order follows [projects] so the list does not reshuffle between polls; anything whose
 * project is missing or unknown collects in one trailing group.
 *
 * [deviceNamesById] is keyed by connector id, which is what [AgentProject.connectorId] holds.
 * It is empty when the page is not labelling rows, which keeps the default answer identical to
 * the one before devices existed. When it is populated, the device name is appended to [detail]
 * because two devices can own the same path (`~/work` on a laptop and on a desktop) and the path
 * alone then reads as a duplicate row.
 */
internal fun buildSidebarGroups(
    projects: List<AgentProject>,
    sessions: List<AgentSession>,
    deviceNamesById: Map<String, String> = emptyMap(),
): List<SidebarGroup> {
    val known = projects.mapTo(mutableSetOf()) { it.id }
    val byProject = sessions.groupBy { it.projectId }
    val groups = projects.map { project ->
        // Trimmed, not just defaulted: a device with a whitespace-only name would otherwise
        // become a second, invisible " · " separator in the detail line.
        val deviceName = deviceNamesById[project.connectorId].orEmpty().trim()
        SidebarGroup(
            id = project.id,
            title = project.name.ifBlank { project.workspacePath.ifBlank { "未命名目录" } },
            detail = listOf(project.workspacePath, deviceName)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            sessions = byProject[project.id].orEmpty(),
            deviceName = deviceName,
        )
    }
    val unfiled = sessions.filter { it.projectId == null || it.projectId !in known }
    return if (unfiled.isEmpty()) {
        groups
    } else {
        // No device name here: the group mixes conversations from every device by definition,
        // so one name on the row would be a half truth.
        groups + SidebarGroup(
            id = SIDEBAR_UNFILED_GROUP_ID,
            title = "未归属目录",
            detail = "",
            sessions = unfiled,
        )
    }
}

/**
 * Page 0 — sidebar: working directories (projects) and the conversations inside them.
 *
 * Leftmost page: dragging the finger to the right from the conversation reaches it.
 *
 * Every directory starts collapsed and opens in place, so the conversations always appear
 * directly under the row that was tapped rather than in a separate list at the bottom.
 *
 * [projects] and [sessions] arrive already filtered by [deviceFilterId], so this page never
 * drops anything itself: two views of one account means the filtering has exactly one owner.
 */
@Composable
fun WatchSidebarPage(
    devices: List<AgentDevice>,
    deviceFilterId: String?,
    projects: List<AgentProject>,
    sessions: List<AgentSession>,
    errorMessage: String?,
    scrollState: ScrollState,
    selectedSessionId: String?,
    loading: Boolean,
    onSelectDevice: (String?) -> Unit,
    onNewSession: () -> Unit,
    onReload: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    // Names only under "all devices": with one device picked, every row on screen belongs to it
    // and repeating the name would cost the title width it needs to stay readable at 9sp.
    val labelDevices = deviceFilterId == null && devices.size > 1
    val deviceNamesById: Map<String, String> = remember(devices, labelDevices) {
        if (labelDevices) devices.associate { it.id to deviceLabel(it) } else emptyMap()
    }
    // Keyed on the filter: a directory left open on the previous device is not the one the user
    // is looking at now, and the accordion would otherwise carry the expansion across the switch.
    var expandedIds by remember(deviceFilterId) { mutableStateOf(emptySet<String>()) }
    val groups = remember(projects, sessions, deviceNamesById) {
        buildSidebarGroups(projects, sessions, deviceNamesById)
    }

    WatchPageScaffold(
        title = "工作区",
        subtitle = "${projects.size} 个工作目录 · ${sessions.size} 个对话",
        scrollState = scrollState,
    ) {
        errorMessage?.let { message -> WatchErrorBanner(message) }

        // One device cannot be switched away from, and the row costs two lines of a 233dp panel.
        if (devices.size > 1) {
            DeviceSelector(
                devices = devices,
                selectedDeviceId = deviceFilterId,
                onSelectDevice = onSelectDevice,
            )
        }

        // Above the list rather than at its end: starting a conversation is what this page is
        // for, and the bottom of a scrolling list is where controls go to be missed.
        WatchPrimaryButton(text = "＋ 新会话", onClick = onNewSession)

        if (groups.isEmpty()) {
            WatchCaption(
                text = when {
                    loading -> "加载中…"
                    devices.isEmpty() -> "还没有在线的工作设备，请先在手机或电脑上运行 Connector。"
                    else -> "还没有对话。"
                },
                textAlign = TextAlign.Center,
            )
        }

        groups.forEach { group ->
            val expanded = group.id in expandedIds
            val containsSelection = group.sessions.any { it.id == selectedSessionId }
            GroupRow(
                group = group,
                expanded = expanded,
                containsSelection = containsSelection,
                onToggle = {
                    expandedIds = if (expanded) {
                        expandedIds - group.id
                    } else {
                        expandedIds + group.id
                    }
                },
            )
            if (expanded) {
                NestedSessions(
                    sessions = group.sessions,
                    selectedSessionId = selectedSessionId,
                    onOpenSession = onOpenSession,
                )
            }
        }

        WatchSecondaryButton(
            text = "刷新列表",
            onClick = onReload,
            enabled = !loading,
            height = 34.dp,
            cornerRadius = 17.dp,
            icon = Icons.Default.Refresh,
        )
    }
}

/** How the selector and the directory rows spell one device. */
private fun deviceLabel(device: AgentDevice): String {
    val name = device.name.trim()
    if (name.isNotEmpty()) return name
    // The name is user-editable and can be cleared; the OS is the only other thing a device
    // reliably reports about itself.
    return device.deviceOs?.trim().orEmpty().ifEmpty { "未命名设备" }
}

/**
 * The device filter, as wrapping chips.
 *
 * It wraps instead of scrolling sideways: a horizontal scroller on a round panel hides the last
 * device behind the bezel with nothing on screen to say that it is there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceSelector(
    devices: List<AgentDevice>,
    selectedDeviceId: String?,
    onSelectDevice: (String?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // "All devices" first and unlabelled by a dot: it is not a device, it is the absence of
        // a filter, and a status light on it would be a status for nothing.
        DeviceChip(
            label = "全部设备",
            selected = selectedDeviceId == null,
            online = null,
            onClick = { onSelectDevice(null) },
        )
        devices.forEach { device ->
            DeviceChip(
                label = deviceLabel(device),
                selected = device.id == selectedDeviceId,
                online = device.online,
                onClick = { onSelectDevice(device.id) },
            )
        }
    }
}

/**
 * One device, as a chip.
 *
 * Not [com.agentsanywhere.app.watch.ui.WatchChoiceChip]: that one fills the width it is given,
 * which is right for the single-column lists elsewhere but would put one device per line here.
 */
@Composable
private fun DeviceChip(
    label: String,
    selected: Boolean,
    online: Boolean?,
    onClick: () -> Unit,
) {
    val colors = LocalAAColors.current
    val shape = RoundedCornerShape(percent = 50)
    val background = if (selected) colors.primaryAction else colors.secondaryActionSurface
    val contentColor = if (selected) colors.onPrimaryAction else colors.ink
    Row(
        modifier = Modifier
            .heightIn(min = 20.dp)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (online != null) {
            // A listed device is not necessarily a reachable one, and the name alone never says
            // so. Green here means the same thing it means everywhere else on this watch.
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (online) colors.noticeSuccess else colors.faint),
            )
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One directory, as a pill: chevron, name, the device it lives on when one is shown, and how
 * much is inside it.
 *
 * It used to be a two-line block with the path under the name, which made every workspace
 * heavier than the conversations inside it, and a full-width row with a 12sp title read as
 * oversized on the watch. One line, a count, and a little air on both sides is the shape that
 * lets the accordion header stay the lightest thing on the page.
 *
 * The device name rides along as a dim tag instead of a second line, because a second line is
 * exactly what the pill was shrunk to get rid of.
 */
@Composable
private fun GroupRow(
    group: SidebarGroup,
    expanded: Boolean,
    containsSelection: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalAAColors.current
    val shape = RoundedCornerShape(percent = 50)
    val background = when {
        containsSelection -> colors.primaryAction
        expanded -> colors.secondaryActionSurface
        else -> colors.raisedSurface
    }
    val contentColor = if (containsSelection) colors.onPrimaryAction else colors.ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(shape)
            .background(background)
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            color = contentColor,
            // 8sp was small enough to read as punctuation rather than as the control it is.
            fontSize = 12.sp,
            lineHeight = 13.sp,
            maxLines = 1,
        )
        Text(
            text = group.title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = if (containsSelection) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (group.deviceName.isNotBlank()) {
            // Capped: a long device name must lose its own tail rather than push the directory
            // name, which is what the row is actually about, into an ellipsis.
            Text(
                text = group.deviceName,
                modifier = Modifier.widthIn(max = 44.dp),
                color = if (containsSelection) colors.onPrimaryAction else colors.faint,
                fontSize = 7.sp,
                lineHeight = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${group.sessions.size}",
            color = if (containsSelection) colors.onPrimaryAction else colors.faint,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            maxLines = 1,
        )
    }
}

/**
 * The conversations of one directory, indented and bracketed by a rule so they read as
 * belonging to the row above instead of as another top-level list.
 */
@Composable
private fun NestedSessions(
    sessions: List<AgentSession>,
    selectedSessionId: String?,
    onOpenSession: (String) -> Unit,
) {
    val colors = LocalAAColors.current
    if (sessions.isEmpty()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(colors.subtle),
            )
            WatchCaption(text = "这个目录下还没有对话。", maxLines = 1)
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.subtle),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            sessions.forEach { session ->
                SessionRow(
                    session = session,
                    selected = session.id == selectedSessionId,
                    onClick = { onOpenSession(session.id) },
                )
            }
        }
    }
}

/**
 * One conversation, as plain text.
 *
 * Nested under a pill it must not be another pill: the row is just a status dot, the title and
 * one line of meta, so the indent and the rule at the left do all the grouping. The open
 * conversation is the white title; everything else recedes.
 */
@Composable
private fun SessionRow(
    session: AgentSession,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAAColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(statusColor(session.status)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = session.title.ifBlank { "未命名对话" },
                color = if (selected) colors.ink else colors.muted,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = if (session.unread || selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildAnnotatedString {
                    // The state is the one word worth picking out of this line, so it is the one
                    // word set in bold; the timestamp and the takeover flag stay background.
                    withStyle(SpanStyle(color = colors.inkSoft, fontWeight = FontWeight.Bold)) {
                        append(session.statusLabel)
                    }
                    append(" · ")
                    append(session.updatedAtLabel)
                    if (session.takeover) append(" · 已接管")
                },
                color = colors.faint,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun statusColor(status: SessionStatus): Color {
    val colors = LocalAAColors.current
    return when (status) {
        SessionStatus.Running -> colors.runtimeRunning
        SessionStatus.Waiting,
        SessionStatus.Pending,
        SessionStatus.Stopping,
        -> colors.runtimeTransitioning

        SessionStatus.WaitingApproval,
        SessionStatus.Blocked,
        -> colors.runtimeActive

        SessionStatus.Error -> colors.errorIcon

        // Green, not white: an idle session is one whose runtime answered the last poll, and
        // that is the single most useful thing to see at a glance in a list of sessions.
        SessionStatus.Idle -> colors.noticeSuccess
        SessionStatus.Unknown -> colors.faint
    }
}
