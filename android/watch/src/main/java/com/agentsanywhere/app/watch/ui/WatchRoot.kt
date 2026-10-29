package com.agentsanywhere.app.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.agentsanywhere.app.feature.auth.AuthSessionStore
import com.agentsanywhere.app.watch.data.WatchAuthController
import com.agentsanywhere.app.watch.ui.auth.WatchAuthScreen

/**
 * Auth gate: shows the password screen until the shared session store holds a usable
 * token, then the four-page watch shell.
 */
@Composable
fun WatchRoot(sessionStore: AuthSessionStore) {
    val scope = rememberCoroutineScope()
    val auth = remember(sessionStore) {
        WatchAuthController(sessionStore = sessionStore, scope = scope)
    }

    LaunchedEffect(auth) { auth.verifySavedSession() }

    if (auth.signedIn) {
        WatchShell(sessionStore = sessionStore, onSignOut = auth::signOut)
    } else {
        WatchAuthScreen(
            state = auth.state,
            onEmailChange = auth::updateEmail,
            onPasswordChange = auth::updatePassword,
            onDisplayNameChange = auth::updateDisplayName,
            onCodeChange = auth::updateCode,
            onServerUrlChange = auth::updateServerUrl,
            onToggleServerField = auth::toggleServerField,
            onSignIn = auth::signIn,
            onOpenRegister = auth::openRegister,
            onRequestCode = auth::requestRegisterCode,
            onRegister = auth::register,
            onBackToSignIn = auth::backToSignIn,
        )
    }
}
