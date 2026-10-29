package com.agentsanywhere.app.watch.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the round-panel layout arithmetic on the JVM, without a watch.
 *
 * The panel is 466x466px at density 320 (233dp) and only the inscribed circle is visible,
 * so "this control is fully visible" is a solvable question rather than a matter of taste.
 * These tests pin the answers for the two controls that a full-width row would have pushed
 * outside the bezel: the pill row on the sign-in page and the composer on the conversation
 * page.
 */
class WatchRoundTest {

    @Test
    fun `the panel matches the measured screen`() {
        assertEquals(233f, WatchRound.PANEL_DP, 0.001f)
        assertEquals(116.5f, WatchRound.RADIUS_DP, 0.001f)
        assertEquals(165f, WatchSafeContentWidth.value, 0.001f)
    }

    @Test
    fun `a full width row only fits near the vertical middle`() {
        assertTrue(
            "165dp across the middle of the panel must fit",
            WatchRound.fitsInside(top = 98.dp, height = 36.dp, width = WatchSafeContentWidth),
        )
        assertFalse(
            "the same row near the bottom edge must not fit",
            WatchRound.fitsInside(top = 186.dp, height = 36.dp, width = WatchSafeContentWidth),
        )
    }

    @Test
    fun `maxRowWidth narrows as a row moves away from the middle`() {
        val middle = WatchRound.maxRowWidth(top = 100.dp, height = 30.dp)
        val low = WatchRound.maxRowWidth(top = 170.dp, height = 30.dp)
        val offPanel = WatchRound.maxRowWidth(top = 232.dp, height = 26.dp)

        assertTrue("middle should allow the full content width", middle.value >= 165f)
        assertTrue("lower rows must be narrower than the middle", low.value < middle.value)
        assertEquals("a row past the bottom of the circle has no width", 0f, offPanel.value, 0.001f)
    }

    @Test
    fun `every sign in row is inside the circle`() {
        val rows = listOf(
            "email field" to Triple(WatchLoginLayout.EmailTop, WatchLoginLayout.FieldHeight, WatchSafeContentWidth),
            "password field" to Triple(WatchLoginLayout.PasswordTop, WatchLoginLayout.FieldHeight, WatchSafeContentWidth),
            "sign in button" to Triple(WatchLoginLayout.PrimaryTop, WatchLoginLayout.PrimaryHeight, WatchSafeContentWidth),
            "pill row" to Triple(WatchLoginLayout.PillRowTop, WatchLoginLayout.PillRowHeight, WatchLoginLayout.pillRowWidth),
        )
        rows.forEach { (name, row) ->
            val (top, height, width) = row
            assertTrue(
                "$name at top=${top.value}dp h=${height.value}dp w=${width.value}dp is clipped by the bezel",
                WatchRound.fitsInside(top, height, width),
            )
        }
    }

    @Test
    fun `the pill row is narrower than the content width on purpose`() {
        // A full-width pair of pills at this height would be cut off, so the row is sized
        // to the circle instead - this is the whole point of the metric.
        assertTrue(WatchLoginLayout.pillRowWidth < WatchSafeContentWidth)
        assertFalse(
            WatchRound.fitsInside(
                WatchLoginLayout.PillRowTop,
                WatchLoginLayout.PillRowHeight,
                WatchSafeContentWidth,
            ),
        )
        // Still wide enough for a two-character label plus padding.
        assertTrue("pills must stay tappable and labelled", WatchLoginLayout.pillWidth.value >= 45f)
    }

    @Test
    fun `the primary button is the largest control and keeps the full width`() {
        assertTrue(WatchLoginLayout.PrimaryHeight.value > 38f)
        assertTrue(
            WatchLoginLayout.PrimaryHeight.value > WatchLoginLayout.PillRowHeight.value,
        )
        assertTrue(
            WatchRound.fitsInside(
                WatchLoginLayout.PrimaryTop,
                WatchLoginLayout.PrimaryHeight,
                WatchSafeContentWidth,
            ),
        )
    }

