package com.agentsanywhere.app.watch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agentsanywhere.app.api.AuthApi
import com.agentsanywhere.app.feature.auth.AuthController
import com.agentsanywhere.app.feature.auth.AuthSessionStore
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.WatchRotary
import com.agentsanywhere.app.watch.data.WatchAccountController
import com.agentsanywhere.app.watch.data.rememberWatchSessionController
import com.agentsanywhere.app.watch.ui.pages.WatchAccountPage
import com.agentsanywhere.app.watch.ui.pages.WatchActionsPage
import com.agentsanywhere.app.watch.ui.pages.WatchConversationPage
import com.agentsanywhere.app.watch.ui.pages.WatchSidebarPage
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The four screens of the watch client. */
enum class WatchPage {
    Account,
    Actions,
    Conversation,
    Sidebar,
}

/**
 * Physical left-to-right page order.
 *
 * Confirmed product brief: the conversation is the landing page, the sidebar sits to its
 * RIGHT (drag the finger right to reach it) and the runtime actions sit to its LEFT
 * (drag the finger left), with the account page one more page to the left.
 *
 * Swiping right always reveals the previous entry in this list, so mirroring every
 * swipe direction is a matter of reversing this one list. [WATCH_LANDING_PAGE] follows
 * automatically.
 */
val WatchPageOrder = listOf(
    WatchPage.Sidebar,
    WatchPage.Conversation,
    WatchPage.Actions,
    WatchPage.Account,
)

/** The conversation is the page the app lands on. */
val WATCH_LANDING_PAGE = WatchPageOrder.indexOf(WatchPage.Conversation)

/** How often the visible live page re-reads the session from the server. */
private const val LIVE_REFRESH_MILLIS = 3_000L

/** Top padding of the page-indicator strip, and the strip's total height. */
private val DotsTopPadding = 5.dp

/**
 * Space reserved above the pager for the page indicator.
 *
 * The indicator used to live *inside* each page, which meant it slid away with the page and
 * sat at a different height on every screen. It is now drawn once by the shell in this strip
 * and the pages start below it.
 */
private val WatchTopInset = 18.dp

/**
 * How far one crown notch moves whatever page is on screen.
 *
 * A notch is a deliberate click, not a flick, so it moves about an eighth of a panel - roughly
 * two lines of transcript. The step is fixed on purpose: scaling it by the reported axis is what
 * made the crown unusable, because the firmware already multiplies a light turn several times.
 */
private val CROWN_STEP_DP = 30.dp

/**
 * Most notches one turn may queue.
 *
 * The rest are dropped. A hard spin should scroll quickly, not keep running long after the finger
 * stopped, and this is the only place where that can be bounded.
 */
private const val CROWN_MAX_QUEUED_STEPS = 3

/**
 * How long one queued notch takes to glide.
 *
 * Short enough that the page tracks the crown, long enough that the step does not read as a jump.
 * A batch of notches is animated as a single tween of `steps * this`, so speed stays constant
 * whether the crown was nudged once or spun.
 */
private const val CROWN_ANIM_MS_PER_STEP = 55

/**
 * Horizontal inset that keeps content inside the round panel.
 *
 * Derived from an inscribed square on the 466x466 panel: (466 - 466/sqrt(2)) / 2 is
 * about 68px, which is 34dp at density 320. Re-derive from the measured `wm density`
 * if the panel reports a different density.
 */
val WatchSafeHorizontal = 34.dp

