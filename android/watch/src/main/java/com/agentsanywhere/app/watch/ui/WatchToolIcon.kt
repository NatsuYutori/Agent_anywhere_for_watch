package com.agentsanywhere.app.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import com.agentsanywhere.app.feature.sessiondetail.TimelineMessageKind
import kotlin.math.max

/**
 * The small mark that sits beside a line of agent activity.
 *
 * A transcript on a 165dp-wide column is mostly grey text, and "工具 · 调用工具" says far less
 * than a wrench does. The glyph answers "what kind of thing is happening" before the words are
 * read: a terminal for a command, a pen for a file change, a magnifier for a search.
 */
internal enum class WatchEntryGlyph {
    Command,
    Tool,
    Write,
    Read,
    Search,
    Web,
    Agent,
    Thought,
    Notice,
    Warning,
}

/**
 * Picks a glyph for a timeline entry.
 *
 * The kind decides most of it; when the runtime reports an actual tool call the tool's own name
 * is more specific than the kind, so "Bash" gets a terminal rather than a generic wrench. The
 * keys are matched as substrings because tool names are namespaced in every runtime we talk to
 * (mcp__fs__write_file, shell_exec, Read, WebFetch...).
 */
internal fun entryGlyph(kind: TimelineMessageKind, toolName: String?): WatchEntryGlyph = when (kind) {
    TimelineMessageKind.Command -> WatchEntryGlyph.Command
    TimelineMessageKind.FileChange -> WatchEntryGlyph.Write
    TimelineMessageKind.AgentCall -> WatchEntryGlyph.Agent
    TimelineMessageKind.Reasoning -> WatchEntryGlyph.Thought
    TimelineMessageKind.Diagnostic -> WatchEntryGlyph.Warning
    TimelineMessageKind.Error -> WatchEntryGlyph.Warning
    TimelineMessageKind.Artifact -> WatchEntryGlyph.Notice
    TimelineMessageKind.Marker -> WatchEntryGlyph.Notice
    TimelineMessageKind.ToolCall ->
        toolName?.takeIf(String::isNotBlank)?.let(::toolGlyph) ?: WatchEntryGlyph.Tool
    else -> WatchEntryGlyph.Notice
}

internal fun toolGlyph(toolName: String): WatchEntryGlyph {
    val name = toolName.lowercase()
    return when {
        COMMAND_KEYS.any(name::contains) -> WatchEntryGlyph.Command
        WRITE_KEYS.any(name::contains) -> WatchEntryGlyph.Write
        SEARCH_KEYS.any(name::contains) -> WatchEntryGlyph.Search
        READ_KEYS.any(name::contains) -> WatchEntryGlyph.Read
        WEB_KEYS.any(name::contains) -> WatchEntryGlyph.Web
        AGENT_KEYS.any(name::contains) -> WatchEntryGlyph.Agent
        else -> WatchEntryGlyph.Tool
    }
}

private val COMMAND_KEYS = listOf(
    "bash", "shell", "cmd", "command", "exec", "terminal", "zsh", "powershell", "script",
    "spawn", "console",
)
private val WRITE_KEYS = listOf(
    "write", "edit", "create", "patch", "apply", "replace", "insert", "notebook", "save",
    "mkdir", "touch", "rename",
)
private val SEARCH_KEYS = listOf("grep", "search", "find", "glob", "ripgrep", "locate", "query")
private val READ_KEYS = listOf("read", "view", "open", "cat", "list", "show", "get", "stat")
private val WEB_KEYS = listOf("web", "fetch", "http", "url", "browse", "curl", "download")
private val AGENT_KEYS = listOf("agent", "task", "todo", "delegate", "subagent", "plan")

/** Draws a glyph at whatever size the caller asked for; the tint comes from the row. */
@Composable
internal fun WatchEntryIcon(
    glyph: WatchEntryGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (glyph == WatchEntryGlyph.Command) {
        TerminalGlyph(tint = tint, modifier = modifier)
    } else {
        Icon(
            imageVector = glyph.vector,
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
    }
}

private val WatchEntryGlyph.vector: ImageVector
    get() = when (this) {
        // Unreachable: the terminal is drawn rather than taken from the icon set.
        WatchEntryGlyph.Command -> Icons.Default.Build
        WatchEntryGlyph.Tool -> Icons.Default.Build
        WatchEntryGlyph.Write -> Icons.Default.Edit
        WatchEntryGlyph.Read -> Icons.Default.List
        WatchEntryGlyph.Search -> Icons.Default.Search
        WatchEntryGlyph.Web -> Icons.Default.Refresh
        WatchEntryGlyph.Agent -> Icons.Default.Person
        WatchEntryGlyph.Thought -> Icons.Default.Info
        WatchEntryGlyph.Notice -> Icons.Default.Star
        WatchEntryGlyph.Warning -> Icons.Default.Warning
    }

/**
 * The one glyph the Material core icon set does not have.
 *
 * A command line is a window with a prompt in it, so an outline says it better than a solid
 * block would, and it stays readable at 11dp where a filled box with knocked-out letters would
 * turn to mud.
 */
@Composable
private fun TerminalGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = max(1.1f, w * 0.10f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.09f, h * 0.20f),
            size = Size(w * 0.82f, h * 0.60f),
            cornerRadius = CornerRadius(w * 0.15f, h * 0.15f),
            style = stroke,
        )
        val prompt = Path().apply {
            moveTo(w * 0.28f, h * 0.38f)
            lineTo(w * 0.42f, h * 0.50f)
            lineTo(w * 0.28f, h * 0.62f)
        }
        drawPath(prompt, color = tint, style = stroke)
        drawLine(
            color = tint,
            start = Offset(w * 0.52f, h * 0.62f),
            end = Offset(w * 0.72f, h * 0.62f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}
