package com.agentsanywhere.app.watch.ui.pages

import com.agentsanywhere.app.feature.sessiondetail.MessageAuthor
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessage
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Counting what a conversation cost.
 *
 * Turns and steps are counted off the transcript and must be exact. Tokens come from whatever the
 * runtime wrote into its own events, which is a shape this client does not control - so the tests
 * below pin the shapes that are known (Claude Code's per-message usage and its result totals) and
 * pin the behaviour when nothing is there at all.
 */
class WatchUsageTest {

    private fun message(
        id: String,
        author: MessageAuthor = MessageAuthor.Agent,
        kind: TimelineMessageKind = TimelineMessageKind.Text,
        raw: String = "",
    ) = TimelineMessage(id = id, text = "", author = author, kind = kind, rawContent = raw)

    @Test
    fun `turns are the messages the user sent`() {
        val usage = buildWatchUsage(
            listOf(
                message("1", author = MessageAuthor.User),
                message("2"),
                message("3"),
                message("4", author = MessageAuthor.User),
            ),
        )

        assertEquals(2, usage.turns)
    }

    @Test
    fun `steps are what the agent did rather than said`() {
        val usage = buildWatchUsage(
            listOf(
                message("1", author = MessageAuthor.User),
                message("2"),
                message("3", kind = TimelineMessageKind.Command),
                message("4", kind = TimelineMessageKind.ToolCall),
                message("5", kind = TimelineMessageKind.FileChange),
                message("6", kind = TimelineMessageKind.AgentCall),
                message("7", kind = TimelineMessageKind.Reasoning),
            ),
        )

        assertEquals(1, usage.turns)
        assertEquals(4, usage.steps)
    }

    @Test
    fun `per-call usage is summed`() {
        val usage = buildWatchUsage(
            listOf(
                message("1", raw = ASSISTANT_EVENT),
                message("2", raw = ASSISTANT_EVENT),
            ),
        )

        assertTrue(usage.reported)
        assertEquals(2_000, usage.input)
        assertEquals(400, usage.output)
        assertEquals(3_000, usage.cacheRead)
        assertEquals(1_000, usage.cacheWrite)
    }

    @Test
    fun `the runtime's own turn totals supersede the per-message counters`() {
        val usage = buildWatchUsage(
            listOf(
                message("1", raw = ASSISTANT_EVENT),
                message("2", raw = RESULT_EVENT),
            ),
        )

        assertTrue(usage.reported)
        assertEquals(12_345, usage.input)
        assertEquals(678, usage.output)
        assertEquals(90_000, usage.cacheRead)
        assertEquals(4_000, usage.cacheWrite)
        assertEquals(107_023, usage.total)
    }

    @Test
    fun `a transcript without usage reports nothing rather than zeroes`() {
        val usage = buildWatchUsage(
            listOf(
                message("1", author = MessageAuthor.User, raw = """{"text":"hello"}"""),
                message("2", raw = """{"text":"hi there"}"""),
            ),
        )

        assertFalse(usage.reported)
        assertEquals(0, usage.total)
        assertEquals(1, usage.turns)
    }

    @Test
    fun `a token-shaped word alone does not become a usage record`() {
        val usage = buildWatchUsage(
            listOf(message("1", raw = """{"text":"the token budget is fine"}""")),
        )

        assertFalse(usage.reported)
    }

    @Test
    fun `counts are compact above a thousand and exact below it`() {
        assertEquals("999", formatTokens(999))
        assertEquals("1.5K", formatTokens(1_500))
        assertEquals("12.3K", formatTokens(12_345))
        assertEquals("1.2M", formatTokens(1_240_000))
    }

    @Test
    fun `exact counts are grouped in threes`() {
        assertEquals("7", formatCount(7))
        assertEquals("999", formatCount(999))
        assertEquals("1,000", formatCount(1_000))
        assertEquals("1,234,567", formatCount(1_234_567))
    }

    private companion object {
        /** Claude Code's assistant event: usage nested under the message. */
        val ASSISTANT_EVENT = """
            {
              "type": "assistant",
              "message": {
                "role": "assistant",
                "content": [{"type": "text", "text": "ok"}],
                "usage": {
                  "input_tokens": 1000,
                  "output_tokens": 200,
                  "cache_read_input_tokens": 1500,
                  "cache_creation_input_tokens": 500
                }
              }
            }
        """.trimIndent()

        /** Claude Code's result event: the totals for the whole turn. */
        val RESULT_EVENT = """
            {
              "type": "result",
              "subtype": "success",
              "num_turns": 3,
              "total_cost_usd": 0.42,
              "usage": {
                "input_tokens": 12345,
                "output_tokens": 678,
                "cache_read_input_tokens": 90000,
                "cache_creation_input_tokens": 4000
              }
            }
        """.trimIndent()
    }
}
