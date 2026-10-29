package com.agentsanywhere.app.watch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Byte-compatibility tests for the login verifier.
 *
 * The expected values were produced by an independent implementation
 * (Node/OpenSSL `pbkdf2Sync(password, salt, 120000, 32, "sha256").toString("base64url")`),
 * the same reference `web-next/test/password-verifier.test.mjs` asserts
 * `server/agent_server/core/auth.py` against. If these ever fail, the watch build can
 * no longer sign in to the official cloud.
 */
class PasswordVerifierTest {

    @Test
    fun `matches the OpenSSL reference vectors`() {
        val vectors = listOf(
            Triple(
                "synthetic-login-password",
                "synthetic-login-salt",
                "_cpnCB8b7a9nU20EDM0sQMI8GRT7HmxsKQdToityd1Q",
            ),
            Triple(
                "\u5bc6\u7801 \ud83d\udd10 cafe\u0301",
                "\u542b\u4e2d\u6587\u7684\u76d0_-",
                "jZpqvHCz-RB7CHoAWLbHF1a0ylFKTxqcpjOsx5f_nKk",
            ),
            Triple(
                "long password \u5bc6\u7801 ".repeat(20),
                "salt",
                "6jh72H54HXruYaX9ce1k0cOexgmDkfHC38fksYJrcfU",
            ),
            Triple(
                "password\u0000with\u0000nulls",
                "salt\u0000suffix",
                "7hKfUXYyaL9WMZ3_BFpoWPK_x9KXlkh3iO4tzifJP6s",
            ),
        )

        for ((password, salt, expected) in vectors) {
            assertEquals(
                "verifier mismatch for password=[$password] salt=[$salt]",
                expected,
                PasswordVerifier.derive(password, salt),
            )
        }
    }

    /**
     * The upstream Web test also covers `("", "")`. PBEKeySpec refuses an empty
     * password, and an empty password cannot reach this code path from the watch UI,
     * so the guard is asserted instead of deriving a value nothing can send.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `rejects an empty password`() {
        PasswordVerifier.derive("", "synthetic-login-salt")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an empty salt`() {
        PasswordVerifier.derive("synthetic-login-password", "")
    }

    @Test
    fun `derives 32 unpadded base64url bytes`() {
        val verifier = PasswordVerifier.derive("synthetic-login-password", "synthetic-login-salt")
        assertTrue(
            "unexpected verifier shape: $verifier",
            Regex("^[A-Za-z0-9_-]{43}$").matches(verifier),
        )
        assertFalse(verifier.contains('='))
    }

    @Test
    fun `salt is reused for the same password and salt`() {
        assertEquals(
            PasswordVerifier.derive("same", "same-salt"),
            PasswordVerifier.derive("same", "same-salt"),
        )
        assertNotEquals(
            PasswordVerifier.derive("same", "same-salt"),
            PasswordVerifier.derive("same", "other-salt"),
        )
    }

    @Test
    fun `random salts are 22 char url safe and unique`() {
        val first = PasswordVerifier.randomSalt()
        val second = PasswordVerifier.randomSalt()
        assertTrue("unexpected salt shape: $first", Regex("^[A-Za-z0-9_-]{22}$").matches(first))
        assertNotEquals(first, second)
    }
}
