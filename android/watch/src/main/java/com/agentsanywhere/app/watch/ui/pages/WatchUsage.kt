package com.agentsanywhere.app.watch.ui.pages

import com.agentsanywhere.app.feature.sessiondetail.MessageAuthor
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessage
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessageKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * How much work a conversation has cost, as far as the transcript can tell.
 *
 * Two of the three numbers are counted straight off the loaded transcript and need no runtime
 * support: a *turn* is a message the user sent, and a *step* is anything the agent did on the
 * side while answering (a command, a tool call, a file change, a sub-agent). Those are exact for
 * the part of the conversation the watch has loaded.
 *
 * Tokens are different. Nothing in the client API carries usage: the server does not model it,
 * the connector does not store it, and the phone client does not read it. What the watch *does*
 * have is the raw content of every timeline item, which is the runtime's own event, so the
 * counters are read out of that when the runtime puts them there - which Claude Code does for
 * every assistant message and, as a total, for the result event of a turn.
 *
 * [reported] says whether anything was found, so the page can admit it has no numbers rather than
 * print a row of zeroes that looks like real spending.
 */
internal data class WatchUsage(
    val turns: Int = 0,
    val steps: Int = 0,
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val reported: Boolean = false,
) {
    val total: Long get() = input + output + cacheRead + cacheWrite
}

private val STEP_KINDS = setOf(
    TimelineMessageKind.Command,
    TimelineMessageKind.ToolCall,
    TimelineMessageKind.FileChange,
    TimelineMessageKind.AgentCall,
)

/** Everything the agent did rather than said. */
private fun TimelineMessage.isStep(): Boolean = kind in STEP_KINDS

internal fun buildWatchUsage(messages: List<TimelineMessage>): WatchUsage {
    var turns = 0
    var steps = 0
    var summed = TokenCounts()
    var totals: TokenCounts? = null

    messages.forEach { message ->
        if (message.author == MessageAuthor.User) turns++
        if (message.isStep()) steps++
        usageRecords(message.rawContent).forEach { record ->
            // A record that carries the runtime's own turn totals supersedes the per-message
            // counters, because it already includes them.
            if (record.authoritative) totals = record.counts else summed += record.counts
        }
    }

    val tokens = totals ?: summed
    return WatchUsage(
        turns = turns,
        steps = steps,
        input = tokens.input,
        output = tokens.output,
        cacheRead = tokens.cacheRead,
        cacheWrite = tokens.cacheWrite,
        reported = totals != null || summed.any,
    )
}

/** Compact token count: watches have room for three or four glyphs, not seven digits. */
internal fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> trim(value / 1_000_000.0) + "M"
    value >= 1_000 -> trim(value / 1_000.0) + "K"
    else -> value.toString()
}

/** Exact count with thousands separators, for the expanded detail rows. */
internal fun formatCount(value: Long): String {
    val text = value.toString()
    if (text.length <= 3) return text
    val builder = StringBuilder()
    text.reversed().forEachIndexed { index, char ->
        if (index > 0 && index % 3 == 0) builder.append(',')
        builder.append(char)
    }
    return builder.reverse().toString()
}

private fun trim(value: Double): String {
    val rounded = Math.round(value * 10).toDouble() / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private data class TokenCounts(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
) {
    val any: Boolean get() = input != 0L || output != 0L || cacheRead != 0L || cacheWrite != 0L

    operator fun plus(other: TokenCounts) = TokenCounts(
        input = input + other.input,
        output = output + other.output,
        cacheRead = cacheRead + other.cacheRead,
        cacheWrite = cacheWrite + other.cacheWrite,
    )
}

private data class UsageRecord(val counts: TokenCounts, val authoritative: Boolean)

/** Keys Claude Code (and the runtimes that copy it) use for the same four counters. */
private val INPUT_KEYS = listOf("input_tokens", "inputTokens", "prompt_tokens", "promptTokens")
private val OUTPUT_KEYS =
    listOf("output_tokens", "outputTokens", "completion_tokens", "completionTokens")
private val CACHE_READ_KEYS =
    listOf("cache_read_input_tokens", "cacheReadInputTokens", "cached_tokens", "cacheReadTokens")
private val CACHE_WRITE_KEYS =
    listOf("cache_creation_input_tokens", "cacheCreationInputTokens", "cacheWriteTokens")

/**
 * Every usage object inside one timeline item's raw content.
 *
 * The content is the runtime's event, and runtimes nest usage differently - under `usage`, under
 * `message.usage`, beside `total_cost_usd` on a result event - so the whole tree is walked rather
 * than one path being assumed. The walk is depth limited: these documents are small, and a
 * bounded search cannot be turned into a stall by an unexpected payload.
 */
private fun usageRecords(raw: String): List<UsageRecord> {
    if (raw.isBlank()) return emptyList()
    // Cheap gate: most items (reasoning, file diffs, plain text) mention no tokens at all, and
    // this runs over the whole transcript on every refresh.
    if (!raw.contains("token", ignoreCase = true)) return emptyList()
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    val authoritativeRoot = root.has("num_turns") ||
        root.has("total_cost_usd") ||
        root.optString("subtype") == "success"
    val records = mutableListOf<UsageRecord>()
    collectUsage(root, depth = 0, authoritative = authoritativeRoot, into = records)
    return records
}

private fun collectUsage(
    node: JSONObject,
    depth: Int,
    authoritative: Boolean,
    into: MutableList<UsageRecord>,
) {
    if (depth > 4) return
    val counts = countsOf(node)
    if (counts != null) into += UsageRecord(counts, authoritative)
    // org.json hands back an Iterator rather than a collection, hence the explicit loop.
    val keys = node.keys()
    while (keys.hasNext()) {
        when (val child = node.opt(keys.next())) {
            is JSONObject -> collectUsage(child, depth + 1, authoritative, into)
            is JSONArray -> {
                for (index in 0 until child.length()) {
                    (child.opt(index) as? JSONObject)?.let {
                        collectUsage(it, depth + 1, authoritative, into)
                    }
                }
            }
        }
    }
}

private fun countsOf(node: JSONObject): TokenCounts? {
    val input = node.firstLong(INPUT_KEYS)
    val output = node.firstLong(OUTPUT_KEYS)
    val cacheRead = node.firstLong(CACHE_READ_KEYS)
    val cacheWrite = node.firstLong(CACHE_WRITE_KEYS)
    if (input == null && output == null && cacheRead == null && cacheWrite == null) return null
    return TokenCounts(
        input = input ?: 0,
        output = output ?: 0,
        cacheRead = cacheRead ?: 0,
        cacheWrite = cacheWrite ?: 0,
    )
}

private fun JSONObject.firstLong(keys: List<String>): Long? {
    keys.forEach { key ->
        if (has(key) && !isNull(key)) {
            val value = optLong(key, -1L)
            if (value >= 0L) return value
        }
    }
    return null
}
