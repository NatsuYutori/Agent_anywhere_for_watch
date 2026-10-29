package com.agentsanywhere.app.watch

import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.TextUnit
import com.agentsanywhere.app.feature.auth.AuthSessionStore
import com.agentsanywhere.app.ui.designsystem.AAAppearanceMode
import com.agentsanywhere.app.ui.designsystem.AgentsAnywhereTheme
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.ui.WatchRoot
import com.agentsanywhere.app.watch.ui.asWatchMonochrome

/**
 * Single entry point for the watch client.
 *
 * The watch build always uses the dark scheme: the panel is AMOLED and every
 * surface is meant to be read at a glance.
 */
class WatchMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionStore = AuthSessionStore(applicationContext)
        setContent {
            AgentsAnywhereTheme(appearanceMode = AAAppearanceMode.Dark) {
                // The watch runs the shared dark scheme with its hues flattened to grey. Taken
                // from the theme that was just installed rather than rebuilt by hand, so the
                // two cannot drift apart.
                val base = LocalAAColors.current
                val monochrome = remember(base) { base.asWatchMonochrome() }
                val tightText = LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified)
                CompositionLocalProvider(
                    LocalAAColors provides monochrome,
                    // Material hands every Text the bodyLarge line box, which is 24sp. On a 233dp
                    // panel that is most of a line wasted per row - an 8sp label was measured on
                    // device as a 24dp-tall box, taller than the 14sp page title above it, and it
                    // is why so little text fitted on screen. Dropping the inherited line height
                    // lets each Text fall back to its own font's natural one; anything that wants
                    // a specific line height still sets it itself.
                    LocalTextStyle provides tightText,
                ) {
                    WatchRoot(sessionStore = sessionStore)
                }
            }
        }
    }

    /**
     * Takes the crown out of the view tree before any scrollable can see it.
     *
     * The crown on this watch is a vertical wheel device with a firmware velocity curve, so a
     * light turn arrives as a scroll event big enough to throw a list or the pager a long way.
     * The raw unit count is handed to [WatchRotary] instead and the shell decides how far that
     * moves; the event is consumed so nothing else acts on it twice.
     *
     * The log line is the calibration hook: turn the crown once, read `WatchCrown` in logcat, and
     * the source, action and per-detent unit count are all there.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val units = event.crownUnits()
        if (units != 0f) {
            Log.d(
                TAG,
                "crown units=" + units +
                    " action=" + event.action +
                    " source=0x" + Integer.toHexString(event.source) +
                    " device=" + event.deviceId +
                    " accepted=" + event.isCrownScroll(),
            )
            if (event.isCrownScroll()) {
                WatchRotary.emit(units)
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Diagnostic only: on some firmware revisions a crown arrives as key repeats instead of
     * wheel motion. These are logged and then passed on untouched, so a single turn of the crown
     * says which path the platform actually uses without changing any behaviour.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            Log.d(
                TAG,
                "crown-as-key? code=" + event.keyCode +
                    " action=" + event.action +
                    " repeat=" + event.repeatCount +
                    " device=" + event.deviceId +
                    " source=0x" + Integer.toHexString(event.source),
            )
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Any wheel motion that no finger produced is the crown.
     *
     * This deliberately does not exclude [InputDevice.SOURCE_MOUSE]. OPPO's crown reports itself
     * as a wheel-bearing pointer, and excluding pointer sources was exactly why the crown used to
     * fall through to whatever list was on screen instead of turning the page: one notch threw
     * the transcript to the end, and on a long transcript that fling read as the app freezing.
     * There is no mouse on this watch; if one is ever paired, having its wheel turn pages is a
     * fair trade for the crown working.
     */
    private fun MotionEvent.isCrownScroll(): Boolean =
        isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) ||
            !isFromSource(InputDevice.SOURCE_TOUCHSCREEN)

    private fun MotionEvent.crownUnits(): Float {
        val vertical = getAxisValue(MotionEvent.AXIS_VSCROLL)
        return if (vertical != 0f) vertical else getAxisValue(MotionEvent.AXIS_SCROLL)
    }

    private companion object {
        const val TAG = "WatchCrown"
    }
}
