package com.agentsanywhere.app.watch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The crown counter the shell turns into page steps.
 *
 * What matters is that the firmware's velocity curve cannot leak through: the crown on this
 * watch reports a *wheel* axis, so a light turn can arrive as a large number, and treating that
 * number as distance is what used to throw the pager across three pages. One event is one notch
 * here, whatever it weighs.
 */
class WatchRotaryTest {

    @Test
    fun `one event is one notch regardless of its magnitude`() {
        val start = WatchRotary.notches.value

        WatchRotary.emit(4.5f)
        WatchRotary.emit(0.25f)
        WatchRotary.emit(15f)

        assertEquals(start + 3, WatchRotary.notches.value)
    }

    @Test
    fun `direction comes from the sign`() {
        val start = WatchRotary.notches.value

        WatchRotary.emit(-1f)
        WatchRotary.emit(-9f)

        assertEquals(start - 2, WatchRotary.notches.value)
    }

    @Test
    fun `a zero axis is not a notch`() {
        val start = WatchRotary.notches.value

        WatchRotary.emit(0f)

        assertEquals(start, WatchRotary.notches.value)
    }
}
