package com.agentsanywhere.app.watch.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.feature.sessiondetail.RuntimeNotice
import com.agentsanywhere.app.feature.sessiondetail.RuntimeSelectionOption
import com.agentsanywhere.app.feature.sessiondetail.SESSION_INTERRUPT_CAPABILITY
import com.agentsanywhere.app.feature.sessiondetail.SESSION_MODEL_CATALOG_CAPABILITY
import com.agentsanywhere.app.feature.sessiondetail.SESSION_PERMISSION_CATALOG_CAPABILITY
import com.agentsanywhere.app.feature.sessiondetail.SessionDetailState
import com.agentsanywhere.app.feature.sessiondetail.runtimeSelectionEnabled
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.ui.WatchCaption
import com.agentsanywhere.app.watch.ui.WatchChoiceChip
import com.agentsanywhere.app.watch.ui.WatchEmptyGlyph
import com.agentsanywhere.app.watch.ui.WatchEmptyState
import com.agentsanywhere.app.watch.ui.WatchErrorBanner
import com.agentsanywhere.app.watch.ui.WatchInfoRow
import com.agentsanywhere.app.watch.ui.WatchPageScaffold
import com.agentsanywhere.app.watch.ui.WatchPrimaryButton
import com.agentsanywhere.app.watch.ui.WatchSecondaryButton

/**
 * Action sizing.
 *
 * This page is the remote control, so its actions are its content: primaries are the
 * tallest full-width pills in the app, secondaries are deliberately shorter so the two
 * ranks can never be mistaken for each other, and the runtime/status lines above them
 * shrink to background size.
 */
private val PrimaryActionHeight = 34.dp
private val SecondaryActionHeight = 26.dp

/** Approval buttons sit inside a card, so they are a step under a page primary. */
private val ApprovalActionHeight = 32.dp

/**
 * Page 2 — runtime actions: interrupt, takeover, model and permission selection, and
 * any approval the runtime is waiting on. Reached by dragging the finger to the left
 * from the conversation.
 *
 * Selection changes are gated on takeover, exactly like the phone app: a runtime
 * rejects a selection patch while nobody holds the session.
 */
