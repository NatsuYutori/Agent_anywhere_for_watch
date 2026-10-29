package com.agentsanywhere.app.watch.data

import com.agentsanywhere.app.api.ApiException
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wire-format test for the password endpoints, exercised over real HTTP.
 *
 * The expected shapes come from the server itself:
 *   server/agent_server/core/models.py  — AuthRequest / AuthResponse /
 *                                         AuthPasswordSaltResponse / EmailCodeResponse
 *   server/agent_server/api/auth.py     — auth_password_salt / auth_login /
 *                                         auth_register / send_email_code
 *
 * Everything is routed under the `/api/v2` namespace by ApiUrls.apiPath(), so these
 * tests also pin the request path and prove the client does not fall back to the legacy
 * route when the endpoint answers with JSON.
 *
 * REFERENCE_VERIFIER is what the *server's* own implementation produces for
 * ("secret123", "testsalt"):
 *
 *   hashlib.pbkdf2_hmac("sha256", b"secret123", b"testsalt", 120000)
 *   base64.urlsafe_b64encode(digest).rstrip("=")   # 43 chars, unpadded
 *
 * Asserting it against the verifier the client actually puts on the wire is the check
 * that matters, because the server stores that string verbatim and later compares it
 * byte for byte.
 */
class WatchAuthApiTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private val routes = HashMap<String, Stub>()
    private val seen = mutableListOf<Seen>()

    private data class Stub(val status: Int, val body: String)
    private data class Seen(val path: String, val body: String)

    @Before
    fun startServer() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = runCatching { request.body.readUtf8() }.getOrDefault("")
                synchronized(seen) { seen.add(Seen(path, body)) }
                val stub = routes[path] ?: Stub(404, """{"detail":"not found"}""")
                return MockResponse()
                    .setResponseCode(stub.status)
                    .setHeader("Content-Type", "application/json")
                    .setBody(stub.body)
            }
        }
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `login posts the documented v2 shape and parses the auth response`() {
        routes["/api/v2/auth/password-salt"] =
            Stub(200, """{"salt":"testsalt","serverTime":"t"}""")
        routes["/api/v2/auth/login"] = Stub(200, authResponseJson())

        val auth = WatchAuthApi().login(baseUrl, "user@example.test", "secret123")

        // Namespaced route first, and no legacy retry against a healthy JSON endpoint.
        assertEquals(
            listOf("/api/v2/auth/password-salt", "/api/v2/auth/login"),
            recordedPaths(),
        )

        val loginBody = JSONObject(recordedBody("/api/v2/auth/login"))
        assertEquals(
            setOf("email", "passwordVerifier", "passwordSalt"),
            loginBody.keys().asSequence().toSet(),
        )
        assertEquals("user@example.test", loginBody.getString("email"))
        assertEquals("testsalt", loginBody.getString("passwordSalt"))
        assertEquals(REFERENCE_VERIFIER, loginBody.getString("passwordVerifier"))

        assertEquals("user-1", auth.userId)
        assertEquals("member", auth.role)
        assertEquals("token-abc", auth.accessToken)
        assertEquals("bearer", auth.tokenType)
        assertEquals("2026-01-01T00:00:00Z", auth.serverTime)
        assertEquals("user@example.test", auth.email)
        assertEquals("小明", auth.displayName)
        assertTrue(auth.emailVerified)
    }

    @Test
    fun `login echoes the stored salt and surfaces the 401 detail the server sends`() {
        routes["/api/v2/auth/password-salt"] =
            Stub(200, """{"salt":"stored-salt","serverTime":"t"}""")
        routes["/api/v2/auth/login"] = Stub(401, """{"detail":"invalid credentials"}""")

        val failure = runCatching {
            WatchAuthApi().login(baseUrl, "user@example.test", "wrong-password")
        }.exceptionOrNull()

        val apiException = failure as ApiException
        assertEquals(401, apiException.statusCode)
        // Exactly the string WatchAuthController's translation table keys on.
        assertEquals("invalid credentials", apiException.message)
        assertEquals("stored-salt", JSONObject(recordedBody("/api/v2/auth/login")).getString("passwordSalt"))
    }

    @Test
    fun `login fails clearly when the server returns no salt`() {
        routes["/api/v2/auth/password-salt"] = Stub(200, """{"serverTime":"t"}""")

        val failure = runCatching {
            WatchAuthApi().login(baseUrl, "user@example.test", "secret123")
        }.exceptionOrNull()

        assertEquals("服务器未返回密码盐值。", (failure as ApiException).message)
        assertEquals(listOf("/api/v2/auth/password-salt"), recordedPaths())
    }

    @Test
    fun `register sends a client chosen salt and only the optional fields provided`() {
        routes["/api/v2/auth/register"] = Stub(200, authResponseJson())
        val api = WatchAuthApi()

        api.register(
            serverUrl = baseUrl,
            email = "new@example.test",
            displayName = "",
            code = null,
            password = "secret123",
        )
        val minimal = JSONObject(recordedBody("/api/v2/auth/register"))
        assertEquals(
            setOf("email", "passwordVerifier", "passwordSalt"),
            minimal.keys().asSequence().toSet(),
        )
        val salt = minimal.getString("passwordSalt")
        assertEquals(22, salt.length)
        assertFalse("salt must be unpadded base64url", salt.contains('='))
        // The verifier value itself is pinned by PasswordVerifierTest's fixed vectors;
        // with a random salt the invariant to check here is that it matches that salt.
        assertEquals(
            PasswordVerifier.derive("secret123", salt),
            minimal.getString("passwordVerifier"),
        )

        api.register(
            serverUrl = baseUrl,
            email = "new2@example.test",
            displayName = "小明",
            code = "123456",
            password = "secret123",
        )
        val full = JSONObject(recordedBody("/api/v2/auth/register"))
        assertEquals("小明", full.getString("displayName"))
        assertEquals("123456", full.getString("code"))
    }

    @Test
    fun `register code request uses the register purpose and parses the windows`() {
        routes["/api/v2/auth/email-code"] =
            Stub(200, """{"expiresIn":600,"retryAfter":60,"serverTime":"t"}""")

        val response = WatchAuthApi().requestRegisterCode(baseUrl, "new@example.test")

        assertEquals(600, response.expiresIn)
        assertEquals(60, response.retryAfter)
        val body = JSONObject(recordedBody("/api/v2/auth/email-code"))
        assertEquals("register", body.getString("purpose"))
        assertEquals("new@example.test", body.getString("email"))
    }

    private fun recordedPaths(): List<String> = synchronized(seen) { seen.map { it.path } }

    private fun recordedBody(path: String): String = synchronized(seen) {
        seen.lastOrNull { it.path == path }?.body
            ?: error("no request was recorded for $path; saw ${seen.map { it.path }}")
    }

    private fun authResponseJson(): String = JSONObject()
        .put("userId", "user-1")
        .put("role", "member")
        .put("accessToken", "token-abc")
        .put("tokenType", "bearer")
        .put("serverTime", "2026-01-01T00:00:00Z")
        .put("email", "user@example.test")
        .put("displayName", "小明")
        .put("emailVerified", true)
        .toString()

    private companion object {
        /** Produced by the server's own PBKDF2 settings for ("secret123", "testsalt"). */
        const val REFERENCE_VERIFIER = "ya57mR6uyioAY1JHvwbtpNr5BPfIBI7As7AeYNhG8wQ"
    }
}
