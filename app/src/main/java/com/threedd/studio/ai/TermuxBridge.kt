package com.threedd.studio.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real Android-to-Termux bridge.
 *
 * Android cannot start another app's service freely, but Termux documents an external command
 * interface: an intent to com.termux.app.RunCommandService carrying a script to run. That is
 * what this uses - there is no fake "start server" button anywhere.
 *
 * Every precondition is checked and reported separately, because a single generic failure is
 * useless when diagnosing this: Termux not installed, the RUN_COMMAND permission not granted,
 * external apps not enabled by the user, or the service refusing the request.
 */
@Singleton
class TermuxBridge @Inject constructor(@ApplicationContext private val context: Context) {

    sealed interface Result {
        data class Delivered(val detail: String) : Result
        data class Refused(val reason: Reason, val detail: String, val fix: String) : Result

        enum class Reason { TERMUX_MISSING, PERMISSION_MISSING, EXTERNAL_APPS_DISABLED, SERVICE_REFUSED }
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
        private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
    }

    fun termuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun permissionGranted(): Boolean = runCatching {
        context.checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** The user-facing steps for whatever is currently missing, or null when nothing is. */
    fun blocker(): Result.Refused? {
        if (!termuxInstalled()) {
            return Result.Refused(
                Result.Reason.TERMUX_MISSING,
                "Termux is not installed",
                "Install Termux from F-Droid (the Play build is out of date), then open it once."
            )
        }
        if (!permissionGranted()) {
            return Result.Refused(
                Result.Reason.PERMISSION_MISSING,
                "The com.termux.permission.RUN_COMMAND permission is not granted",
                "Reinstall or update the app so the permission is registered, then grant it."
            )
        }
        return null
    }

    /**
     * Runs [script] inside Termux. [background] detaches it so the server survives the session.
     * Returns Delivered as soon as Termux accepts the request - delivery is not the same as
     * the server being up, and callers must still health check.
     */
    fun runScript(script: String, background: Boolean = true): Result {
        blocker()?.let { return it }

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_PATH, TERMUX_BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", script))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, background)
            putExtra(EXTRA_SESSION_ACTION, "0")
        }

        return try {
            context.startService(intent)
            Result.Delivered("Sent the command to Termux")
        } catch (t: Throwable) {
            // Termux returns nothing useful when allow-external-apps is off, so this is the
            // most common cause and worth naming explicitly.
            Log.w(TAG, "RUN_COMMAND refused", t)
            Result.Refused(
                Result.Reason.EXTERNAL_APPS_DISABLED,
                "Termux refused the command (${t.message ?: t::class.java.simpleName})",
                "In Termux run: mkdir -p ~/.termux && echo 'allow-external-apps=true' >> " +
                    "~/.termux/termux.properties && termux-reload-settings"
            )
        }
    }

    /** Starts Ollama if it is not already answering. Health is still the caller's job. */
    fun startOllama(port: Int = 11434): Result {
        val script = buildString {
            append("command -v ollama >/dev/null 2>&1 || { echo 'ollama not installed'; exit 127; }; ")
            append("if ! curl -s -m 2 http://127.0.0.1:$port/v1/models >/dev/null 2>&1; then ")
            append("nohup ollama serve > ~/ollama.log 2>&1 & ")
            append("fi; ")
            append("echo started")
        }
        return runScript(script, background = true)
    }

    /** Starts a llama.cpp server for [modelPath]. */
    fun startLlamaServer(modelPath: String, port: Int = 8088): Result {
        val script = buildString {
            append("command -v llama-server >/dev/null 2>&1 || { echo 'llama-server not installed'; exit 127; }; ")
            append("if ! curl -s -m 2 http://127.0.0.1:$port/v1/models >/dev/null 2>&1; then ")
            append("nohup llama-server -m ").append(modelPath)
            append(" --host 127.0.0.1 --port $port -c 2048 -ngl 0 -ngld -1 ")
            append("> ~/llama-server.log 2>&1 & ")
            append("fi; ")
            append("echo started")
        }
        return runScript(script, background = true)
    }

    private const val TAG = "3DoubleD-Termux"
}
