package com.agentsanywhere.app.watch.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.agentsanywhere.app.api.ApiClient
import com.agentsanywhere.app.api.ApiException
import com.agentsanywhere.app.api.DevicesApi
import com.agentsanywhere.app.api.FilesApi
import com.agentsanywhere.app.api.RealtimeApi
import com.agentsanywhere.app.api.SessionsApi
import com.agentsanywhere.app.feature.auth.AuthSessionReader
import com.agentsanywhere.app.feature.auth.AuthSessionStore
import com.agentsanywhere.app.feature.devices.DeviceRuntime
import com.agentsanywhere.app.feature.devices.toDeviceRuntimeList
import com.agentsanywhere.app.feature.realtime.RealtimeClientIdStore
import com.agentsanywhere.app.feature.realtime.SessionRealtimeController
import com.agentsanywhere.app.feature.sessions.NewSessionCreateDraft
import com.agentsanywhere.app.feature.sessions.NewSessionCreateOutcome
import com.agentsanywhere.app.feature.sessions.NewSessionSelections
import com.agentsanywhere.app.feature.sessions.NewSessionWorkspace
import com.agentsanywhere.app.feature.sessions.activeNewSessionRuntimes
import com.agentsanywhere.app.feature.sessions.workspaceOptionsFor
import com.agentsanywhere.app.feature.sessiondetail.RuntimeSelectionOption
import com.agentsanywhere.app.feature.sessiondetail.SessionDetailController
import com.agentsanywhere.app.feature.sessiondetail.SessionDetailState
import com.agentsanywhere.app.feature.sessiondetail.SessionRuntimeStatus
import com.agentsanywhere.app.feature.sessiondetail.laterEventCursor
import com.agentsanywhere.app.feature.sessiondetail.selectionOptions
import com.agentsanywhere.app.feature.sessions.SessionsController
import com.agentsanywhere.app.feature.sessions.SessionsState
import com.agentsanywhere.app.model.AgentDevice
import com.agentsanywhere.app.model.AgentProject
import com.agentsanywhere.app.model.AgentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Scope names the runtime selection map uses (see SessionDetailScreen). */
private const val SELECTION_SCOPE_MODEL = "model"
private const val SELECTION_SCOPE_PERMISSION = "permission"

/** How long a transient status line stays on screen. */
private const val NOTICE_TIMEOUT_MILLIS = 4_000L

/**
 * The server's "the runtime is still starting" conflict.
 *
 * A runtime that is synchronizing its sessions answers the next request with a 409 whose
 * `code` is this, and whose *message* is the connector's own phase sentence — DSH says
 * ("DSH 会话同步中断，正在重试…"). Nothing the user did is wrong and the request usually
 * succeeds a few seconds later, so it must not be shown as a page failure: as red text it
 * stayed on screen until the next tap, and read as "the watch is broken".
 */
private const val RUNTIME_STARTING_CODE = "runtime_starting"

/** What the watch says instead of the connector's phase sentence. */
private const val RUNTIME_STARTING_NOTICE = "运行时正在同步，稍后自动恢复。"

/**
 * Everything the three remote-control pages share.
 *
 * The upstream [SessionsController] and [SessionDetailController] already contain the
 * whole remote-control protocol, so this holder only owns the watch's own concerns:
 * which session is selected, a single composer draft, the two selection catalogs, and
 * mapping failures onto the one-line status each page shows.
 *
 * Realtime is deliberately polling based (`refreshDomains` every few seconds while a
 * page that shows live data is visible). A watch is a remote control that is looked at
 * for seconds at a time, and polling avoids holding a websocket on a device that
 * suspends background work aggressively.
 */
/**
 * The new-session composer.
 *
 * A watch cannot host the phone's four-step wizard, so the flow is flattened into one sheet:
 * device, runtime and workspace are pills that already hold a sensible default, and the message
 * field is the only thing that needs typing. Empty [runtimes] with [runtimesLoading] false means
 * [runtimesError] explains why there is nothing to pick.
 */