@Composable
fun WatchActionsPage(
    detail: SessionDetailState,
    modelOptions: List<RuntimeSelectionOption>,
    permissionOptions: List<RuntimeSelectionOption>,
    selectedModelId: String?,
    selectedPermissionId: String?,
    busy: Boolean,
    error: String?,
    notice: String?,
    scrollState: ScrollState,
    onTakeover: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectPermission: (String) -> Unit,
    onRespondNotice: (String, String, Map<String, Any?>?) -> Unit,
    onReloadCatalogs: () -> Unit,
) {
    val colors = LocalAAColors.current
    val session = detail.session
    val takeover = session?.takeover == true
    val modelUsable = detail.capabilities.isUsable(
        SESSION_MODEL_CATALOG_CAPABILITY,
        detail.runtime.runtimeId,
        detail.runtime.runtimeType,
    )
    val permissionUsable = detail.capabilities.isUsable(
        SESSION_PERMISSION_CATALOG_CAPABILITY,
        detail.runtime.runtimeId,
        detail.runtime.runtimeType,
    )
    val approvals = detail.notices.notices.filter { it.respondable }

    WatchPageScaffold(
        title = "操作",
        subtitle = session?.title?.ifBlank { "未命名对话" } ?: "未选择对话",
        scrollState = scrollState,
    ) {
        error?.let { message -> WatchErrorBanner(message) }
        notice?.let { message -> WatchCaption(text = message, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2) }

        if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                WatchEmptyState(glyph = WatchEmptyGlyph.Controls, text = "")
            }
            return@WatchPageScaffold
        }

        // Background information: the smallest text on the page, so the buttons below it
        // read as the page's actual content.
        WatchInfoRow(
            label = "运行时",
            value = session.runtimeContextLabel.ifBlank { session.runtimeLabel },
            dense = true,
        )
        WatchInfoRow(
            label = "状态",
            value = "${session.statusLabel} · ${if (session.connectorOnline) "设备在线" else "设备离线"}",
            dense = true,
            online = session.connectorOnline,
        )

        // How much work this conversation has taken, and what it cost. Three numbers fit in two
        // rows; the token breakdown is behind a tap because it is the only part of it that is
        // read occasionally rather than at a glance.
        val usage = remember(detail.messages) { buildWatchUsage(detail.messages) }
        SectionTitle("用量")
        WatchInfoRow(
            label = "进度",
            value = "${usage.turns} 轮 · ${usage.steps} 步",
            dense = true,
        )
        if (usage.reported) {
            var usageOpen by remember { mutableStateOf(false) }
            WatchChoiceChip(
                text = "tokens ${formatTokens(usage.total)}",
                selected = usageOpen,
                icon = Icons.Default.Info,
                onClick = { usageOpen = !usageOpen },
            )
            AnimatedVisibility(
                visible = usageOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    UsageDetailRow("输入", usage.input)
                    UsageDetailRow("输出", usage.output)
                    UsageDetailRow("缓存命中", usage.cacheRead)
                    UsageDetailRow("缓存写入", usage.cacheWrite)
                    UsageDetailRow("合计", usage.total, emphasise = true)
                }
            }
        } else {
            WatchCaption(
                text = "这段对话里没有 token 统计：运行时没有把用量写进事件。",
                maxLines = 3,
            )
        }

        if (approvals.isNotEmpty()) {
            SectionTitle("待审批（${approvals.size}）")
            approvals.forEach { approval ->
                WatchNoticeCard(
                    notice = approval,
                    busy = busy,
                    onRespond = onRespondNotice,
                )
            }
        }

        SectionTitle("接管")
        WatchCaption(
            text = if (takeover) "已接管：可以切换模型和权限。" else "未接管：切换模型前需要先接管。",
            maxLines = 2,
        )
        if (takeover) {
            WatchSecondaryButton(
                text = "释放接管",
                onClick = { onTakeover(false) },
                enabled = !busy,
                height = SecondaryActionHeight,
                cornerRadius = SecondaryActionHeight / 2,
                icon = Icons.Default.Close,
            )
        } else {
            WatchPrimaryButton(
                text = "接管会话",
                onClick = { onTakeover(true) },
                enabled = !busy,
                height = PrimaryActionHeight,
                cornerRadius = PrimaryActionHeight / 2,
                icon = Icons.Default.Person,
            )
        }

        // Stopping the turn is not here any more: the composer turns into the stop button while
        // the agent is producing, so the control sits next to the thing it stops rather than on
        // a page the user has to swipe to while waiting.
        SectionTitle("模型")
        val modelSelectionEnabled = runtimeSelectionEnabled(takeover, modelUsable)
        if (modelOptions.isEmpty()) {
            WatchCaption(
                text = when {
                    !modelUsable -> "该运行时没有提供模型列表。"
                    takeover -> "正在读取模型列表…"
                    else -> "先接管会话才能切换。"
                },
                maxLines = 2,
            )
        } else {
            // One row, showing the model in use. The list only exists while the model is being
            // changed, and it unfolds in place: a watch has no room to keep fifteen rows on
            // screen for a choice that is made once, and the current model is the only part of
            // that list worth showing the rest of the time.
            var modelsOpen by remember { mutableStateOf(false) }
            var unfolded by remember { mutableStateOf<String?>(null) }
            val groups = buildModelGroups(modelOptions)
            val activeGroup = groups.firstOrNull { group ->
                group.options.any { it.isActive(selectedModelId) }
            }
            val active = activeGroup?.options?.firstOrNull { it.isActive(selectedModelId) }
            WatchChoiceChip(
                text = when {
                    activeGroup == null -> "未选择模型"
                    activeGroup.foldable && active != null ->
                        "${activeGroup.title} · ${active.variantLabel()}"

                    else -> activeGroup.title
                },
                selected = true,
                enabled = modelSelectionEnabled && modelOptions.any { it.enabled },
                icon = Icons.Default.Star,
                onClick = { modelsOpen = !modelsOpen },
            )
            AnimatedVisibility(
                visible = modelsOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    groups.forEach { group ->
                        val groupActive = group.options.firstOrNull { it.isActive(selectedModelId) }
                        val isOpen = unfolded == group.title
                        WatchChoiceChip(
                            text = if (group.foldable && groupActive != null) {
                                "${group.title} · ${groupActive.variantLabel()}"
                            } else {
                                group.title
                            },
                            selected = groupActive != null,
                            enabled = modelSelectionEnabled && group.options.any { it.enabled },
                            onClick = {
                                if (group.foldable) {
                                    unfolded = if (isOpen) null else group.title
                                } else {
                                    group.options.firstOrNull()?.let { onSelectModel(it.selectionId) }
                                }
                            },
                        )
                        if (group.foldable && isOpen) {
                            group.options.forEach { option ->
                                WatchChoiceChip(
                                    text = option.variantLabel(),
                                    selected = option.isActive(selectedModelId),
                                    enabled = modelSelectionEnabled && option.enabled,
                                    onClick = { onSelectModel(option.selectionId) },
                                    modifier = Modifier.padding(start = 14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (permissionOptions.isNotEmpty()) {
            SectionTitle("权限")
            val permissionSelectionEnabled = runtimeSelectionEnabled(takeover, permissionUsable)
            permissionOptions.forEach { option ->
                // The runtime names these in English ("Read Only", "Workspace Write"); the watch
                // shows them in Chinese with a glyph, because a lock says it faster than a
                // sentence does at 11sp.
                val permission = watchPermission(option.label, option.selectionId)
                WatchChoiceChip(
                    text = permission.label,
                    selected = option.selectionId == selectedPermissionId ||
                        (selectedPermissionId == null && option.default),
                    enabled = permissionSelectionEnabled && option.enabled,
                    icon = permission.icon,
                    onClick = { onSelectPermission(option.selectionId) },
                )
            }
        }

        WatchSecondaryButton(
            text = "重新读取模型与权限",
            onClick = onReloadCatalogs,
            enabled = !busy,
            height = SecondaryActionHeight,
            cornerRadius = SecondaryActionHeight / 2,
            icon = Icons.Default.Refresh,
        )
    }
}

/**
 * Section label. Kept small and faint on purpose: on this page the labels are background
 * and the controls under them are the content.
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        color = LocalAAColors.current.faint,
        fontSize = 10.sp,
        letterSpacing = 0.6.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

/** One line of the expanded token breakdown: label on the left, exact count on the right. */
@Composable
private fun UsageDetailRow(label: String, value: Long, emphasise: Boolean = false) {
    val colors = LocalAAColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (emphasise) colors.muted else colors.faint,
            fontSize = 9.sp,
            maxLines = 1,
        )
        Text(
            text = formatCount(value),
            color = if (emphasise) colors.ink else colors.inkSoft,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

// The approval card moved to WatchNoticeCard.kt, where it can also render an input request.

