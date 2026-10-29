package com.agentsanywhere.app.watch.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.data.WatchAuthMode
import com.agentsanywhere.app.watch.data.WatchAuthUiState
import com.agentsanywhere.app.watch.ui.WatchCaption
import com.agentsanywhere.app.watch.ui.WatchErrorBanner
import com.agentsanywhere.app.watch.ui.WatchLoginLayout
import com.agentsanywhere.app.watch.ui.WatchNoticeText
import com.agentsanywhere.app.watch.ui.WatchPillButton
import com.agentsanywhere.app.watch.ui.WatchPrimaryButton
import com.agentsanywhere.app.watch.ui.WatchSafeHorizontal
import com.agentsanywhere.app.watch.ui.WatchSecondaryButton
import com.agentsanywhere.app.watch.ui.WatchTextField
import com.agentsanywhere.app.watch.ui.WatchWordmark

/**
 * Email + password sign-in and registration, sized for a round 466px panel.
 *
 * The watch has a full 26-key OPPO keyboard, so real text entry is viable; the screen
 * scrolls because the keyboard covers most of the panel while open.
 *
 * Layout is deliberate rather than incidental (see [WatchLoginLayout]):
 *  - the sign-in button is the biggest control on the panel (48dp, full content width, and
 *    placed while the circle is still wide) so the primary action is unmistakable;
 *  - registration and the server settings are secondary, so they are small ellipses side
 *    by side underneath, sized by [WatchRound.maxRowWidth] for the height they sit at -
 *    a full-width pair there would run outside the bezel;
 *  - the trailing spacer equals the leftover room, so a page that does need to scroll (the
 *    register form, or an error banner) lands on these same positions when scrolled to the
 *    bottom.
 *
 * There is no login-time second factor in the upstream protocol — the 6-digit email code is
 * required for registration (and email binding) only.
 */
@Composable
fun WatchAuthScreen(
    state: WatchAuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onToggleServerField: () -> Unit,
    onSignIn: () -> Unit,
    onOpenRegister: () -> Unit,
    onRequestCode: () -> Unit,
    onRegister: () -> Unit,
    onBackToSignIn: () -> Unit,
) {
    val colors = LocalAAColors.current
    val registering = state.mode == WatchAuthMode.Register

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WatchSafeHorizontal, vertical = WatchLoginLayout.TopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WatchLoginLayout.Gap),
    ) {
        // The header block keeps its exact height and centres its content: it is the first
        // term of the vertical rhythm below (WatchLoginLayout.HeaderHeight), so removing the
        // sign-in subtitle must not shift the fields up into the narrow top of the circle.
        Box(
            modifier = Modifier.height(WatchLoginLayout.HeaderHeight),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Sign-in is where the product signs itself, so the wordmark is the real one:
                // Caveat at Medium, the same file the web and phone clients ship.
                WatchWordmark(fontSize = 24.sp, textAlign = TextAlign.Center)
                if (registering) {
                    WatchCaption(text = "注册新账号", textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }

        state.error?.let { message -> WatchErrorBanner(message) }
        state.notice?.let { message -> WatchNoticeText(message) }

        WatchTextField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = "邮箱",
            keyboardType = KeyboardType.Email,
        )

        if (registering) {
            WatchTextField(
                value = state.code,
                onValueChange = onCodeChange,
                placeholder = "6 位验证码",
                keyboardType = KeyboardType.Number,
            )
            WatchSecondaryButton(
                text = if (state.cooldownSeconds > 0) {
                    "重新发送（${state.cooldownSeconds}s）"
                } else {
                    "发送验证码"
                },
                onClick = onRequestCode,
                enabled = state.cooldownSeconds == 0 && !state.busy,
                height = 34.dp,
                cornerRadius = 17.dp,
            )
        }

        WatchTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = if (registering) "密码（至少 8 位）" else "密码",
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = if (registering) onRegister else onSignIn,
        )

        if (registering) {
            WatchTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                placeholder = "昵称（可选）",
                imeAction = ImeAction.Done,
                onImeAction = onRegister,
            )
        }

        WatchPrimaryButton(
            text = if (registering) "注册并登录" else "登录",
            onClick = if (registering) onRegister else onSignIn,
            loading = state.busy,
            height = WatchLoginLayout.PrimaryHeight,
            cornerRadius = WatchLoginLayout.PrimaryHeight / 2,
        )

        Row(
            modifier = Modifier.width(WatchLoginLayout.pillRowWidth),
            horizontalArrangement = Arrangement.spacedBy(WatchLoginLayout.PillGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WatchPillButton(
                text = if (registering) "返回登录" else "注册",
                onClick = if (registering) onBackToSignIn else onOpenRegister,
                enabled = !state.busy,
                modifier = Modifier.width(WatchLoginLayout.pillWidth),
            )
            WatchPillButton(
                text = if (state.showServerField) "收起" else "服务器",
                onClick = onToggleServerField,
                enabled = !state.busy,
                modifier = Modifier.width(WatchLoginLayout.pillWidth),
            )
        }

        if (state.showServerField) {
            WatchCaption(text = "服务器地址", textAlign = TextAlign.Center)
            WatchTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                placeholder = "https://...",
                keyboardType = KeyboardType.Uri,
            )
            WatchCaption(
                text = "密码只在本机派生校验值，明文不会离开手表。",
                textAlign = TextAlign.Center,
            )
        }

        // Keeps "scrolled to the bottom" aligned with the positions the widths were
        // computed for, so the pill row never drifts under the bezel.
        Spacer(modifier = Modifier.height(WatchLoginLayout.trailingSpace))
    }
}