data class WatchNewSessionState(
    val visible: Boolean = false,
    val deviceId: String? = null,
    val runtimes: List<DeviceRuntime> = emptyList(),
    val runtimesLoading: Boolean = false,
    val runtimesError: String? = null,
    val runtimeId: String? = null,
    val workspaces: List<NewSessionWorkspace> = emptyList(),
    val workspacePath: String? = null,
    val text: String = "",
    val creating: Boolean = false,
    val error: String? = null,
) {
    val runtime: DeviceRuntime?
        get() = runtimes.firstOrNull { it.id == runtimeId }

    val canCreate: Boolean
        get() = !creating && deviceId != null && runtime != null &&
            !workspacePath.isNullOrBlank() && text.isNotBlank()
}

class WatchSessionController(
    private val sessionsController: SessionsController,
    private val detailController: SessionDetailController,
    private val devicesApi: DevicesApi,
    private val sessionStore: AuthSessionReader,
    private val scope: CoroutineScope,
    private val onAuthExpired: () -> Unit,
    /**
     * The live event stream, when the runtime supports it.
     *
     * Upstream already implements the whole websocket protocol and `feature/realtime` is part of
     * the watch's shared source set, so streaming on the watch is the same client the phone uses;
     * leaving it null keeps the polling behaviour, which is what the tests and previews exercise.
     */
    private val realtime: SessionRealtimeController? = null,
) {
    var sessions by mutableStateOf(SessionsState())
        private set

    var detail by mutableStateOf(SessionDetailState())
        private set

    var selectedSessionId by mutableStateOf<String?>(null)
        private set

    var composerText by mutableStateOf("")
        private set

    var modelOptions by mutableStateOf<List<RuntimeSelectionOption>>(emptyList())
        private set

    var permissionOptions by mutableStateOf<List<RuntimeSelectionOption>>(emptyList())
        private set

    var sessionsLoading by mutableStateOf(false)
        private set

    var detailLoading by mutableStateOf(false)
        private set

    var actionInFlight by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var notice by mutableStateOf<String?>(null)
        private set

    /**
     * True when [error] was raised by a refresh the user did not ask for.
     *
     * Those clear themselves: the same poll succeeding is the proof that whatever broke is
     * gone. An error the user caused by tapping something stays until they act again.
     */
    private var errorFromBackground = false

    /**
     * True from the moment a message is delivered until the polled runtime status says the turn
     * is over.
     *
     * The status alone is not enough to drive the composer: it is only re-read every few
     * seconds, so after sending, the input field would stay on screen for a turn that has
     * already started. This flips the composer to the stop button immediately.
     */
    var turnInFlight by mutableStateOf(false)
        private set

    /** The live stream, and which conversation it belongs to. */
    private var realtimeJob: Job? = null
    private var realtimeSessionId: String? = null

    /**
     * Which device the sidebar is showing; null means every device of the account.
     *
     * One account can have several connectors - a laptop, a desktop, a phone - and the sidebar
     * used to merge all of them. Two machines that both keep a `~/work` project produced two rows
     * with the same name, with nothing saying which machine each belonged to and no way to narrow
     * the list, which is what "switching devices" was missing.
     */
    var deviceFilterId by mutableStateOf<String?>(null)
        private set

    /** The new-session composer; invisible until opened. */
    var newSession by mutableStateOf(WatchNewSessionState())
        private set

    val devices: List<AgentDevice>
        get() = sessions.devices

    /** Projects after the device filter, in the same order as [SessionsState.projects]. */
    val visibleProjects: List<AgentProject>
        get() = deviceFilterId
            ?.let { id -> sessions.projects.filter { it.connectorId == id } }
            ?: sessions.projects

    /** Conversations after the device filter, in the same order as [SessionsState.sessions]. */
    val visibleSessions: List<AgentSession>
        get() = deviceFilterId
            ?.let { id -> sessions.sessions.filter { it.connectorId == id } }
            ?: sessions.sessions

    fun deviceName(connectorId: String?): String? =
        connectorId?.let { id -> sessions.devices.firstOrNull { it.id == id }?.name }
            ?.takeIf(String::isNotBlank)

    /** Null (or an unknown id) means "all devices". */
    fun selectDeviceFilter(connectorId: String?) {
        deviceFilterId = connectorId?.takeIf { id -> sessions.devices.any { it.id == id } }
    }

    val selectedSession: AgentSession?
        get() = sessions.sessions.firstOrNull { it.id == selectedSessionId }

    val selectedModelId: String?
        get() = detail.runtime.selections[SELECTION_SCOPE_MODEL]

    val selectedPermissionId: String?
        get() = detail.runtime.selections[SELECTION_SCOPE_PERMISSION]

    /**
     * True while the agent is mid-turn, i.e. still producing output.
     *
     * The conversation page turns this into the stop button: the input field is only offered
     * when the runtime is idle, so a message can never be typed into a turn that is already
     * running.
     */
    val producing: Boolean
        get() = turnInFlight || detail.runtime.status == SessionRuntimeStatus.Running

    fun updateComposer(value: String) {
        composerText = value
    }

    fun clearError() {
        error = null
        errorFromBackground = false
    }

    fun clearNotice() {
        notice = null
    }

    fun refreshSessions(showSpinner: Boolean = false) {
        if (showSpinner) sessionsLoading = true
        scope.launch {
            sessionsController.loadSessions()
                .onSuccess { loaded ->
                    sessions = loaded
                    sessionsLoading = false
                    clearBackgroundError()
                    // A device that was unpaired elsewhere must not leave the sidebar filtered to
                    // something that no longer exists.
                    val filter = deviceFilterId
                    if (filter != null && loaded.devices.none { it.id == filter }) deviceFilterId = null
                    if (selectedSessionId != null && loaded.sessions.none { it.id == selectedSessionId }) {
                        // The selected conversation was archived or deleted elsewhere.
                        selectSession(loaded.sessions.firstOrNull()?.id)
                    }
                }
                .onFailure { failure ->
                    sessionsLoading = false
                    reportBackground(failure, "无法加载会话列表。")
                }
        }
    }

    fun selectSession(sessionId: String?) {
        if (sessionId == null) {
            selectedSessionId = null
            detail = SessionDetailState()
            modelOptions = emptyList()
            permissionOptions = emptyList()
            notice = null
            turnInFlight = false
            detailLoading = false
            return
        }
        if (sessionId == selectedSessionId && detail.initialized) return
        selectedSessionId = sessionId
        detail = SessionDetailState()
        modelOptions = emptyList()
        permissionOptions = emptyList()
        composerText = ""
        turnInFlight = false
        notice = null
        detailLoading = true
        scope.launch {
            detailController.loadInitialSnapshot(sessionId, sessions.devices, null)
                .onSuccess { snapshot ->
                    if (selectedSessionId == sessionId) {
                        detail = snapshot
                        markRead(sessionId)
                    }
                }
                .onFailure { failure ->
                    if (selectedSessionId == sessionId) report(failure, "无法加载该对话。")
                }
            if (selectedSessionId == sessionId) {
                detailLoading = false
                loadCatalogs(sessionId)
            }
        }
    }

    // ---- new session -------------------------------------------------------

    /**
     * Open the composer, defaulting to the device the sidebar is filtered to.
     *
     * Defaults matter more than usual here: on a watch every extra tap costs a second of
     * squinting at a 233dp panel, so the device, runtime and workspace are pre-chosen and only
     * the first message has to be typed.
     */
    fun openNewSession() {
        val deviceId = deviceFilterId
            ?: sessions.devices.firstOrNull { it.online }?.id
            ?: sessions.devices.firstOrNull()?.id
        newSession = WatchNewSessionState(visible = true, deviceId = deviceId)
        if (deviceId != null) loadNewSessionOptions(deviceId)
    }

    fun closeNewSession() {
        if (newSession.creating) return
        newSession = WatchNewSessionState()
    }

    fun selectNewSessionDevice(deviceId: String) {
        if (newSession.deviceId == deviceId) return
        newSession = newSession.copy(
            deviceId = deviceId,
            runtimeId = null,
            runtimes = emptyList(),
            runtimesError = null,
            workspaces = emptyList(),
            workspacePath = null,
            error = null,
        )
        loadNewSessionOptions(deviceId)
    }

    fun selectNewSessionRuntime(runtimeId: String) {
        newSession = newSession.copy(runtimeId = runtimeId, error = null)
    }

    fun selectNewSessionWorkspace(path: String) {
        newSession = newSession.copy(workspacePath = path, error = null)
    }

    fun updateNewSessionText(value: String) {
        newSession = newSession.copy(text = value, error = null)
    }

    private fun loadNewSessionOptions(deviceId: String) {
        // Workspaces are the directories this device already has conversations in, plus its home
        // directory - the same list the phone offers, from the same shared function.
        val workspaces = workspaceOptionsFor(
            sessions = sessions.sessions,
            deviceId = deviceId,
            homePath = null,
        )
        newSession = newSession.copy(
            runtimesLoading = true,
            runtimesError = null,
            workspaces = workspaces,
            workspacePath = newSession.workspacePath ?: workspaces.firstOrNull()?.path,
        )
        scope.launch {
            val serverUrl = sessionStore.readServerUrl()
            val accessToken = sessionStore.readAccessToken()
            if (serverUrl.isBlank() || accessToken.isBlank()) {
                newSession = newSession.copy(runtimesLoading = false, runtimesError = "先登录再新建对话。")
                return@launch
            }
            runCatching { devicesApi.listDeviceRuntimes(serverUrl, accessToken, deviceId).toDeviceRuntimeList() }
                .onSuccess { list ->
                    if (newSession.deviceId != deviceId) return@onSuccess
                    val usable = activeNewSessionRuntimes(list.runtimes)
                    newSession = newSession.copy(
                        runtimesLoading = false,
                        runtimes = usable,
                        runtimesError = when {
                            usable.isNotEmpty() -> null
                            list.runtimes.isEmpty() -> "这台设备上还没有配置运行时。"
                            else -> "这台设备上的运行时要先在手机或电脑上启动。"
                        },
                        runtimeId = newSession.runtimeId ?: usable.firstOrNull()?.id,
                    )
                }
                .onFailure { failure ->
                    if (newSession.deviceId != deviceId) return@onFailure
                    newSession = newSession.copy(
                        runtimesLoading = false,
                        runtimesError = describe(failure, "无法读取运行时列表。"),
                    )
                }
        }
    }

    /**
     * Create the session and its first turn in one call.
     *
     * The shared controller does the whole dance - resolving the directory, creating the project
     * row if the path is new, posting `create-and-start`, and reconciling a network failure that
     * may or may not have created something. The watch only has to hold the draft.
     */
    fun createNewSession() {
        val state = newSession
        if (state.creating) return
        val deviceId = state.deviceId
        val runtime = state.runtime
        val path = state.workspacePath
        val content = state.text.trim()
        val problem = when {
            deviceId == null -> "这个账号还没有可用的工作设备。"
            runtime == null -> "先选一个运行时。"
            path.isNullOrBlank() -> "先选一个工作目录。"
            content.isEmpty() -> "先写一句话再开始。"
            else -> null
        }
        if (problem != null) {
            newSession = state.copy(error = problem)
            return
        }
        newSession = state.copy(creating = true, error = null)
        scope.launch {
            val outcome = sessionsController.createAndStartSession(
                draft = NewSessionCreateDraft(
                    connectorId = deviceId!!,
                    runtime = runtime!!.type,
                    title = null,
                    cwd = path,
                    content = content,
                    selections = NewSessionSelections(),
                    attachments = emptyList(),
                    clientMessageId = UUID.randomUUID().toString(),
                    knownSessionIds = sessions.sessions.map { it.id }.toSet(),
                    runtimeId = runtime.id,
                    runtimeType = runtime.type,
                    projectId = "",
                ),
                devices = sessions.devices,
                projects = sessions.projects,
            )
            when (outcome) {
                is NewSessionCreateOutcome.Created -> {
                    newSession = WatchNewSessionState()
                    // The new conversation may not be in the cached page yet; the refresh brings it
                    // in and `selectSession` then loads its snapshot.
                    refreshSessions(showSpinner = true)
                    selectSession(outcome.session.id)
                }

                is NewSessionCreateOutcome.Failed -> {
                    outcome.refreshedState?.let { refreshed -> sessions = refreshed }
                    newSession = newSession.copy(
                        creating = false,
                        error = describe(outcome.error, "无法新建对话。"),
                    )
                }
            }
        }
    }

    /**
     * Open the live event stream for [sessionId], replacing any stream already open.
     *
     * The watch used to poll only. Polling costs a round trip every few seconds and makes a
     * producing turn arrive as a series of jumps, one per poll; the socket delivers each event as
     * the runtime emits it, which is what makes the transcript read as streaming. Polling is kept
     * as the initial snapshot and as the fallback when the socket cannot be opened (an old server,
     * or a token the ticket endpoint rejects).
     */
    fun startRealtime(sessionId: String) {
        val controller = realtime ?: return
        if (realtimeSessionId == sessionId && realtimeJob?.isActive == true) return
        stopRealtime()
        realtimeSessionId = sessionId
        realtimeJob = controller.start(
            scope = scope,
            sessionId = sessionId,
            cursor = { detail.realtime.cursor },
            onEvents = { events ->
                withContext(Dispatchers.Main.immediate) {
                    if (selectedSessionId == sessionId) {
                        detail = detailController.applyRealtimeEvents(detail, events, sessions.devices)
                    }
                }
            },
            onCursorAdvanced = { cursor ->
                withContext(Dispatchers.Main.immediate) {
                    if (selectedSessionId == sessionId) {
                        detail = detail.copy(
                            realtime = detail.realtime.copy(
                                cursor = laterEventCursor(detail.realtime.cursor, cursor),
                            ),
                        )
                    }
                }
            },
            onSnapshotRequired = {
                // A gap in the stream: the socket cannot reconstruct what was missed, so the
                // page falls back to the polled snapshot exactly as it did before streaming.
                withContext(Dispatchers.Main.immediate) {
                    if (selectedSessionId == sessionId) refreshDetail()
                }
            },
            onRuntimeRefreshRequired = { _, _ ->
                withContext(Dispatchers.Main.immediate) {
                    if (selectedSessionId == sessionId) refreshDetail()
                }
            },
            // Not a suspend callback (the transport reports it from its own thread), so the state
            // write is pushed onto this controller's scope first.
            onConnectionChanged = { connected, recovering, attempt, error ->
                scope.launch {
                    withContext(Dispatchers.Main.immediate) {
                        if (selectedSessionId == sessionId) {
                            detail = detail.copy(
                                realtime = detail.realtime.copy(
                                    connected = connected,
                                    recovering = recovering,
                                    reconnectAttempt = attempt,
                                    lastErrorMessage = error,
                                ),
                            )
                        }
                    }
                }
            },
        )
    }

    /**
     * Close the stream.
     *
     * Called when the conversation leaves the screen: a watch suspends background work
     * aggressively, and a socket that is kept open while the user is on the account page is a
     * battery cost with nothing on screen to show for it.
     */
    fun stopRealtime() {
        realtimeSessionId = null
        realtimeJob?.cancel()
        realtimeJob = null
        if (detail.realtime.connected || detail.realtime.recovering) {
            detail = detail.copy(
                realtime = detail.realtime.copy(connected = false, recovering = false, reconnectAttempt = 0),
            )
        }
    }

    /** Polled while the conversation or actions page is visible. */
    fun refreshDetail() {
        val sessionId = selectedSessionId ?: return
        if (actionInFlight || detailLoading) return
        scope.launch {
            val refreshed = detailController.refreshDomains(sessionId, sessions.devices, detail)
            if (selectedSessionId == sessionId) {
                detail = refreshed
                // The composer goes back to an input field once the turn is over.
                if (refreshed.runtime.status != SessionRuntimeStatus.Running) turnInFlight = false
            }
        }
    }

    fun loadCatalogs(sessionId: String? = null) {
        val target = sessionId ?: selectedSessionId ?: return
        scope.launch {
            detailController.loadSessionModelCatalog(target)
                .onSuccess { catalog ->
                    if (selectedSessionId == target) {
                        modelOptions = catalog.selectionOptions()
                        clearBackgroundError()
                    }
                }
                .onFailure { failure -> reportBackground(failure, "无法读取模型列表。") }
            detailController.loadSessionPermissionCatalog(target)
                .onSuccess { catalog ->
                    if (selectedSessionId == target) {
                        permissionOptions = catalog.selectionOptions()
                        clearBackgroundError()
                    }
                }
                .onFailure { failure -> reportBackground(failure, "无法读取权限列表。") }
        }
    }

    fun send() {
        val sessionId = selectedSessionId ?: return
        val content = composerText.trim()
        if (content.isEmpty()) return
        if (actionInFlight) return
        // Upstream 2.1.0 dropped the send/steer split: every message goes through sendMessage and a
        // runtime that is not accepting input answers with a refusal, which is handled below. The
        // old capability lookup could answer "not now" before the round trip; that answer is now
        // only known after sending, so the failure branch carries the same message.
        actionInFlight = true
        error = null
        scope.launch {
            val clientMessageId = UUID.randomUUID().toString()
            val result = detailController.sendMessage(sessionId, content, clientMessageId)
            if (result.isSuccess) {
                composerText = ""
                turnInFlight = true
                notify("已发送。")
                detail = detailController.refreshDomains(sessionId, sessions.devices, detail)
            } else if (isRefusal(result.exceptionOrNull())) {
                notify("运行时当前不接受新消息。")
            } else {
                report(result.exceptionOrNull(), "消息发送失败。")
            }
            actionInFlight = false
        }
    }

    fun interrupt() {
        val sessionId = selectedSessionId ?: return
        if (actionInFlight) return
        actionInFlight = true
        error = null
        scope.launch {
            val result = detailController.interrupt(sessionId)
            if (result.isSuccess) {
                notify("已请求终止当前回合。")
                detail = detailController.refreshDomains(sessionId, sessions.devices, detail)
            } else {
                report(result.exceptionOrNull(), "终止失败。")
            }
            actionInFlight = false
        }
    }

    fun setTakeover(enabled: Boolean) {
        val sessionId = selectedSessionId ?: return
        if (actionInFlight) return
        actionInFlight = true
        error = null
        scope.launch {
            val result = detailController.setTakeover(sessionId, enabled, sessions.devices)
            result
                .onSuccess { session -> detail = detail.copy(meta = detail.meta.copy(session = session)) }
            if (result.isSuccess) {
                notify(if (enabled) "已接管该会话。" else "已释放接管。")
                detail = detailController.refreshDomains(sessionId, sessions.devices, detail)
            } else {
                report(result.exceptionOrNull(), if (enabled) "接管失败。" else "释放接管失败。")
            }
            actionInFlight = false
        }
    }

    fun selectModel(selectionId: String) {
        applySelection(SELECTION_SCOPE_MODEL, selectionId, "模型已切换。")
    }

    fun selectPermission(selectionId: String) {
        applySelection(SELECTION_SCOPE_PERMISSION, selectionId, "权限已切换。")
    }

    private fun applySelection(scopeName: String, selectionId: String, successNotice: String) {
        val sessionId = selectedSessionId ?: return
        if (actionInFlight) return
        if (detail.runtime.selections[scopeName] == selectionId) return
        val selections = detail.runtime.selections.toMutableMap().apply { put(scopeName, selectionId) }
        val previousRuntime = detail.runtime
        actionInFlight = true
        error = null
        // Optimistic: the chip marks itself immediately, then the server confirms.
        detail = detail.copy(
            runtime = previousRuntime.copy(selections = selections),
            selectionUpdating = true,
        )
        scope.launch {
            val result = detailController.updateSelections(sessionId, selections)
            if (result.isSuccess) {
                if (selectedSessionId == sessionId) {
                    val observed = result.getOrNull()
                    val confirmed = observed ?: detail.runtime.copy(selections = selections)
                    detail = detail.copy(runtime = confirmed, selectionUpdating = false)
                    notify(successNotice)
                }
            } else {
                if (selectedSessionId == sessionId) {
                    detail = detail.copy(runtime = previousRuntime, selectionUpdating = false)
                }
                report(result.exceptionOrNull(), "切换失败。")
            }
            actionInFlight = false
        }
    }

    fun respondNotice(noticeId: String, actionId: String, input: Map<String, Any?>? = null) {
        val sessionId = selectedSessionId ?: return
        if (actionInFlight) return
        actionInFlight = true
        error = null
        scope.launch {
            val result = detailController.respondNotice(sessionId, noticeId, actionId, input)
            if (result.isSuccess) {
                notify("已提交审批结果。")
                detail = detailController.refreshDomains(sessionId, sessions.devices, detail)
            } else {
                report(result.exceptionOrNull(), "审批提交失败。")
            }
            actionInFlight = false
        }
    }

    private fun markRead(sessionId: String) {
        scope.launch {
            sessionsController.markSessionRead(sessionId, sessions.devices)
                .onSuccess { updated ->
                    sessions = sessions.copy(
                        sessions = sessions.sessions.map { if (it.id == updated.id) updated else it },
                    )
                }
        }
    }

    /**
     * A 4xx refusal means "not now", not "broken".
     *
     * Those are answered with the pending-send strip instead of red text; anything else
     * (network, 5xx) is a real failure and still gets the error line. 401 keeps its own
     * handling in [report].
     */
    private fun isRefusal(failure: Throwable?): Boolean =
        failure is ApiException && failure.statusCode in 400..499 && failure.statusCode != 401

    private fun report(failure: Throwable?, fallback: String) {
        if (failure is ApiException && failure.statusCode == 401) {
            error = "登录已过期，请重新登录。"
            onAuthExpired()
            return
        }
        // "Not yet" is not "broken": a syncing runtime is answered in the line that fades
        // instead of in the red strip that has to be cleared by another tap.
        if (failure.isRuntimeStarting()) {
            errorFromBackground = false
            error = null
            notify(RUNTIME_STARTING_NOTICE)
            return
        }
        errorFromBackground = false
        error = describe(failure, fallback)
    }

    /**
     * Reports a failure the user did not ask for: a poll, a catalog warm-up.
     *
     * A background failure has to expire on its own, because the user never chose it and the
     * only thing it describes is a path that is already being retried.
     */
    private fun reportBackground(failure: Throwable?, fallback: String) {
        if (failure is ApiException && failure.statusCode == 401) {
            report(failure, fallback)
            return
        }
        if (failure.isRuntimeStarting()) {
            errorFromBackground = false
            error = null
            notify(RUNTIME_STARTING_NOTICE)
            return
        }
        error = describe(failure, fallback)
        errorFromBackground = true
    }

    /** A success on the same path retires a background error; a user-facing one stays. */
    private fun clearBackgroundError() {
        if (!errorFromBackground) return
        errorFromBackground = false
        error = null
    }

    /** One-line message for a failure, without touching the page-level [error] banner. */
    private fun describe(failure: Throwable?, fallback: String): String =
        failure?.message?.takeIf(String::isNotBlank) ?: fallback

    /**
     * Shows a short-lived status line.
     *
     * The round panel has room for very little text, so a notice that stayed until the
     * next action would permanently steal a line from the transcript.
     */
    private fun notify(message: String) {
        notice = message
        scope.launch {
            delay(NOTICE_TIMEOUT_MILLIS)
            if (notice == message) notice = null
        }
    }
}

