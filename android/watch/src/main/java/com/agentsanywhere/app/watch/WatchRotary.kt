package com.agentsanywhere.app.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Crown (rotary) notches, counted by the activity and drained by the shell.
 *
 * The crown is deliberately not left to the view tree. On this watch it is a vertical *wheel*
 * device with a firmware velocity curve (`WheelVelocityControlParameters`, `acceleration =
 * 4.000`), so a light turn arrives as a scroll event several times its own size: any scrollable
 * that consumed it threw itself across the screen, and on a long transcript the fling was slow
 * enough to look like a freeze. The activity takes the event before the view tree, counts it
 * here, and the shell turns exactly one page per notch.
 *
 * The count is a running total rather than a stream of events on purpose. A [StateFlow] is
 * conflated: if the user spins while a page turn is still animating, the shell is handed the
 * latest total when it is free again instead of a backlog of events to replay.
 */
object WatchRotary {
    private val _notches = MutableStateFlow(0)

    /** Total notches since launch; positive is forward. Only ever compared against itself. */
    val notches: StateFlow<Int> = _notches.asStateFlow()

    /** Records one crown event. The magnitude is ignored: one event is one notch. */
    fun emit(units: Float) {
        if (units == 0f) return
        _notches.value = _notches.value + if (units > 0f) 1 else -1
    }
}
