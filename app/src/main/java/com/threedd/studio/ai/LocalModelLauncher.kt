package com.threedd.studio.ai

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the local llama.cpp server by itself, so the assistant does not depend on the user
 * setting anything up first.
 *
 * It works through the possibilities in order and reports exactly where it got to:
 *  1. a server already answering on the loopback port is adopted;
 *  2. a `llama-server` binary is looked for in the app's own storage, /data/local/tmp and the
 *     Termux prefix, together with a `.gguf` model;
 *  3. if both are present it launches the process and waits for the health endpoint.
 *
 * Android 10 and later forbid executing files written into an app's data directory (the W^X
 * rule), so step 3 can legitimately be refused by the platform. When that happens the status
 * says so, names the reason, and the Diagnostics screen offers the exact command to run the
 * server yourself - it never pretends the model is running when it is not.
 */
@Singleton
class LocalModelLauncher @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Phase { NOT_STARTED, PROBING, RUNNING, STARTING, NO_BINARY, NO_MODEL, EXEC_BLOCKED, FAILED, STOPPED }

    data class Status(
        val phase: Phase = Phase.NOT_STARTED,
        val detail: String = "Not checked yet",
        val endpoint: String = DEFAULT_ENDPOINT,
        val binaryPath: String? = null,
        val modelPath: String? = null,
        val pid: Long? = null
    ) {
        val running: Boolean get() = phase == Phase.RUNNING
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var process: Process? = null

    private val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }
    private val binDir: File get() = File(context.filesDir, "bin").apply { mkdirs() }

    fun setEndpoint(endpoint: String) {
        _status.value = _status.value.copy(endpoint = endpoint.trimEnd('/'))
    }

    /** Adopts a server that is already listening, or says so if nothing is there. */
    suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        _status.value = _status.value.copy(phase = Phase.PROBING, detail = "Checking ${_status.value.endpoint}")
        val alive = healthy(_status.value.endpoint)
        _status.value = if (alive) {
            _status.value.copy(phase = Phase.RUNNING, detail = "A local model server is already answering")
        } else {
            _status.value.copy(phase = Phase.NOT_STARTED, detail = "No server on ${_status.value.endpoint}")
        }
        alive
    }

    /** The autonomous path: find, launch, wait for health. */
    suspend fun start(): Status = withContext(Dispatchers.IO) {
        if (healthy(_status.value.endpoint)) {
            _status.value = _status.value.copy(phase = Phase.RUNNING, detail = "A local model server is already answering")
            return@withContext _status.value
        }

        val binary = findBinary()
        if (binary == null) {
            _status.value = _status.value.copy(
                phase = Phase.NO_BINARY,
                detail = "No llama-server binary found. Put one at ${binDir.absolutePath}/llama-server " +
                    "or run it yourself (see the command below)."
            )
            return@withContext _status.value
        }
        val model = findModel()
        if (model == null) {
            _status.value = _status.value.copy(
                phase = Phase.NO_MODEL,
                binaryPath = binary.absolutePath,
                detail = "Found ${binary.name} but no .gguf model. Import one to ${modelsDir.absolutePath}."
            )
            return@withContext _status.value
        }

        _status.value = _status.value.copy(
            phase = Phase.STARTING,
            binaryPath = binary.absolutePath,
            modelPath = model.absolutePath,
            detail = "Launching ${binary.name} with ${model.name}"
        )

        val failure: Result<Unit> = runCatching {
            val builder = ProcessBuilder(
                binary.absolutePath,
                "-m", model.absolutePath,
                "--host", "127.0.0.1",
                "--port", portOf(_status.value.endpoint)
            )
            builder.redirectErrorStream(true)
            builder.directory(context.filesDir)
            process = builder.start()
        }

        failure.onFailure { error: Throwable ->
            val blocked = error is SecurityException ||
                (error.message?.contains("permission", true) == true) ||
                (error.message?.contains("exec", true) == true)
            _status.value = _status.value.copy(
                phase = if (blocked) Phase.EXEC_BLOCKED else Phase.FAILED,
                detail = if (blocked)
                    "Android refused to execute a binary from app storage (the post-Android-10 W^X rule). " +
                        "Run the server from Termux instead; the command is below."
                else "Launch failed: ${error.message ?: error::class.java.simpleName}"
            )
        }

        if (_status.value.phase != Phase.STARTING) return@withContext _status.value

        // wait for health
        repeat(HEALTH_ATTEMPTS) {
            delay(HEALTH_INTERVAL_MS)
            if (healthy(_status.value.endpoint)) {
                _status.value = _status.value.copy(
                    phase = Phase.RUNNING,
                    detail = "Local model server running on ${_status.value.endpoint}"
                )
                return@withContext _status.value
            }
        }
        _status.value = _status.value.copy(
            phase = Phase.FAILED,
            detail = "The server started but never became healthy on ${_status.value.endpoint}."
        )
        _status.value
    }

    fun stop(): Status {
        runCatching { process?.destroy() }
        process = null
        _status.value = _status.value.copy(phase = Phase.STOPPED, detail = "Server stopped")
        return _status.value
    }

    /** Copies a user-picked .gguf into app storage so the launcher can find it. */
    suspend fun importModel(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryName(uri) ?: "model-${System.currentTimeMillis()}.gguf"
            val target = File(modelsDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            } ?: return@runCatching null
            target.takeIf { it.length() > 0 }
        }.getOrNull()
    }

    fun installedModels(): List<File> =
        modelsDir.listFiles { f -> f.extension.equals("gguf", true) }?.sortedBy { it.name } ?: emptyList()

    /**
     * Copies a model into public Downloads so another app can read it.
     *
     * This is the step that makes the manual route work at all: the app's own files directory
     * is private to its UID, so Termux cannot open a model stored there. Downloads is shared.
     */
    fun exportModelToDownloads(file: File): String? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/3DoubleD")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching null
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: return@runCatching null
        "/sdcard/Download/3DoubleD/${file.name}"
    }.getOrNull()

    /**
     * The command to run in Termux when the platform blocks the app from launching the server.
     * [sharedPath] must point at the copy in Downloads, not at app-private storage.
     */
    fun manualCommand(sharedPath: String? = null): String {
        val model = sharedPath
            ?: _status.value.modelPath?.takeIf { it.startsWith("/sdcard") || it.startsWith("/storage") }
            ?: "/sdcard/Download/3DoubleD/your-model.gguf"
        val port = portOf(_status.value.endpoint)
        // -ngl 0: no GPU layers, phones have no usable backend.
        // -ngld -1: leave the speculative draft model unset; a fresh llama.cpp build asserts
        //   "n_gpu_layers_draft < 0" during argument parsing if this is left at its default.
        return "llama-server -m $model --host 127.0.0.1 --port $port -c 2048 -ngl 0 -ngld -1"
    }

    private fun findBinary(): File? {
        val candidates = listOf(
            File(binDir, "llama-server"),
            File(context.filesDir, "llama-server"),
            File("/data/local/tmp/llama-server"),
            File("/data/data/com.termux/files/usr/bin/llama-server"),
            File("/system/bin/llama-server")
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }
            ?: candidates.firstOrNull { it.exists() }
    }

    private fun findModel(): File? {
        val searchDirs = listOf(
            modelsDir,
            File("/data/local/tmp"),
            File("/sdcard/Download"),
            File("/sdcard/Models")
        )
        searchDirs.forEach { dir ->
            dir.listFiles { f -> f.extension.equals("gguf", true) }?.firstOrNull()?.let { return it }
        }
        return null
    }

    private fun healthy(endpoint: String): Boolean = runCatching {
        val connection = (URL("$endpoint/v1/models").openConnection() as HttpURLConnection).apply {
            connectTimeout = 1200
            readTimeout = 2500
            requestMethod = "GET"
        }
        val code = connection.responseCode
        connection.disconnect()
        code == 200
    }.getOrDefault(false)

    private fun portOf(endpoint: String): String =
        endpoint.substringAfterLast(':', "8088").substringBefore('/')

    private fun queryName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8088"
        private const val HEALTH_ATTEMPTS = 20
        private const val HEALTH_INTERVAL_MS = 900L
    }
}
