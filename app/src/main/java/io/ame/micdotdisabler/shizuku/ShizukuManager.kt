package io.ame.micdotdisabler.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.ame.micdotdisabler.ui.AppState
import rikka.shizuku.Shizuku

/**
 * Encapsulates all Shizuku interaction logic.
 *
 * Responsibilities:
 * - Check whether Shizuku is installed, running, and authorized
 * - Request Shizuku permission from the user
 * - Execute privileged shell commands via [Shizuku.newProcess]
 * - Emit state changes through [onStateChanged] callback
 */
class ShizukuManager(private val context: Context) {

    var onStateChanged: ((AppState) -> Unit)? = null
    private var lastState: AppState? = null

    // ── Binder lifecycle ─────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        notifyState(evaluateState())
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        notifyState(AppState.ShizukuNotRunning)
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) {
                notifyState(evaluateState())
            }
        }

    // ── State evaluation ─────────────────────

    fun evaluateState(): AppState {
        return when {
            !isShizukuInstalled() -> AppState.ShizukuNotInstalled
            !isShizukuRunning() -> AppState.ShizukuNotRunning
            !hasShizukuPermission() -> AppState.PermissionRequired
            else -> AppState.Ready
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    private fun hasShizukuPermission(): Boolean {
        if (Shizuku.isPreV11()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    // ── Permission ───────────────────────────

    fun requestPermission() {
        if (Shizuku.isPreV11()) return
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    // ── Command execution ────────────────────

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * Execute a shell command via Shizuku [Shizuku.newProcess].
     *
     * The process runs with ADB shell (UID 2000) or root (UID 0) identity.
     * Call this from a background thread — it blocks on process I/O.
     */
    fun execCommand(command: String): CommandResult {
        checkBinder()

        val process = Shizuku.newProcess(
            arrayOf("sh", "-c", command),
            null,
            null
        )

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        return CommandResult(exitCode, stdout.trim(), stderr.trim())
    }

    /**
     * Execute the two commands that disable the microphone privacy indicator.
     *
     * Order rationale: on some Android 14+ devices, the sync-disabled flag
     * must be set BEFORE writing the protected config value.
     */
    fun disableMicDot(): CommandResult {
        val sb = StringBuilder()

        // Step 1: disable config sync first (required on some devices)
        val rSync = execCommand(CMD_DISABLE_SYNC)
        sb.appendLine("> $CMD_DISABLE_SYNC")
        sb.appendLine("exit=${rSync.exitCode}")
        if (rSync.stdout.isNotEmpty()) sb.appendLine(rSync.stdout)
        if (rSync.stderr.isNotEmpty()) sb.appendLine(rSync.stderr)

        // Step 2: set the flag
        val rFlag = execCommand(CMD_DISABLE_MIC_DOT)
        sb.appendLine("> $CMD_DISABLE_MIC_DOT")
        sb.appendLine("exit=${rFlag.exitCode}")
        if (rFlag.stdout.isNotEmpty()) sb.appendLine(rFlag.stdout)
        if (rFlag.stderr.isNotEmpty()) sb.appendLine(rFlag.stderr)

        // Detect known failure modes
        if (!rFlag.isSuccess) {
            val detail = when {
                isAllowlistError(rFlag.stderr) -> ALLOWLIST_ERROR_GUIDANCE
                else -> ""
            }
            if (detail.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine(detail)
            }
            return CommandResult(rFlag.exitCode, sb.toString(), "")
        }

        return CommandResult(
            exitCode = 0,
            stdout = sb.toString(),
            stderr = ""
        )
    }

    /**
     * Check whether the command output indicates an allowlist-protected flag
     * that cannot be modified via shell UID on this device.
     */
    private fun isAllowlistError(stderr: String): Boolean {
        return stderr.contains("allowlist", ignoreCase = true) ||
               stderr.contains("must add flag", ignoreCase = true)
    }

    // ── Navigation helpers ───────────────────

    fun openShizukuPlayStoreIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$SHIZUKU_PACKAGE")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openShizukuAppIntent(): Intent? {
        return context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    }

    fun openWirelessDebuggingIntent(): Intent {
        return Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ── Lifecycle ────────────────────────────

    fun register() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    fun unregister() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    // ── Internal ─────────────────────────────

    private fun checkBinder() {
        if (!isShizukuRunning()) throw IllegalStateException("Shizuku binder is not alive")
        if (!hasShizukuPermission()) throw SecurityException("Shizuku permission not granted")
    }

    private fun notifyState(state: AppState) {
        if (state != lastState) {
            lastState = state
            onStateChanged?.invoke(state)
        }
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST_CODE = 1000

        private const val CMD_DISABLE_MIC_DOT =
            "cmd device_config put privacy camera_mic_icons_enabled false default"
        private const val CMD_DISABLE_SYNC =
            "cmd device_config set_sync_disabled_for_tests persistent"

        private const val ALLOWLIST_ERROR_GUIDANCE =
            "NOTE: This device enforces a write-allowlist for the " +
            "'camera_mic_icons_enabled' flag (Android 14+ stock/Pixel firmware). " +
            "The flag cannot be modified via ADB shell UID. " +
            "Root access or an LSPosed module (e.g. GreenDotHide) is required on this device. " +
            "This app successfully works on Android 12–13 and many OEM ROMs " +
            "(Samsung OneUI, Xiaomi HyperOS) where the allowlist is not enforced."
    }
}