/**
 * True for the server's retryable "the runtime is still starting" conflict.
 *
 * Kept outside the class so the wire shape can be pinned by a unit test: the page decides
 * between a red strip and a fading line on exactly this distinction.
 */
internal fun Throwable?.isRuntimeStarting(): Boolean =
    this is ApiException && errorCode == RUNTIME_STARTING_CODE

/** Builds the shared controllers from one authenticated session store. */
@Composable
fun rememberWatchSessionController(
    sessionStore: AuthSessionStore,
    onAuthExpired: () -> Unit,
): WatchSessionController {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    return remember(sessionStore) {
        val reader: AuthSessionReader = sessionStore
        val client = ApiClient()
        WatchSessionController(
            sessionsController = SessionsController(
                sessionsApi = SessionsApi(client),
                devicesApi = DevicesApi(client),
                filesApi = FilesApi(client),
                sessionStore = reader,
            ),
            detailController = SessionDetailController(
                sessionsApi = SessionsApi(client),
                sessionStore = reader,
            ),
            devicesApi = DevicesApi(client),
            sessionStore = reader,
            realtime = SessionRealtimeController(
                transport = RealtimeApi(client = client),
                sessionStore = reader,
                // Identifies this watch to the server so two devices do not fight over one
                // subscription; the phone client stores it the same way.
                clientId = RealtimeClientIdStore(context).readOrCreate(),
            ),
            scope = scope,
            onAuthExpired = onAuthExpired,
        )
    }
}
