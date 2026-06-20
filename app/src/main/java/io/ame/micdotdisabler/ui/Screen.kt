package io.ame.micdotdisabler.ui

/**
 * Represents the current application state, driving which screen
 * is displayed and what the user sees.
 */
sealed interface AppState {

    /** Shizuku is not installed on this device. */
    data object ShizukuNotInstalled : AppState

    /** Shizuku is installed but its binder is not alive (service not running). */
    data object ShizukuNotRunning : AppState

    /** Shizuku is running but this app has not been granted permission yet. */
    data object PermissionRequired : AppState

    /** Shizuku is running and permission is granted — ready to execute commands. */
    data object Ready : AppState

    /** A command is currently being executed (loading state). */
    data object Executing : AppState

    /** The mic-dot disable command completed successfully. */
    data object Success : AppState

    /** An error occurred during command execution. */
    data class Error(val message: String) : AppState
}
