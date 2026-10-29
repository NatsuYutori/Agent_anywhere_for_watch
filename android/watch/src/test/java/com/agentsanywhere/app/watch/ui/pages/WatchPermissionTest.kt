package com.agentsanywhere.app.watch.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Permission labels on the watch.
 *
 * Runtimes hand these back in English or as a bare id, and which one arrives varies by runtime,
 * so both are matched and both are normalised. An unrecognised mode has to survive as itself:
 * showing the wrong permission on a security control would be worse than showing an English one.
 */
class WatchPermissionTest {

    @Test
    fun `english display names are translated`() {
        assertEquals("只读", watchPermission("Read Only", "").label)
        assertEquals("仅工作区可写", watchPermission("Workspace Write", "").label)
        assertEquals("完全访问", watchPermission("Full Access", "").label)
        assertEquals("每次询问", watchPermission("Request Approval", "").label)
        assertEquals("自动审查", watchPermission("Auto Review", "").label)
    }

    @Test
    fun `ids are translated`() {
        assertEquals("只读", watchPermission("", "read-only").label)
        assertEquals("完全访问", watchPermission("", "fullAccess").label)
        assertEquals("自动接受编辑", watchPermission("", "acceptEdits").label)
        assertEquals("计划模式", watchPermission("", "plan").label)
    }

    @Test
    fun `an unknown mode keeps its own name`() {
        val permission = watchPermission("Turbo Mode", "turbo")

        assertEquals("Turbo Mode", permission.label)
    }

    @Test
    fun `read only is not mistaken for workspace write`() {
        val readOnly = watchPermission("Read Only", "read-only")
        val write = watchPermission("Workspace Write", "workspace-write")

        assertNotEquals(readOnly.label, write.label)
        assertNotEquals(readOnly.icon, write.icon)
    }

    @Test
    fun `dangerous modes are marked apart from safe ones`() {
        val dangerous = watchPermission("Full Access", "full-access")
        val safe = watchPermission("Read Only", "read-only")

        assertNotEquals(dangerous.icon, safe.icon)
    }
}
