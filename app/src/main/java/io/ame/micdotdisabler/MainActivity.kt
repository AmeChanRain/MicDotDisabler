package io.ame.micdotdisabler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.ame.micdotdisabler.shizuku.ShizukuManager
import io.ame.micdotdisabler.ui.AppState
import io.ame.micdotdisabler.ui.components.ConfettiOverlay
import io.ame.micdotdisabler.ui.main.MainScreen
import io.ame.micdotdisabler.ui.setup.SetupScreen
import io.ame.micdotdisabler.ui.theme.MicDotDisablerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var shizukuManager: ShizukuManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        shizukuManager = ShizukuManager(this)

        setContent {
            MicDotDisablerTheme {
                MicDotDisablerApp(shizukuManager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shizukuManager.register()
        // Re-evaluate state on return (user may have started Shizuku while away)
        shizukuManager.onStateChanged?.invoke(shizukuManager.evaluateState())
    }

    override fun onPause() {
        super.onPause()
        shizukuManager.unregister()
    }
}

// ──────────────────────────────────────────
// Root composable
// ──────────────────────────────────────────

@Composable
private fun MicDotDisablerApp(manager: ShizukuManager) {
    var appState by remember { mutableStateOf(manager.evaluateState()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Wire up the state callback
    remember {
        manager.onStateChanged = { newState ->
            appState = newState
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Crossfade(
            targetState = shouldShowSetup(appState),
            animationSpec = tween(durationMillis = 300),
            label = "screen_crossfade"
        ) { showSetup ->
            if (showSetup) {
                SetupScreen(
                    state = appState,
                    onCheckAgain = {
                        appState = manager.evaluateState()
                    },
                    onOpenShizuku = {
                        val intent = manager.openShizukuAppIntent()
                        if (intent != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(manager.openShizukuPlayStoreIntent())
                        }
                    },
                    onOpenPlayStore = {
                        context.startActivity(manager.openShizukuPlayStoreIntent())
                    },
                    onOpenWirelessDebugging = {
                        try {
                            context.startActivity(manager.openWirelessDebuggingIntent())
                        } catch (_: Exception) {
                            // Some devices don't expose wireless debugging directly;
                            // silently ignore and let the user navigate manually.
                        }
                    },
                    onRequestPermission = {
                        manager.requestPermission()
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                MainScreen(
                    state = appState,
                    onDisableMicDot = {
                        scope.launch {
                            appState = AppState.Executing
                            val result = withContext(Dispatchers.IO) {
                                manager.disableMicDot()
                            }
                            appState = if (result.isSuccess) {
                                AppState.Success
                            } else {
                                AppState.Error(result.stdout.ifEmpty {
                                    "Exit code ${result.exitCode}\n${result.stderr}"
                                })
                            }
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

/**
 * Returns true if the app should show the setup/onboarding screen.
 * Only switch to the main screen when Shizuku is fully ready.
 */
private fun shouldShowSetup(state: AppState): Boolean {
    return when (state) {
        AppState.ShizukuNotInstalled,
        AppState.ShizukuNotRunning,
        AppState.PermissionRequired -> true
        AppState.Ready,
        AppState.Executing,
        is AppState.Success,
        is AppState.Error -> false
    }
}
