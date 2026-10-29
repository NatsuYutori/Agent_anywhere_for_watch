package com.agentsanywhere.app.watch.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Round-panel geometry for the OPPO Watch X3 panel.
 *
 * The display is 466x466px at density 320, i.e. a 233dp square. Only the circle inscribed
 * in that square is visible, so a row that spans the full content width is safe only near
 * the vertical middle: the closer a control sits to the top or the bottom, the narrower it
 * has to be, otherwise the bezel cuts its corners off.
 *
 * [maxRowWidth] solves that: for a rectangle of a given height whose top edge is at [top],
 * it returns the widest width whose farthest corner still lands inside the circle (minus
 * [margin]). The pill row and the composer are sized with it, so they are never clipped
 * wherever they end up. [fitsInside] is the assertion form, used by the unit tests.
 */
internal object WatchRound {
    /** Panel edge in dp: 466px / 2 (density 320 => 1dp = 2px). */
    const val PANEL_DP = 233f

    /** Radius of the inscribed circle. */
    const val RADIUS_DP = 116.5f

    /** Breathing room so a control never visually touches the bezel. */
    const val MARGIN_DP = 5f

    /** Widest row that fits at [top] without the circle cutting its corners. */
    fun maxRowWidth(top: Dp, height: Dp, margin: Dp = MARGIN_DP.dp): Dp {
        val usableRadius = RADIUS_DP - margin.value
        val halfHeight = height.value / 2f
        val deltaY = abs(top.value + halfHeight - RADIUS_DP) + halfHeight
        val squared = usableRadius * usableRadius - deltaY * deltaY
        return if (squared <= 0f) 0.dp else (2f * sqrt(squared)).dp
    }

    /** True when a [width] x [height] rectangle whose top edge is at [top] fits the circle. */
    fun fitsInside(top: Dp, height: Dp, width: Dp, margin: Dp = MARGIN_DP.dp): Boolean {
        val usableRadius = RADIUS_DP - margin.value
        val halfWidth = width.value / 2f
        val halfHeight = height.value / 2f
        val deltaY = abs(top.value + halfHeight - RADIUS_DP) + halfHeight
        return halfWidth * halfWidth + deltaY * deltaY <= usableRadius * usableRadius
    }
}

/** Content width every page is laid out in: the panel minus the round-screen inset. */
internal val WatchSafeContentWidth = WatchRound.PANEL_DP.dp - WatchSafeHorizontal * 2

/**
 * Vertical rhythm of the sign-in page.
 *
 * Every element has an explicit height (fields and buttons are sized, the header pins its
 * line heights), so the positions below are exact rather than estimated. Two consequences
 * are deliberate:
 *
 *  - the primary button is placed while the circle is still wide, so it gets the full
 *    content width and can be tall without its corners being cut;
 *  - the two pill buttons sit below it, where the circle has narrowed, so they are sized
 *    from [WatchRound.maxRowWidth] and come out narrower on purpose.
 *
 * [trailingSpace] is the leftover room at the bottom. The screens add it as a trailing
 * spacer, which makes "scrolled fully to the bottom" land exactly on the designed
 * position - so the pill row stays inside the circle even on the longer register form.
 */
internal object WatchLoginLayout {
    val TopPadding = 6.dp
    val Gap = 6.dp

    /** title 20sp line + 2dp + subtitle 14sp line. */
    val HeaderHeight = 36.dp

    /** Natural height of [WatchTextField] (9dp padding + a 13sp line). */
    val FieldHeight = 36.dp
    val PrimaryHeight = 48.dp
    val PillRowHeight = 26.dp
    val PillGap = 8.dp

    val HeaderTop = TopPadding
    val EmailTop = HeaderTop + HeaderHeight + Gap
    val PasswordTop = EmailTop + FieldHeight + Gap
    val PrimaryTop = PasswordTop + FieldHeight + Gap
    val PillRowTop = PrimaryTop + PrimaryHeight + Gap
    val contentBottom = PillRowTop + PillRowHeight
    val trailingSpace = WatchRound.PANEL_DP.dp - contentBottom

    /** Width of the two pills together, already limited by the circle at [PillRowTop]. */
    val pillRowWidth = minOf(
        WatchSafeContentWidth,
        WatchRound.maxRowWidth(PillRowTop, PillRowHeight),
    )
    val pillWidth = (pillRowWidth - PillGap) / 2
}

/**
 * Vertical rhythm of the conversation page.
 *
 * The composer is pinned under the transcript, so its top edge follows from the bottom
 * padding instead of being measured, and its width follows from that position.
 */
internal object WatchConversationLayout {
    val TopPadding = 6.dp

    /**
     * Room left under the composer.
     *
     * This is the one number that decides how low the composer can sit, and the circle narrows
     * fast towards the bottom edge: a full-width row at [top] can be at most
     * `2 * sqrt(usableRadius^2 - deltaY^2)` wide, where `deltaY` works out to
     * `116.5 - BottomPadding` for a row pinned to the bottom. So every dp taken off here is
     * width taken off the row: 26dp gave 126.8dp of width, 20dp would give only 107.7dp. It is
     * parked at 24dp, which still leaves the input field ~89dp.
     */
    val BottomPadding = 24.dp
    val Gap = 4.dp

    /**
     * Height of the composer row.
     *
     * Shortened from 34dp to 28dp: the row is pinned by its bottom edge, so a shorter row simply
     * starts lower - the transcript above gains the 6dp, and the send button ends up square at
     * 28x28dp instead of an off-round 34x28.
     */
    val ComposerHeight = 28.dp

    /**
     * Width of the composer's action button.
     *
     * It carries a symbol rather than a word now, so it is a circle just wide enough for the
     * glyph - and every dp it gives up goes straight to the input field beside it.
     */
    val SendButtonWidth = 28.dp
    val ComposerGap = 4.dp

    val ComposerTop = WatchRound.PANEL_DP.dp - BottomPadding - ComposerHeight

    /**
     * Extra breathing room for the composer, on top of [WatchRound.MARGIN_DP].
     *
     * The composer is the one row whose far corner sits hard against the bezel, and the send
     * button is what lives in that corner. It cannot go much beyond this: at 26dp from the
     * bottom the circle is already down to 130dp of usable width at that height, and every dp
     * of margin costs width the input field needs.
     */
    val ComposerMargin = 6.dp

    val ComposerWidth = minOf(
        WatchSafeContentWidth,
        WatchRound.maxRowWidth(ComposerTop, ComposerHeight, ComposerMargin),
    )
    val ComposerFieldWidth = ComposerWidth - ComposerGap - SendButtonWidth
}
