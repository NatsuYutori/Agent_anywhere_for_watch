package com.agentsanywhere.app.watch.ui.pages

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.data.WatchAccountUiState
import com.agentsanywhere.app.watch.ui.WatchAvatar
import com.agentsanywhere.app.watch.ui.WatchErrorBanner
import com.agentsanywhere.app.watch.ui.WatchInfoRow
import com.agentsanywhere.app.watch.ui.WatchNoticeText
import com.agentsanywhere.app.watch.ui.WatchPageScaffold
import com.agentsanywhere.app.watch.ui.WatchPrimaryButton
import com.agentsanywhere.app.watch.ui.WatchSecondaryButton
import com.agentsanywhere.app.watch.ui.WatchTextField
import com.agentsanywhere.app.watch.ui.WatchWordmark

/**
 * Account page control sizing.
 *
 * A step under the actions page: nothing here is urgent, and the page carries more rows than any
 * other, so every control is shorter and every value line is set at the compact size.
 */
private val AccountActionHeight = 30.dp
private val AccountSecondaryHeight = 26.dp

/**
 * Page 3 — account, the rightmost page.
 *
 * Reached by dragging the finger left twice from the conversation.
 *
 * Also hosts email binding: an account can arrive without a confirmed address (the
 * phone client creates some through OAuth), and the server will only accept a bind when
 * the 6-digit code mailed to that address is supplied.
 */
