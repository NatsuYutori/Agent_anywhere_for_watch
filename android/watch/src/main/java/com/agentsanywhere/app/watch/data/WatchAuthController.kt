package com.agentsanywhere.app.watch.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agentsanywhere.app.BuildConfig
import com.agentsanywhere.app.api.ApiException
import com.agentsanywhere.app.api.AuthApi
import com.agentsanywhere.app.api.normalizeServerOrigin
import com.agentsanywhere.app.feature.auth.AuthSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WatchAuthMode {
    SignIn,
    Register,
}

data class WatchAuthUiState(
    val serverUrl: String = BuildConfig.OFFICIAL_SERVER_URL,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val code: String = "",
    val mode: WatchAuthMode = WatchAuthMode.SignIn,
    val showServerField: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val cooldownSeconds: Int = 0,
    val requiresEmailCode: Boolean = true,
)

/**
 * Sign-in state for the watch.
 *
 * Reuses the upstream [AuthSessionStore] so the watch build writes exactly the same
 * token store the shared API/feature layers already read, and reuses upstream
 * [AuthApi] for /auth/me and /auth/config. Only the password endpoints are new.
 */
class WatchAuthController(
    private val sessionStore: AuthSessionStore,
    private val scope: CoroutineScope,
    private val authApi: AuthApi = AuthApi(),
    private val passwordApi: WatchAuthApi = WatchAuthApi(),
) {
    var state by mutableStateOf(WatchAuthUiState())
        private set

    var signedIn by mutableStateOf(sessionStore.hasAuthSession())
        private set

    private var cooldownJob: Job? = null

    fun updateEmail(value: String) {
        state = state.copy(email = value.trim(), error = null)
    }

    fun updatePassword(value: String) {
        state = state.copy(password = value, error = null)
    }

    fun updateDisplayName(value: String) {
        state = state.copy(displayName = value, error = null)
    }

    fun updateCode(value: String) {
        state = state.copy(code = value.filter(Char::isDigit).take(6), error = null)
    }

    fun updateServerUrl(value: String) {
        state = state.copy(serverUrl = value.trim(), error = null)
    }

    fun toggleServerField() {
        state = state.copy(showServerField = !state.showServerField, error = null)
    }

    fun openRegister() {
        val serverUrl = resolveServerUrl() ?: return
        state = state.copy(
            mode = WatchAuthMode.Register,
            busy = true,
            error = null,
            notice = null,
        )
        scope.launch {
            io { authApi.authConfig(serverUrl) }
                .onSuccess { config ->
                    state = state.copy(
                        busy = false,
                        requiresEmailCode = config.emailVerificationRequired,
                        error = if (config.registrationOpen) null else "该服务器已关闭自助注册。",
                        notice = if (config.registrationOpen && !config.emailVerificationRequired) {
                            "该服务器无需邮箱验证码。"
                        } else {
                            null
                        },
                    )
                }
                .onFailure { error ->
                    state = state.copy(
                        busy = false,
                        requiresEmailCode = true,
                        error = readableError(error, "无法读取服务器注册配置。"),
                    )
                }
        }
    }

    fun backToSignIn() {
        cooldownJob?.cancel()
        state = state.copy(
            mode = WatchAuthMode.SignIn,
            busy = false,
            error = null,
            notice = null,
            code = "",
            cooldownSeconds = 0,
        )
    }

    fun signIn() {
        val serverUrl = resolveServerUrl() ?: return
        val email = state.email.trim()
        val password = state.password
        if (email.isBlank()) {
            fail("请输入邮箱地址。")
            return
        }
        if (password.isEmpty()) {
            fail("请输入密码。")
            return
        }
        state = state.copy(busy = true, error = null, notice = null)
        scope.launch {
            io { passwordApi.login(serverUrl, email, password) }
                .onSuccess { auth ->
                    sessionStore.saveAuthSession(serverUrl, auth)
                    state = state.copy(busy = false, password = "", error = null)
                    signedIn = true
                }
                .onFailure { error ->
                    state = state.copy(busy = false, error = readableError(error, "登录失败，请重试。"))
                }
        }
    }

    fun requestRegisterCode() {
        val serverUrl = resolveServerUrl() ?: return
        val email = state.email.trim()
        if (email.isBlank() || !email.contains('@')) {
            fail("请先输入有效的邮箱地址。")
            return
        }
        if (state.cooldownSeconds > 0 || state.busy) return
        state = state.copy(busy = true, error = null, notice = null)
        scope.launch {
            io { passwordApi.requestRegisterCode(serverUrl, email) }
                .onSuccess { response ->
                    state = state.copy(
                        busy = false,
                        notice = "验证码已发送，${response.expiresIn / 60} 分钟内有效。",
                    )
                    startCooldown(response.retryAfter)
                }
                .onFailure { error ->
                    state = state.copy(busy = false, error = readableError(error, "验证码发送失败。"))
                }
        }
    }

    fun register() {
        val serverUrl = resolveServerUrl() ?: return
        val email = state.email.trim()
        val password = state.password
        val code = state.code.trim()
        if (email.isBlank() || !email.contains('@')) {
            fail("请输入有效的邮箱地址。")
            return
        }
        if (password.length < 8) {
            fail("密码至少 8 位。")
            return
        }
        if (state.requiresEmailCode && code.isBlank()) {
            fail("请输入邮箱验证码。")
            return
        }
        state = state.copy(busy = true, error = null, notice = null)
        scope.launch {
            io {
                passwordApi.register(
                    serverUrl = serverUrl,
                    email = email,
                    displayName = state.displayName.trim(),
                    code = code.takeIf { state.requiresEmailCode },
                    password = password,
                )
            }
                .onSuccess { auth ->
                    sessionStore.saveAuthSession(serverUrl, auth)
                    state = state.copy(busy = false, password = "", code = "", error = null)
                    signedIn = true
                }
                .onFailure { error ->
                    state = state.copy(busy = false, error = readableError(error, "注册失败，请重试。"))
                }
        }
    }

    fun signOut() {
        cooldownJob?.cancel()
        sessionStore.clearAuthSession()
        state = state.copy(
            mode = WatchAuthMode.SignIn,
            password = "",
            code = "",
            error = null,
            notice = null,
            busy = false,
            cooldownSeconds = 0,
        )
        signedIn = false
    }

    /** Confirms a stored token still works, and prefills the account for a fast re-login. */
    fun verifySavedSession() {
        if (!sessionStore.hasAuthSession()) {
            signedIn = false
            return
        }
        signedIn = true
        scope.launch {
            io { authApi.me(sessionStore.readServerUrl(), sessionStore.readAccessToken()) }
                .onSuccess { me ->
                    state = state.copy(
                        email = me.email?.takeIf(String::isNotBlank) ?: state.email,
                        displayName = me.displayName.ifBlank { state.displayName },
                    )
                }
                .onFailure { error ->
                    if (error is ApiException && error.statusCode == 401) {
                        sessionStore.clearAuthSession()
                        signedIn = false
                        state = state.copy(error = "登录已过期，请重新登录。")
                    }
                }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        val total = seconds.coerceAtLeast(0)
        state = state.copy(cooldownSeconds = total)
        cooldownJob = scope.launch {
            var remaining = total
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                state = state.copy(cooldownSeconds = remaining)
            }
        }
    }

    private fun resolveServerUrl(): String? {
        val normalized = normalizeServerOrigin(state.serverUrl.trim())
        if (normalized == null) {
            fail("服务器地址无效，需要 http(s):// 地址。")
            return null
        }
        return normalized
    }

    private fun fail(message: String) {
        state = state.copy(error = message, busy = false)
    }

    private suspend fun <T> io(block: suspend () -> T): Result<T> =
        runCatching { withContext(Dispatchers.IO) { block() } }

    private fun readableError(error: Throwable, fallback: String): String {
        if (error is ApiException) {
            val detail = error.message.trim()
            translatedDetails[detail.lowercase()]?.let { return it }
            return when (error.statusCode) {
                401 -> "邮箱或密码不正确。"
                403 -> "该服务器已关闭自助注册。"
                null -> fallback
                else -> detail.takeIf { it.isNotBlank() } ?: fallback
            }
        }
        return error.message?.takeIf(String::isNotBlank) ?: fallback
    }

    private companion object {
        val translatedDetails = mapOf(
            "invalid credentials" to "邮箱或密码不正确",
            "registration is closed" to "该服务器已关闭自助注册",
            "email verification is not enabled" to "该服务器未启用邮箱验证",
            "invalid or expired verification code" to "验证码无效或已过期",
            "verification code is required" to "请输入邮箱验证码",
            "email already registered" to "该邮箱已注册，请直接登录",
            "this email is already registered" to "该邮箱已注册，请直接登录",
            "the server returned an invalid response." to "服务器返回了无法解析的数据",
        )
    }
}
