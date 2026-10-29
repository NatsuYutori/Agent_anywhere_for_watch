package com.agentsanywhere.app.watch.ui

import androidx.compose.ui.graphics.Color
import com.agentsanywhere.app.ui.designsystem.AgentsAnywhereColors

/**
 * The watch palette: the dark scheme with every semantic colour flattened to grey.
 *
 * On a 233dp AMOLED panel colour was doing very little work. The green "running" dot, the amber
 * warning and the red error were the only hues on the screen, and between them they competed
 * with the one thing that should stand out - the action the user is meant to press. Everything
 * that used to separate by hue now separates by brightness: white for the live or primary
 * thing, mid grey for a transition, dim grey for background information.
 *
 * It is derived from whatever the shared dark theme supplies rather than edited into it, so the
 * phone client keeps its own palette and a new token added upstream inherits a sane value here.
 */
internal fun AgentsAnywhereColors.asWatchMonochrome(): AgentsAnywhereColors = copy(
    // Secondary surfaces are genuinely translucent rather than a fixed dark grey, so a pill
    // reads as a pane of glass over whatever is behind it instead of a painted rectangle.
    secondaryActionSurface = Color(0x1AFFFFFF),
    // The palette is grey on purpose, with exactly two exceptions. Green means "this runtime is
    // up and idle" and red means "something went wrong" - the only two facts on this screen that
    // are worth a colour of their own, and the only two that are invisible as shades of grey.
    errorSurface = Color(0xFF2A1214),
    errorBorder = Color(0xFF7F1D1D),
    errorText = Color(0xFFFCA5A5),
    errorIcon = Color(0xFFEF4444),
    runtimeRunning = Color(0xFFFAFAFA),
    runtimeTransitioning = Color(0xFFA3A3A3),
    runtimeActive = Color(0xFFE5E5E5),
    sessionStatusAccent = Color(0xFFFAFAFA),
    sessionStatusAccentText = Color(0xFFE5E5E5),
    noticeWarning = Color(0xFFE5E5E5),
    noticeSuccess = Color(0xFF22C55E),
)