@Composable
fun WatchShell(
    sessionStore: AuthSessionStore,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = LocalAAColors.current
    val sessions = rememberWatchSessionController(sessionStore = sessionStore, onAuthExpired = onSignOut)
    val account = remember(sessionStore) {
        WatchAccountController(
            auth = AuthController(api = AuthApi(), sessionStore = sessionStore),
            sessionStore = sessionStore,
            scope = scope,
        )
    }
    val pagerState = rememberPagerState(initialPage = WATCH_LANDING_PAGE) { WatchPageOrder.size }
    val currentPage = pagerState.currentPage

    // One scroller per page, hoisted to the shell so the scroll arc can read whichever page is
    // on screen. The pages themselves are unchanged by this.
    val transcriptState = rememberLazyListState()
    val sidebarScroll = rememberScrollState()
    val actionsScroll = rememberScrollState()
    val accountScroll = rememberScrollState()
    val newSessionScroll = rememberScrollState()

    LaunchedEffect(Unit) {
        sessions.refreshSessions(showSpinner = true)
        account.load()
    }

    // Poll only while a page that shows live runtime data is on screen.
    LaunchedEffect(currentPage, sessions.selectedSessionId) {
        val page = WatchPageOrder.getOrNull(currentPage)
        if (page != WatchPage.Conversation && page != WatchPage.Actions) return@LaunchedEffect
        while (true) {
            delay(LIVE_REFRESH_MILLIS)
            sessions.refreshDetail()
        }
    }

    // The live stream, on the same lifetime as the polling above but only for the conversation
    // itself: the actions page reads catalogs and the approval card, which are polled state and
    // do not stream. Polling carries on either way - it is the snapshot the stream starts from,
    // and the fallback when the socket cannot be opened.
    LaunchedEffect(currentPage, sessions.selectedSessionId) {
        val sessionId = sessions.selectedSessionId
        if (WatchPageOrder.getOrNull(currentPage) == WatchPage.Conversation && sessionId != null) {
            sessions.startRealtime(sessionId)
        } else {
            sessions.stopRealtime()
        }
    }
    DisposableEffect(Unit) {
        onDispose { sessions.stopRealtime() }
    }

    LaunchedEffect(currentPage) {
        if (WatchPageOrder.getOrNull(currentPage) == WatchPage.Sidebar) sessions.refreshSessions()
    }

    // Crown: one notch, one small step down the page that is on screen.
    //
    // The crown is a wheel device with a firmware velocity curve (`acceleration = 4.000`), so the
    // reported axis is several times the physical turn; following it literally is what used to
    // fling a long transcript from top to bottom. Only its direction is used here - the distance
    // is fixed - and the crown never changes pages.
    //
    // Smoothness comes from animating a whole batch as one linear glide rather than each notch as
    // its own spring: a spring per 30dp step accelerates and settles again, so a quick spin read
    // as a series of lurches. A linear tween at a fixed speed per step reads as one movement.
    val crownStep = with(LocalDensity.current) { CROWN_STEP_DP.toPx() }
    LaunchedEffect(pagerState) {
        var consumed = 0
        WatchRotary.notches.collect { total ->
            val steps = (total - consumed).coerceIn(-CROWN_MAX_QUEUED_STEPS, CROWN_MAX_QUEUED_STEPS)
            if (steps == 0) return@collect
            consumed = total
            // Turning the crown walks *down* the page. The panel reports the raw axis the other
            // way round, so the sign is flipped here rather than in WatchRotary: the counter is a
            // plain tally of notches and should stay readable on its own.
            val distance = -steps * crownStep
            val spec = tween<Float>(
                durationMillis = CROWN_ANIM_MS_PER_STEP * abs(steps),
                easing = LinearEasing,
            )
            try {
                // The new-session sheet sits on top of whatever page is showing, so it owns the
                // crown while it is open; scrolling the page underneath it would be invisible.
                if (sessions.newSession.visible) {
                    newSessionScroll.animateScrollBy(distance, spec)
                } else {
                    when (WatchPageOrder.getOrNull(pagerState.currentPage)) {
                        WatchPage.Conversation -> transcriptState.animateScrollBy(distance, spec)
                        WatchPage.Sidebar -> sidebarScroll.animateScrollBy(distance, spec)
                        WatchPage.Actions -> actionsScroll.animateScrollBy(distance, spec)
                        WatchPage.Account -> accountScroll.animateScrollBy(distance, spec)
                        null -> Unit
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // A failed scroll must not take the crown down with it.
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.canvas)) {
        HorizontalPager(
            state = pagerState,
            // Pages start below the indicator strip, so the reserved space is not drawn over.
            modifier = Modifier.fillMaxSize().padding(top = WatchTopInset),
            // A swipe while the sheet is open would change the page behind it, and the user would
            // find a different page waiting after dismissing it.
            userScrollEnabled = !sessions.newSession.visible,
        ) { index ->
            when (WatchPageOrder[index]) {
                WatchPage.Account -> WatchAccountPage(
                    state = account.state,
                    scrollState = accountScroll,
                    serverUrl = account.serverUrl,
                    userId = account.userId,
                    onReload = account::load,
                    onBeginEditName = account::beginEditDisplayName,
                    onDraftNameChange = account::updateDraftDisplayName,
                    onSaveName = account::saveDisplayName,
                    onCancelEditName = account::cancelEditDisplayName,
                    onBeginBindEmail = account::beginBindEmail,
                    onBindEmailChange = account::updateBindEmail,
                    onBindCodeChange = account::updateBindCode,
                    onRequestBindCode = account::requestBindCode,
                    onConfirmBind = account::confirmBindEmail,
                    onCancelBind = account::cancelBindEmail,
                    onSignOut = onSignOut,
                )

                WatchPage.Actions -> WatchActionsPage(
                    detail = sessions.detail,
                    modelOptions = sessions.modelOptions,
                    permissionOptions = sessions.permissionOptions,
                    selectedModelId = sessions.selectedModelId,
                    selectedPermissionId = sessions.selectedPermissionId,
                    busy = sessions.actionInFlight,
                    error = sessions.error,
                    notice = sessions.notice,
                    scrollState = actionsScroll,
                    onTakeover = sessions::setTakeover,
                    onSelectModel = sessions::selectModel,
                    onSelectPermission = sessions::selectPermission,
                    onRespondNotice = sessions::respondNotice,
                    onReloadCatalogs = { sessions.loadCatalogs() },
                )

                WatchPage.Conversation -> WatchConversationPage(
                    detail = sessions.detail,
                    loading = sessions.detailLoading,
                    composerText = sessions.composerText,
                    busy = sessions.actionInFlight,
                    working = sessions.producing,
                    error = sessions.error,
                    notice = sessions.notice,
                    listState = transcriptState,
                    onComposerChange = sessions::updateComposer,
                    onSend = sessions::send,
                    onStop = sessions::interrupt,
                )

                WatchPage.Sidebar -> WatchSidebarPage(
                    devices = sessions.devices,
                    deviceFilterId = sessions.deviceFilterId,
                    projects = sessions.visibleProjects,
                    sessions = sessions.visibleSessions,
                    errorMessage = sessions.sessions.errorMessage,
                    scrollState = sidebarScroll,
                    selectedSessionId = sessions.selectedSessionId,
                    loading = sessions.sessionsLoading,
                    onSelectDevice = sessions::selectDeviceFilter,
                    onNewSession = sessions::openNewSession,
                    onReload = { sessions.refreshSessions(showSpinner = true) },
                    onOpenSession = { sessionId ->
                        sessions.selectSession(sessionId)
                        val conversation = WatchPageOrder.indexOf(WatchPage.Conversation)
                        scope.launch { pagerState.animateScrollToPage(conversation) }
                    },
                )
            }
        }

        // Drawn by the shell, above the pager: a page change animates the dot in place and
        // sliding between pages can never move it off screen.
        WatchPageDots(
            current = pagerState.currentPage,
            count = WatchPageOrder.size,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = DotsTopPadding),
        )

        // The composer is an overlay rather than a fifth page. Creating a session starts on the
        // workspace page, and a page would have to be swiped away to abandon it; a sheet is one
        // tap to dismiss and cannot be reached by accident.
        AnimatedVisibility(
            visible = sessions.newSession.visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            WatchNewSessionSheet(
                state = sessions.newSession,
                scrollState = newSessionScroll,
                devices = sessions.devices,
                deviceName = sessions::deviceName,
                onSelectDevice = sessions::selectNewSessionDevice,
                onSelectRuntime = sessions::selectNewSessionRuntime,
                onSelectWorkspace = sessions::selectNewSessionWorkspace,
                onTextChange = sessions::updateNewSessionText,
                onCreate = sessions::createNewSession,
                onDismiss = sessions::closeNewSession,
            )
        }
    }
}
