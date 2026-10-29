package com.agentsanywhere.app.watch.ui.pages

import com.agentsanywhere.app.feature.sessiondetail.RuntimeSelectionOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchModelGroupsTest {

    private fun option(
        id: String,
        label: String,
        default: Boolean = false,
        enabled: Boolean = true,
    ) = RuntimeSelectionOption(
        selectionId = id,
        label = label,
        description = null,
        default = default,
        enabled = enabled,
    )

    @Test
    fun `variants of one model collapse into a single foldable row`() {
        val groups = buildModelGroups(
            listOf(
                option("deepseek-low", "DeepSeek V3 · low", default = true),
                option("deepseek-high", "DeepSeek V3 · high"),
            ),
        )

        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals("DeepSeek V3", group.title)
        assertTrue("a model with variants must be foldable", group.foldable)
        assertEquals(listOf("low", "high"), group.options.map { it.variantLabel() })
    }

    @Test
    fun `a model without variants is not foldable`() {
        val groups = buildModelGroups(listOf(option("claude", "Claude Sonnet 4.5")))

        val group = groups.single()
        assertFalse("a plain model has nothing to unfold", group.foldable)
        assertEquals("Claude Sonnet 4.5", group.title)
    }

    @Test
    fun `models keep the order the catalog gave them`() {
        val groups = buildModelGroups(
            listOf(
                option("a", "Alpha"),
                option("b-low", "Beta · low"),
                option("b-high", "Beta · high"),
                option("c", "Gamma"),
            ),
        )

        assertEquals(listOf("Alpha", "Beta", "Gamma"), groups.map { it.title })
        assertEquals(listOf(false, true, false), groups.map { it.foldable })
    }

    @Test
    fun `a lone variant is still foldable`() {
        // Only one effort was offered, but it is still a variant of a model: the row must show
        // the model and unfold to show "high" rather than claim the model is called that.
        val groups = buildModelGroups(listOf(option("b-high", "Beta · high")))

        val group = groups.single()
        assertTrue(group.foldable)
        assertEquals("Beta", group.title)
        assertEquals("high", group.options.single().variantLabel())
    }

    @Test
    fun `the active option follows the explicit choice and falls back to the default`() {
        val low = option("low", "DeepSeek V3 · low", default = true)
        val high = option("high", "DeepSeek V3 · high")

        assertTrue(low.isActive(selectedId = null))
        assertFalse(high.isActive(selectedId = null))
        assertTrue(high.isActive(selectedId = "high"))
        assertFalse(low.isActive(selectedId = "high"))
    }

    @Test
    fun `a label without the separator is left alone`() {
        val plain = option("x", "Just A Model")

        assertEquals("Just A Model", plain.baseLabel())
        assertEquals("Just A Model", plain.variantLabel())
    }

    @Test
    fun `a disabled variant does not make the group selectable`() {
        val groups = buildModelGroups(
            listOf(
                option("low", "Beta · low", enabled = false),
                option("high", "Beta · high"),
            ),
        )

        val group = groups.single()
        assertTrue(group.foldable)
        assertFalse("the disabled variant must stay disabled", group.options.first().enabled)
    }
}
