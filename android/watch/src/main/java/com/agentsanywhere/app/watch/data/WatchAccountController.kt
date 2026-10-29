package com.agentsanywhere.app.watch.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agentsanywhere.app.api.ApiException
import com.agentsanywhere.app.feature.auth.AuthController
import com.agentsanywhere.app.feature.auth.AuthSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class WatchAccountUiState(
    val loading: Boolean = false,
    val email: String? = null,
    val displayName: String = "",
    val role: String = "",
    /** Whatever `/auth/me` returned: a `data:image/...;base64,...` URL, an http(s) URL, or null. */
    val avatar: String? = null,
    val emailVerified: Boolean = false,
    val disabled: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val draftDisplayName: String? = null,
    // Email binding / verification. Same endpoint pair the phone app uses:
    // POST /auth/email-code {purpose: "bind"} then PUT /auth/me/email.
    val showBindForm: Boolean = false,
    val bindEmail: String = "",
    val bindCode: String = "",
    val bindCooldownSeconds: Int = 0,
    val bindBusy: Boolean = false,
)

/**
 * The account page (page 0) reads the same /auth/me the phone app uses.
 *
 * Signing out is owned by WatchAuthController so the auth gate flips immediately.
 * AuthController already marshals every call onto Dispatchers.IO, so these
 * suspend calls are safe to await straight from the composition scope.
 */
class WatchAccountController(
    private val auth: AuthController,
    private val sessionStore: AuthSessionStore,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(WatchAccountUiState())
        private set

    private var bindCooldownJob: Job? = null

    val serverUrl: String
        get() = sessionStore.readServerUrl()

    val userId: String
        get() = sessionStore.readUserId()

    fun load() {
        state = state.copy(loading = true, error = null)
        scope.launch {
            auth.me()
                .onSuccess { me ->
                    state = state.copy(
                        loading = false,
                        email = me.email,
                        displayName = me.displayName,
                        role = me.role,
                        avatar = me.avatar,
                        emailVerified = me.emailVerified,
                        disabled = me.disabled,
                        error = null,
                        draftDisplayName = null,
                    )
                }
                .onFailure { error ->
                    state = state.copy(
                        loading = false,
                        role = sessionStore.readRole(),
                        error = readable(error, "无法读取账号信息。"),
                    )
                }
        }
    }

    fun beginEditDisplayName() {
        state = state.copy(draftDisplayName = state.displayName, error = null, notice = null)
    }

    fun updateDraftDisplayName(value: String) {
        state = state.copy(draftDisplayName = value)
    }

    fun cancelEditDisplayName() {
        state = state.copy(draftDisplayName = null)
    }

    fun saveDisplayName() {
        val draft = state.draftDisplayName?.trim().orEmpty()
        if (draft.isBlank()) {
            state = state.copy(error = "昵称不能为空。")
            return
        }
        state = state.copy(loading = true, error = null, notice = null)
        scope.launch {
            auth.updateDisplayName(draft)
                .onSuccess { me ->
                    state = state.copy(
                        loading = false,
                        displayName = me.displayName,
                        draftDisplayName = null,
                        notice = "昵称已更新。",
                    )
                }
                .onFailure { error ->
                    state = state.copy(loading = false, error = readable(error, "昵称更新失败。"))
                }
        }
    }

    // ---- email binding / verification --------------------------------------

    /** True when the account has no address yet (e.g. it was created through OAuth). */
    val canBindEmail: Boolean
        get() = state.email.isNullOrBlank()

    /** True when an address exists but the server has not confirmed it yet. */
    val canVerifyEmail: Boolean
        get() = !state.email.isNullOrBlank() && !state.emailVerified

    fun beginBindEmail() {
        state = state.copy(
            showBindForm = true,
            bindEmail = state.email.orEmpty(),
            bindCode = "",
            error = null,
            notice = null,
        )
    }

    fun updateBindEmail(value: String) {
        state = state.copy(bindEmail = value)
    }

    fun updateBindCode(value: String) {
        state = state.copy(bindCode = value)
    }

    fun cancelBindEmail() {
        state = state.copy(showBindForm = false, bindCode = "", error = null)
    }

    fun requestBindCode() {
        val email = state.bindEmail.trim()
        if (email.isBlank() || !email.contains('@')) {
            state = state.copy(error = "请输入有效的邮箱地址。")
            return
        }
        if (state.bindCooldownSeconds > 0 || state.bindBusy) return
        state = state.copy(bindBusy = true, error = null, notice = null)
        scope.launch {
            auth.sendEmailCode(email)
                .onSuccess { response ->
                    state = state.copy(
                        bindBusy = false,
                        notice = "验证码已发送到 $email，${response.expiresIn / 60} 分钟内有效。",
                    )
                    startBindCooldown(response.retryAfter)
                }
                .onFailure { error ->
                    state = state.copy(bindBusy = false, error = readable(error, "验证码发送失败。"))
                }
        }
    }

    fun confirmBindEmail() {
        val email = state.bindEmail.trim()
        val code = state.bindCode.trim()
        if (email.isBlank() || !email.contains('@')) {
            state = state.copy(error = "请输入有效的邮箱地址。")
            return
        }
        if (code.isBlank()) {
            state = state.copy(error = "请输入邮箱验证码。")
            return
        }
        if (state.bindBusy) return
        state = state.copy(bindBusy = true, error = null, notice = null)
        scope.launch {
            auth.bindEmail(email, code)
                .onSuccess { me ->
                    state = state.copy(
                        bindBusy = false,
                        showBindForm = false,
                        bindCode = "",
                        email = me.email ?: email,
                        emailVerified = me.emailVerified,
                        notice = "邮箱已绑定：${me.email ?: email}",
                    )
                }
                .onFailure { error ->
                    state = state.copy(bindBusy = false, error = readable(error, "绑定邮箱失败。"))
                }
        }
    }

    private fun startBindCooldown(seconds: Int) {
        bindCooldownJob?.cancel()
        val total = seconds.coerceAtLeast(0)
        state = state.copy(bindCooldownSeconds = total)
        bindCooldownJob = scope.launch {
            var remaining = total
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                state = state.copy(bindCooldownSeconds = remaining)
            }
        }
    }

    private fun readable(error: Throwable, fallback: String): String {
        if (error is ApiException) {
            translatedDetails[error.message.trim().lowercase()]?.let { return it }
            return when (error.statusCode) {
                401 -> "登录已过期，请退出后重新登录。"
                else -> error.message.takeIf(String::isNotBlank) ?: fallback
            }
        }
        val message = error.message?.takeIf(String::isNotBlank) ?: return fallback
        return translatedDetails[message.lowercase()] ?: message
    }

    private companion object {
        /** Server details (and the upstream "sign in again" guard) in Chinese. */
        val translatedDetails = mapOf(
            "authentication required" to "登录已过期，请退出后重新登录。",
            "email verification is disabled" to "该服务器未启用邮箱验证码。",
            "email is already in use" to "该邮箱已被其他账号使用。",
            "invalid or expired verification code" to "验证码无效或已过期。",
            "verification code is required" to "请输入邮箱验证码。",
            "verification email could not be sent" to "服务器无法发送验证邮件，请稍后重试。",
            "sign in again to update account." to "请重新登录后再操作。",
        )
    }
}
