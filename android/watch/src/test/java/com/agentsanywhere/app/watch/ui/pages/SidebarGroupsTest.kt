package com.agentsanywhere.app.watch.ui.pages

import com.agentsanywhere.app.model.AgentProject
import com.agentsanywhere.app.model.AgentSession
import com.agentsanywhere.app.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the sidebar's grouping rules on the JVM.
 *
 * The sidebar is an accordion: a directory is a collapsed row and its conversations appear
 * directly underneath it. That is only correct if every conversation is filed under the right
 * directory and the row order is stable across the 3s poll, so both are pinned here.
 */
class SidebarGroupsTest {

    @Test
    fun `every conversation lands under its own directory`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端"), project("b", "后端")),
            sessions = listOf(
                session("s1", "a"),
                session("s2", "b"),
                session("s3", "a"),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(listOf("s1", "s3"), groups[0].sessions.map { it.id })
        assertEquals(listOf("s2"), groups[1].sessions.map { it.id })
    }

    @Test
    fun `the row order follows the project order, not the poll order`() {
        // Polls hand the sessions back in whatever order the server used; the sidebar must not
        // reshuffle under the finger because of it.
        val first = buildSidebarGroups(
            projects = listOf(project("a", "前端"), project("b", "后端")),
            sessions = listOf(session("s1", "a"), session("s2", "b")),
        )
        val second = buildSidebarGroups(
            projects = listOf(project("a", "前端"), project("b", "后端")),
            sessions = listOf(session("s2", "b"), session("s1", "a")),
        )

        assertEquals(listOf("a", "b"), first.map { it.id })
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `conversations that no directory claims collect in one trailing group`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端")),
            sessions = listOf(
                session("s1", "a"),
                session("orphan", null),
                session("stranger", "deleted-project"),
            ),
        )

        assertEquals(2, groups.size)
        val unfiled = groups.last()
        assertEquals(SIDEBAR_UNFILED_GROUP_ID, unfiled.id)
        assertEquals("未归属目录", unfiled.title)
        assertEquals(listOf("orphan", "stranger"), unfiled.sessions.map { it.id })
    }

    @Test
    fun `no stray group appears when everything is filed`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端"), project("b", "后端")),
            sessions = listOf(session("s1", "a"), session("s2", "b")),
        )

        assertTrue(
            "an empty unfiled group would add a row nobody can use",
            groups.none { it.id == SIDEBAR_UNFILED_GROUP_ID },
        )
    }

    @Test
    fun `an empty directory still gets a row`() {
        // It has to be tappable so the user can open it and read "这个目录下还没有对话".
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端"), project("b", "后端")),
            sessions = listOf(session("s1", "a")),
        )

        assertEquals(2, groups.size)
        assertEquals(emptyList<String>(), groups[1].sessions.map { it.id })
    }

    @Test
    fun `a directory with no name falls back to its path`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", ""), project("b", "后端")),
            sessions = emptyList(),
        )

        assertEquals("/work/a", groups[0].title)
        assertEquals("/work/a", groups[0].detail)
        assertEquals("后端", groups[1].title)
    }

    private fun project(id: String, name: String) = AgentProject(
        id = id,
        userId = "u1",
        connectorId = "c1",
        name = name,
        workspacePath = "/work/$id",
        pinned = false,
        pinnedAt = null,
        activeSessionCount = 0,
        lastActivityAt = null,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun session(id: String, projectId: String?) = AgentSession(
        id = id,
        connectorId = "c1",
        projectId = projectId,
        deviceName = "Mac",
        title = "会话 $id",
        summary = "",
        cwd = "/work/x",
        workspaceLabel = "/work/x",
        runtime = "claude",
        runtimeLabel = "Claude Code",
        status = SessionStatus.Idle,
        statusLabel = "空闲",
        updatedAtLabel = "刚刚",
        metaLabel = "",
        pinned = false,
        archived = false,
        unread = false,
        lastReadSeq = 0,
        takeover = false,
        connectorOnline = true,
        live = false,
        sortKey = id,
        updatedSeq = 0,
    )
}
