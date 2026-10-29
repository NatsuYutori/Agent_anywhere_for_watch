package com.agentsanywhere.app.watch.data

import com.agentsanywhere.app.api.ApiClient
import com.agentsanywhere.app.api.ApiException
import com.agentsanywhere.app.api.AuthResponse
import com.agentsanywhere.app.api.EmailCodeResponse
import org.json.JSONObject

/**
 * The password endpoints the phone client never needed.
 *
 * The Android app signs in through an OAuth/mobile-login exchange, so only the Web
 * client implements the email + password flow. These calls reproduce it exactly:
 *
 *   POST /auth/password-salt {email}                        -> {salt, serverTime}
 *   POST /auth/login         {email, passwordVerifier, passwordSalt} -> AuthResponse
 *   POST /auth/email-code    {email, purpose: "register"}   -> {expiresIn, retryAfter}
 *   POST /auth/register      {email, displayName, code, passwordVerifier, passwordSalt}
 *
 * `/auth/password-salt` returns the stored salt for an existing account and a random
 * one otherwise, so a verifier can always be derived and a wrong password surfaces as
 * a plain 401 from /auth/login.
 */
class WatchAuthApi(
    private val client: ApiClient = ApiClient(),
) {
    fun login(serverUrl: String, email: String, password: String): AuthResponse {
        val salt = passwordSalt(serverUrl, email)
        val body = JSONObject()
            .put("email", email)
            .put("passwordVerifier", PasswordVerifier.derive(password, salt))
            .put("passwordSalt", salt)
        return client.postJson(serverUrl, "/auth/login", body).toWatchAuthResponse()
    }

    fun register(
        serverUrl: String,
        email: String,
        displayName: String,
        code: String?,
        password: String,
    ): AuthResponse {
        // Registering sends a client-chosen salt; the server persists it inside the
        // password hash so later logins derive the same verifier.
        val salt = PasswordVerifier.randomSalt()
        val body = JSONObject()
            .put("email", email)
            .put("passwordVerifier", PasswordVerifier.derive(password, salt))
            .put("passwordSalt", salt)
        if (displayName.isNotBlank()) body.put("displayName", displayName)
        if (!code.isNullOrBlank()) body.put("code", code)
        return client.postJson(serverUrl, "/auth/register", body).toWatchAuthResponse()
    }

    fun requestRegisterCode(serverUrl: String, email: String): EmailCodeResponse {
        val body = JSONObject()
            .put("email", email)
            .put("purpose", "register")
        val response = client.postJson(serverUrl, "/auth/email-code", body)
        return EmailCodeResponse(
            expiresIn = response.optInt("expiresIn", 600),
            retryAfter = response.optInt("retryAfter", 60),
        )
    }

    private fun passwordSalt(serverUrl: String, email: String): String {
        val response = client.postJson(
            serverUrl = serverUrl,
            path = "/auth/password-salt",
            body = JSONObject().put("email", email),
        )
        val salt = response.optString("salt")
        if (salt.isBlank()) {
            throw ApiException("服务器未返回密码盐值。")
        }
        return salt
    }
}

private fun JSONObject.toWatchAuthResponse(): AuthResponse {
    return AuthResponse(
        userId = getString("userId"),
        role = optString("role", "member"),
        accessToken = getString("accessToken"),
        tokenType = optString("tokenType", "bearer"),
        serverTime = optString("serverTime", ""),
        email = if (has("email") && !isNull("email")) optString("email").takeIf(String::isNotBlank) else null,
        displayName = optString("displayName", ""),
        emailVerified = optBoolean("emailVerified", false),
    )
}
