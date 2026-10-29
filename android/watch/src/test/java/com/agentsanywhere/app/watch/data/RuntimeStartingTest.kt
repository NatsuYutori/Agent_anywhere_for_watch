package com.agentsanywhere.app.watch.data

import com.agentsanywhere.app.api.ApiException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The one distinction that decides between a red strip and a fading line.
 *
 * A runtime that is still synchronizing answers with `409` and `code = runtime_starting`
 * (server/agent_server/services/device_runtimes.py — DeviceRuntimeStartingError). The message
 * on that response is the connector's own phase sentence, e.g. ("DSH 会话同步中断，正在重试…"),
 * which is a state, not a failure. Every other 409 — a conflict, a not-configured runtime — is
 * still a real answer the user has to see.
 */
class RuntimeStartingTest {

    @Test
    fun `a syncing runtime is reported as starting`() {
        val failure = ApiException(
            message = "DSH 会话同步中断，正在重试…",
            statusCode = 409,
            errorCode = "runtime_starting",
        )

        assertTrue(failure.isRuntimeStarting())
    }

    @Test
    fun `the code decides, not the status`() {
        // A 409 without the code is a conflict the user has to read.
        val conflict = ApiException(
            message = "runtime_conflict",
            statusCode = 409,
            errorCode = "runtime_conflict",
        )

        assertFalse(conflict.isRuntimeStarting())
    }

    @Test
    fun `a missing code is not starting`() {
        assertFalse(ApiException(message = "Conflict", statusCode = 409).isRuntimeStarting())
    }

    @Test
    fun `other failures are not starting`() {
        assertFalse(ApiException(message = "Unauthorized", statusCode = 401).isRuntimeStarting())
        assertFalse(IOException("offline").isRuntimeStarting())
        assertFalse(null.isRuntimeStarting())
    }
}
