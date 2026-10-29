package com.agentsanywhere.app.watch.ui.pages

import com.agentsanywhere.app.feature.sessiondetail.RuntimeSelectionOption

/**
 * A model row on the watch.
 *
 * The shared catalog lists every *variant* as its own option, so a runtime that offers a
 * reasoning effort arrives as "DeepSeek V3 · low" and "DeepSeek V3 · high". On a 165dp column
 * that is two rows of near-identical text and no hint that they are the same model. They are
 * folded back into the model they belong to; a model with no variants is [foldable] = false and
 * is simply tapped, not unfolded.
 */
internal data class WatchModelGroup(
    val title: String,
    val options: List<RuntimeSelectionOption>,
    val foldable: Boolean,
)

/** The separator the shared catalog joins a model name and a variant with. */
private const val VARIANT_SEPARATOR = " · "

internal fun buildModelGroups(options: List<RuntimeSelectionOption>): List<WatchModelGroup> {
    val grouped = LinkedHashMap<String, MutableList<RuntimeSelectionOption>>()
    options.forEach { option ->
        val title = option.baseLabel()
        grouped.getOrPut(title) { mutableListOf() }.add(option)
    }
    return grouped.map { (title, items) ->
        // One option whose label carries no variant is a plain model, not a group of one.
        val foldable = items.size > 1 || items.first().variantLabel() != items.first().baseLabel()
        WatchModelGroup(title = title, options = items, foldable = foldable)
    }
}

/** "DeepSeek V3 · high" -> "DeepSeek V3"; a label without a variant comes back unchanged. */
internal fun RuntimeSelectionOption.baseLabel(): String =
    label.substringBefore(VARIANT_SEPARATOR).trim().ifBlank { label.trim() }

/** "DeepSeek V3 · high" -> "high"; the full label when there is no variant to show. */
internal fun RuntimeSelectionOption.variantLabel(): String {
    val parts = label.split(VARIANT_SEPARATOR)
    return if (parts.size > 1) {
        parts.drop(1).joinToString(VARIANT_SEPARATOR).trim()
    } else {
        label.trim()
    }
}

/** Whether this option is the one on screen: the explicit choice, or the default until one is made. */
internal fun RuntimeSelectionOption.isActive(selectedId: String?): Boolean =
    selectionId == selectedId || (selectedId == null && default)
