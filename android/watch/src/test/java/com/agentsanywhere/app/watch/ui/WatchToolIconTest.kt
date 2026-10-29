package com.agentsanywhere.app.watch.ui

import com.agentsanywhere.app.feature.sessiondetail.TimelineMessageKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchToolIconTest {

    @Test
    fun `a shell call gets the terminal`() {
        assertEquals(WatchEntryGlyph.Command, toolGlyph("Bash"))
        assertEquals(WatchEntryGlyph.Command, toolGlyph("shell_exec"))
        assertEquals(WatchEntryGlyph.Command, toolGlyph("mcp__terminal__run"))
    }

    @Test
    fun `writing and editing get the pen`() {
        assertEquals(WatchEntryGlyph.Write, toolGlyph("mcp__fs__write_file"))
        assertEquals(WatchEntryGlyph.Write, toolGlyph("Edit"))
        assertEquals(WatchEntryGlyph.Write, toolGlyph("MultiEdit"))
        assertEquals(WatchEntryGlyph.Write, toolGlyph("apply_patch"))
    }

    @Test
    fun `reading, searching and fetching all look different`() {
        assertEquals(WatchEntryGlyph.Read, toolGlyph("Read"))
        assertEquals(WatchEntryGlyph.Search, toolGlyph("Grep"))
        assertEquals(WatchEntryGlyph.Search, toolGlyph("glob"))
        assertEquals(WatchEntryGlyph.Web, toolGlyph("WebFetch"))
    }

    @Test
    fun `a tool we have never heard of still gets a tool icon`() {
        assertEquals(WatchEntryGlyph.Tool, toolGlyph("some_vendor_thing"))
        assertEquals(WatchEntryGlyph.Tool, toolGlyph(""))
    }

    @Test
    fun `the kind decides when there is no tool name`() {
        assertEquals(WatchEntryGlyph.Command, entryGlyph(TimelineMessageKind.Command, null))
        assertEquals(WatchEntryGlyph.Write, entryGlyph(TimelineMessageKind.FileChange, null))
        assertEquals(WatchEntryGlyph.Agent, entryGlyph(TimelineMessageKind.AgentCall, null))
        assertEquals(WatchEntryGlyph.Warning, entryGlyph(TimelineMessageKind.Error, null))
        assertEquals(WatchEntryGlyph.Thought, entryGlyph(TimelineMessageKind.Reasoning, null))
    }

    @Test
    fun `a tool call prefers the tool name over the generic kind`() {
        assertEquals(
            WatchEntryGlyph.Write,
            entryGlyph(TimelineMessageKind.ToolCall, "mcp__fs__write_file"),
        )
        assertEquals(WatchEntryGlyph.Tool, entryGlyph(TimelineMessageKind.ToolCall, null))
        assertEquals(WatchEntryGlyph.Tool, entryGlyph(TimelineMessageKind.ToolCall, "  "))
    }
}
