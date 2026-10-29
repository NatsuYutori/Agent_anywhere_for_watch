package com.agentsanywhere.app.watch.ui.pages

import com.agentsanywhere.app.model.AgentProject
import com.agentsanywhere.app.model.AgentSession
import com.agentsanywhere.app.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the sidebar's device labelling on the JVM.
 *
 * An account can have several working devices and two of them can own the same path, which used
 * to draw two rows that were indistinguishable right down to the string. The label that tells
 * them apart is appended to `detail`, so it is pinned here rather than discovered on the watch.
 *
 * The same tests pin the backward-compatible shape of [buildSidebarGroups]: without a device map
 * the previous call sites must get exactly what they got before.
 */
class SidebarDeviceLabelsTest {

    @Test
    fun `without a device map a directory keeps the bare path`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端", connectorId = "c1")),
            sessions = listOf(session("s1", "a")),
        )

        assertEquals("/work/a", groups[0].detail)
        assertEquals("", groups[0].deviceName)
    }

    @Test
    fun `a directory reports the device it lives on`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端", connectorId = "c1")),
            sessions = emptyList(),
            deviceNamesById = mapOf("c1" to "MacBook"),
        )

        assertEquals("/work/a · MacBook", groups[0].detail)
        assertEquals("MacBook", groups[0].deviceName)
        // The title stays the directory's own name: the device is metadata, not part of it.
        assertEquals("前端", groups[0].title)
    }

    @Test
    fun `two devices that share a working directory stop reading as one row`() {
        // The bug this exists for: ~/work on a laptop and on a desktop produced two rows with
        // the same title and the same path, and nothing on screen said which was which.
        val groups = buildSidebarGroups(
            projects = listOf(
                project("a", "", connectorId = "c1", workspacePath = "~/work"),
                project("b", "", connectorId = "c2", workspacePath = "~/work"),
            ),
            sessions = emptyList(),
            deviceNamesById = mapOf("c1" to "MacBook", "c2" to "办公室主机"),
        )

        assertEquals(2, groups.size)
        assertEquals("~/work · MacBook", groups[0].detail)
        assertEquals("~/work · 办公室主机", groups[1].detail)
        assertEquals(2, groups.map { it.detail }.toSet().size)
    }

    @Test
    fun `the device is matched by connector id, not by project id`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端", connectorId = "connector-7")),
            sessions = emptyList(),
            deviceNamesById = mapOf("connector-7" to "Pixel"),
        )

        assertEquals("Pixel", groups[0].deviceName)
    }

    @Test
    fun `a directory whose device is unknown keeps its row and gets no label`() {
        // A device can be revoked while its directories are still cached; dropping the row would
        // hide conversations the user can still read.
        val groups = buildSidebarGroups(
            projects = listOf(
                project("a", "前端", connectorId = "c1"),
                project("b", "后端", connectorId = "gone"),
            ),
            sessions = listOf(session("s1", "a")),
            deviceNamesById = mapOf("c1" to "MacBook"),
        )

        assertEquals(2, groups.size)
        assertEquals("后端", groups[1].title)
        assertEquals("/work/b", groups[1].detail)
        assertEquals("", groups[1].deviceName)
    }

    @Test
    fun `a blank device name leaves no dangling separator`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端", connectorId = "c1")),
            sessions = emptyList(),
            deviceNamesById = mapOf("c1" to "  "),
        )

        assertEquals("/work/a", groups[0].detail)
        assertEquals("", groups[0].deviceName)
    }

    @Test
    fun `the unfiled group is never labelled with a device`() {
        // It collects whatever no directory claims, which can be several devices at once.
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端", connectorId = "c1")),
            sessions = listOf(session("orphan", null)),
            deviceNamesById = mapOf("c1" to "MacBook"),
        )

        val unfiled = groups.last()
        assertEquals(SIDEBAR_UNFILED_GROUP_ID, unfiled.id)
        assertEquals("", unfiled.detail)
        assertEquals("", unfiled.deviceName)
    }

    @Test
    fun `labelling devices does not reorder the directories`() {
        val projects = listOf(
            project("a", "前端", connectorId = "c2"),
            project("b", "后端", connectorId = "c1"),
        )
        val names = mapOf("c1" to "MacBook", "c2" to "Pixel")

        val labelled = buildSidebarGroups(projects, emptyList(), names)

        assertEquals(listOf("a", "b"), labelled.map { it.id })
        // And the same order as the unlabelled call, which is the side of the code other pages
        // and the existing tests still see.
        assertEquals(labelled.map { it.id }, buildSidebarGroups(projects, emptyList()).map { it.id })
    }

    @Test
    fun `a labelled group still files its conversations under itself`() {
        val groups = buildSidebarGroups(
            projects = listOf(project("a", "前端", connectorId = "c1")),
            sessions = listOf(session("s1", "a"), session("s2", "a")),
            deviceNamesById = mapOf("c1" to "MacBook"),
        )

        assertEquals(listOf("s1", "s2"), groups[0].sessions.map { it.id })
        assertTrue(groups[0].detail.startsWith("/work/a"))
    }

    private fun project(
        id: String,
        name: String,
        connectorId: String,
        workspacePath: String = "/work/$id",
    ) = AgentProject(
        id = id,
        userId = "u1",
        connectorId = connectorId,
        name = name,
        workspacePath = workspacePath,
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