@Composable
fun WatchAccountPage(
    state: WatchAccountUiState,
    scrollState: ScrollState,
    serverUrl: String,
    userId: String,
    onReload: () -> Unit,
    onBeginEditName: () -> Unit,
    onDraftNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onCancelEditName: () -> Unit,
    onBeginBindEmail: () -> Unit,
    onBindEmailChange: (String) -> Unit,
    onBindCodeChange: (String) -> Unit,
    onRequestBindCode: () -> Unit,
    onConfirmBind: () -> Unit,
    onCancelBind: () -> Unit,
    onSignOut: () -> Unit,
) {
    val draftName = state.draftDisplayName
    val hasEmail = !state.email.isNullOrBlank()

    WatchPageScaffold(
        title = "账号",
        scrollState = scrollState,
    ) {
        // Brand first, then who is signed in. The name moved out of the scaffold subtitle so the
        // avatar can sit beside it; the wordmark is drawn in the same face the web and phone
        // clients sign themselves with.
        WatchWordmark(
            fontSize = 21.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WatchAvatar(
                source = state.avatar,
                size = 24.dp,
                fallback = state.displayName.ifBlank { state.email ?: "?" },
            )
            Text(
                text = if (state.loading && state.displayName.isBlank()) {
                    "读取中…"
                } else {
                    state.displayName.ifBlank { state.email ?: "已登录" }
                },
                modifier = Modifier.padding(start = 5.dp),
                color = LocalAAColors.current.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        state.error?.let { message -> WatchErrorBanner(message) }
        state.notice?.let { message -> WatchNoticeText(message) }
        if (state.disabled) {
            WatchErrorBanner("该账号已被服务器禁用。")
        }

        WatchInfoRow(
            label = "邮箱",
            value = state.email?.takeIf(String::isNotBlank) ?: "未绑定",
            dense = true,
        )
        WatchInfoRow(label = "昵称", value = state.displayName.ifBlank { "未设置" }, dense = true)
        WatchInfoRow(label = "角色", value = roleLabel(state.role), dense = true)
        if (hasEmail) {
            WatchInfoRow(
                label = "邮箱验证",
                value = if (state.emailVerified) "已验证" else "未验证",
                dense = true,
            )
        }

        if (state.showBindForm) {
            WatchTextField(
                value = state.bindEmail,
                onValueChange = onBindEmailChange,
                placeholder = "邮箱地址",
                enabled = !state.bindBusy,
                imeAction = ImeAction.Next,
            )
            WatchSecondaryButton(
                text = if (state.bindCooldownSeconds > 0) {
                    "${state.bindCooldownSeconds} 秒后可重发"
                } else {
                    "发送验证码"
                },
                onClick = onRequestBindCode,
                enabled = !state.bindBusy && state.bindCooldownSeconds == 0,
                height = AccountSecondaryHeight,
                cornerRadius = AccountSecondaryHeight / 2,
                icon = Icons.Default.Send,
            )
            WatchTextField(
                value = state.bindCode,
                onValueChange = onBindCodeChange,
                placeholder = "6 位验证码",
                enabled = !state.bindBusy,
                imeAction = ImeAction.Done,
                onImeAction = onConfirmBind,
            )
            WatchPrimaryButton(
                text = if (hasEmail) "验证邮箱" else "绑定邮箱",
                onClick = onConfirmBind,
                enabled = !state.bindBusy,
                loading = state.bindBusy,
                height = AccountActionHeight,
                cornerRadius = AccountActionHeight / 2,
                icon = Icons.Default.Email,
            )
            WatchSecondaryButton(
                text = "取消",
                onClick = onCancelBind,
                enabled = !state.bindBusy,
                height = AccountSecondaryHeight,
                cornerRadius = AccountSecondaryHeight / 2,
                icon = Icons.Default.Close,
            )
        } else if (!hasEmail) {
            WatchPrimaryButton(
                text = "绑定邮箱",
                onClick = onBeginBindEmail,
                enabled = !state.loading,
                height = AccountActionHeight,
                cornerRadius = AccountActionHeight / 2,
                icon = Icons.Default.Email,
            )
        } else if (!state.emailVerified) {
            WatchSecondaryButton(
                text = "验证邮箱",
                onClick = onBeginBindEmail,
                enabled = !state.loading,
                height = AccountSecondaryHeight,
                cornerRadius = AccountSecondaryHeight / 2,
                icon = Icons.Default.Email,
            )
        }

        if (draftName != null) {
            WatchTextField(
                value = draftName,
                onValueChange = onDraftNameChange,
                placeholder = "新的昵称",
                imeAction = ImeAction.Done,
                onImeAction = onSaveName,
            )
            WatchPrimaryButton(
                text = "保存昵称",
                onClick = onSaveName,
                loading = state.loading,
                height = AccountActionHeight,
                cornerRadius = AccountActionHeight / 2,
                icon = Icons.Default.Check,
            )
            WatchSecondaryButton(
                text = "取消",
                onClick = onCancelEditName,
                enabled = !state.loading,
                height = AccountSecondaryHeight,
                cornerRadius = AccountSecondaryHeight / 2,
                icon = Icons.Default.Close,
            )
        } else {
            WatchSecondaryButton(
                text = "修改昵称",
                onClick = onBeginEditName,
                enabled = !state.loading,
                height = AccountSecondaryHeight,
                cornerRadius = AccountSecondaryHeight / 2,
                icon = Icons.Default.Edit,
            )
        }

        WatchInfoRow(label = "服务器", value = serverUrl.ifBlank { "—" }, dense = true)
        WatchInfoRow(label = "用户 ID", value = userId.take(12).ifBlank { "—" }, dense = true)

        WatchSecondaryButton(
            text = "刷新账号信息",
            onClick = onReload,
            enabled = !state.loading,
            height = AccountSecondaryHeight,
            cornerRadius = AccountSecondaryHeight / 2,
            icon = Icons.Default.Refresh,
        )
        WatchPrimaryButton(
            text = "退出登录",
            onClick = onSignOut,
            height = AccountActionHeight,
            cornerRadius = AccountActionHeight / 2,
            icon = Icons.Default.ExitToApp,
        )
    }
}

private fun roleLabel(role: String): String = when (role) {
    "admin" -> "管理员"
    "member" -> "成员"
    else -> role.ifBlank { "未知" }
}