    @Test
    fun `the sign in page fits without scrolling`() {
        assertTrue(
            "content ends at ${WatchLoginLayout.contentBottom.value}dp",
            WatchLoginLayout.contentBottom.value <= WatchRound.PANEL_DP,
        )
        assertEquals(
            "the trailing spacer must absorb exactly the leftover room",
            WatchRound.PANEL_DP,
            WatchLoginLayout.contentBottom.value + WatchLoginLayout.trailingSpace.value,
            0.01f,
        )
    }

    @Test
    fun `the composer is a single row that fits where it is pinned`() {
        assertTrue(
            WatchRound.fitsInside(
                WatchConversationLayout.ComposerTop,
                WatchConversationLayout.ComposerHeight,
                WatchConversationLayout.ComposerWidth,
            ),
        )
        assertTrue(
            "the field must stay usable next to the send button",
            WatchConversationLayout.ComposerFieldWidth.value >= 80f,
        )
        assertEquals(
            WatchConversationLayout.ComposerWidth.value,
            WatchConversationLayout.ComposerFieldWidth.value +
                WatchConversationLayout.ComposerGap.value +
                WatchConversationLayout.SendButtonWidth.value,
            0.01f,
        )
    }

    @Test
    fun `the composer sits above the narrow bottom of the circle`() {
        val bottom = WatchConversationLayout.ComposerTop + WatchConversationLayout.ComposerHeight
        assertTrue(
            "the composer must not reach the very bottom of the panel",
            bottom.value < WatchRound.PANEL_DP - 20f,
        )
    }

    @Test
    fun `the composer leaves room for the transcript without leaving a band under it`() {
        // Trading bottom padding for width is the whole trade-off at the bottom of the circle:
        // the field has to stay worth typing in while the row stays as low as the user asked.
        assertTrue(
            "the composer must sit low enough to use the space",
            WatchConversationLayout.ComposerTop.value > 150f,
        )
        assertTrue(
            "but not so low that its far corner leaves the circle",
            WatchRound.fitsInside(
                WatchConversationLayout.ComposerTop,
                WatchConversationLayout.ComposerHeight,
                WatchConversationLayout.ComposerWidth,
            ),
        )
    }

    @Test
    fun `the composer needs the bottom padding to stay inside the circle`() {
        // The page pins the composer with BottomPadding, and that is what puts it at
        // ComposerTop. Without it the weighted transcript pushes the row down to the content
        // edge instead, where the circle is far too narrow for it - which is exactly how the
        // send button ended up half under the bezel. Both positions are asserted together so
        // dropping that padding fails the build rather than the watch.
        val contentEdgeTop = WatchRound.PANEL_DP.dp -
            WatchConversationLayout.TopPadding -
            WatchConversationLayout.ComposerHeight

        assertFalse(
            "a composer at the content edge must be recognised as clipped",
            WatchRound.fitsInside(
                contentEdgeTop,
                WatchConversationLayout.ComposerHeight,
                WatchConversationLayout.ComposerWidth,
            ),
        )
        assertTrue(
            "and at its designed position it must fit",
            WatchRound.fitsInside(
                WatchConversationLayout.ComposerTop,
                WatchConversationLayout.ComposerHeight,
                WatchConversationLayout.ComposerWidth,
            ),
        )
    }

    @Test
    fun `the composer keeps a wider margin than every other row`() {
        assertTrue(
            "the send button lives in the far corner, so the composer keeps more clearance",
            WatchConversationLayout.ComposerMargin.value > WatchRound.MARGIN_DP,
        )
        assertTrue(
            "and that wider margin must actually narrow the row",
            WatchConversationLayout.ComposerWidth <
                WatchRound.maxRowWidth(
                    WatchConversationLayout.ComposerTop,
                    WatchConversationLayout.ComposerHeight,
                ),
        )
    }
}
