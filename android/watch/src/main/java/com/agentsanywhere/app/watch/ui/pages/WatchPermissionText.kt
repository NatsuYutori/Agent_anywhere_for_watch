package com.agentsanywhere.app.watch.ui.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What a permission row shows on the watch.
 *
 * The runtime hands these back in English ("Read Only", "Workspace Write", ...) or as a bare id,
 * and the phone client leaves them that way. On a watch the row is also the only place with room
 * for a glyph, so each mode gets one: a lock for read-only, a pen for "may edit files", a
 * warning for "no limits".
 */
internal data class WatchPermission(
    val label: String,
    val icon: ImageVector,
)

/**
 * Matches on the id as well as the label because runtimes differ in which one they fill in, and
 * normalises away case and punctuation so `read-only`, `Read Only` and `readOnly` all land on
 * the same row. An unknown mode keeps its own name rather than being mislabelled.
 */
internal fun watchPermission(label: String, selectionId: String): WatchPermission {
    val keys = listOf(selectionId, label).map(::normalize)
    keys.forEach { key ->
        PERMISSIONS.forEach { (needles, permission) ->
            if (needles.any(key::contains)) return permission
        }
    }
    return WatchPermission(label = label, icon = Icons.Default.Lock)
}

private fun normalize(raw: String): String =
    raw.lowercase().filter(Char::isLetterOrDigit)

private val PERMISSIONS: List<Pair<List<String>, WatchPermission>> = listOf(
    // Order matters: "readonly" must win before the broader "write" key can match it.
    listOf("readonly", "read") to WatchPermission("只读", Icons.Default.Lock),
    listOf("workspacewrite", "workspace") to
        WatchPermission("仅工作区可写", Icons.Default.Edit),
    listOf("fullaccess", "bypass", "dangerous") to
        WatchPermission("完全访问", Icons.Default.Warning),
    listOf("requestapproval", "askeverytime", "ask") to
        WatchPermission("每次询问", Icons.Default.Person),
    listOf("autoreview", "review", "autoapprove") to
        WatchPermission("自动审查", Icons.Default.Search),
    listOf("acceptEdits", "acceptedits") to WatchPermission("自动接受编辑", Icons.Default.Check),
    listOf("plan") to WatchPermission("计划模式", Icons.Default.List),
    listOf("dontask") to WatchPermission("不再询问", Icons.Default.Check),
    listOf("auto") to WatchPermission("自动", Icons.Default.PlayArrow),
    listOf("default") to WatchPermission("默认", Icons.Default.Settings),
    listOf("sandbox", "restricted") to WatchPermission("受限沙箱", Icons.Default.Lock),
    listOf("build", "test") to WatchPermission("可执行命令", Icons.Default.Build),
    listOf("info", "custom") to WatchPermission("自定义", Icons.Default.Info),
    listOf("refresh", "reload") to WatchPermission("重新读取", Icons.Default.Refresh),
)
