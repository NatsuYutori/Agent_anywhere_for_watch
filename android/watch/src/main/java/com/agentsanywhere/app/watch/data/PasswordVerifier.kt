package com.agentsanywhere.app.watch.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Client-side password verifier.
 *
 * The plaintext password never leaves the watch. Only PBKDF2-HMAC-SHA256 over
 * UTF-8(password) with the server-provided salt is sent, byte-compatible with
 * `server/agent_server/core/auth.py` (hash_password / hash_password_verifier) and the
 * Web client (`web-next/src/features/auth/password-verifier.ts`):
 *
 *   base64url(PBKDF2-HMAC-SHA256(password, salt, 120000, dkLen = 32)) without padding
 *
 * Note the salt is hashed as its UTF-8 *characters*, not as decoded base64 bytes.
 * The iteration count is a fixed constant across clients: the
 * /auth/password-salt response carries only the salt, never the cost parameters.
 */
internal object PasswordVerifier {
    const val ITERATION_COUNT = 120_000
    const val VERIFIER_LENGTH_BYTES = 32

    private const val SALT_LENGTH_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private val secureRandom = SecureRandom()

    /** A fresh client-chosen salt, used only when registering a new account. */
    fun randomSalt(): String = base64Url(ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes))

    fun derive(password: String, salt: String): String {
        // PBEKeySpec rejects an empty password, and PBKDF2 over an empty salt is
        // meaningless. Both are unreachable from the UI (sign-in requires a
        // non-empty password, registration requires at least 8 characters, and
        // /auth/password-salt always returns a non-empty salt), so fail loudly
        // here instead of hand-rolling PBKDF2 to support an impossible input.
        require(password.isNotEmpty()) { "Password must not be empty." }
        require(salt.isNotEmpty()) { "Password salt must not be empty." }
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            ITERATION_COUNT,
            VERIFIER_LENGTH_BYTES * 8,
        )
        return try {
            val secret = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec)
            base64Url(secret.encoded)
        } finally {
            spec.clearPassword()
        }
    }

    internal fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
