package com.threedd.studio.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The studio's assistant boundary.
 *
 * [LocalRuleAdvisor] is always available and costs nothing: no model, no key, no network. The
 * local Llama provider talks to a llama.cpp server on the device loopback (the same
 * OpenAI-compatible endpoint the Aura/Grok-Girls projects use), which is free and offline.
 * [AiRouter] prefers the model when it is reachable and silently uses the rules otherwise, so
 * an answer is always produced.
 */
interface AiAdvisor {
    val id: String
    suspend fun available(): Boolean
    suspend fun ask(question: String, context: String): String?
}

/**
 * Deterministic, offline advisor. It reasons over the diagnostic text with explicit rules, so
 * there is never a charge and never an outage. This is the floor the app always has.
 */
class LocalRuleAdvisor @Inject constructor() : AiAdvisor {

    override val id: String = "rules"

    override suspend fun available(): Boolean = true

    override suspend fun ask(question: String, context: String): String? {
        val q = question.lowercase()
        val c = context.lowercase()
        return when {
            "import" in q || "glb" in q || "gltf" in q ->
                "Imports may fail when a .gltf points at a missing .bin, when an accessor declares " +
                    "an unsupported component type, or when the file is not really glTF. Re-import; the " +
                    "repair pass retries leniently and skips unreadable primitives."

            "render" in q || "blank" in q || "black" in q || "nothing" in q || "显示" in q ->
                "Open Settings > 3D engine. If it says 'simplified fallback', the driver rejected the " +
                    "PBR shader and the app is shading with a factors-only material. If the backend is " +
                    "NOOP, the device gave no graphics context."

            "photo" in q || "jpg" in q || "webp" in q || "image" in q ->
                "Photos without a separable background fall back to a centred oval mask. A plainer, " +
                    "higher contrast background produces a far better silhouette."

            "scan" in q ->
                "Capture 8-24 frames against a plain background. Fewer than 8 frames cannot carve a " +
                    "volume; more than 24 mostly costs time."

            "hair" in q || "tattoo" in q || "outfit" in q || "augment" in q ->
                "Wearables are generated from the loaded rig's proportions. If a model has unusual " +
                    "proportions, parts scale with it, so they stay attached."

            "slow" in q || "lag" in q || "performance" in q ->
                "Settings > Render quality controls MSAA and shadow map size. Battery mode is the " +
                    "cheapest. The per-frame morph rebuild only runs when weights actually change."

            else -> if (c.isBlank()) null else
                "Diagnostics: ${c.take(280)}"
        }
    }
}

/**
 * Free, on-device model access over an OpenAI-compatible loopback endpoint
 * (llama.cpp server), matching the provider used by the existing Aura projects.
 */
class LocalLlamaAdvisor @Inject constructor(@ApplicationContext private val context: Context) : AiAdvisor {

    override val id: String = "local-llama"

    private var endpoint: String = DEFAULT_ENDPOINT
    private var model: String = DEFAULT_MODEL

    fun configure(endpoint: String, model: String) {
        if (endpoint.isNotBlank()) this.endpoint = endpoint.trimEnd('/')
        if (model.isNotBlank()) this.model = model
    }

    override suspend fun available(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("$endpoint/v1/models").openConnection() as HttpURLConnection).apply {
                connectTimeout = 1200
                readTimeout = 2500
                requestMethod = "GET"
            }
            val code = connection.responseCode
            connection.disconnect()
            code == 200
        }.getOrDefault(false)
    }

    override suspend fun ask(question: String, context: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("model", model)
                put("max_tokens", 220)
                put("temperature", 0.2)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    put(JSONObject().put("role", "user").put("content", "$question\n\nContext:\n${context.take(2000)}"))
                })
            }.toString()

            val connection = (URL("$endpoint/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 90_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode != 200) {
                connection.disconnect()
                return@runCatching null
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            JSONObject(text)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
                ?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:11434"
        const val DEFAULT_MODEL = "llama3.2:1b"
        const val SYSTEM_PROMPT =
            "You are the diagnostics assistant inside an Android 3D avatar studio. " +
                "Answer in at most four sentences. Be concrete and mention which screen or setting to use. " +
                "If the context shows a repeated failure, name the repair that fixed it before."
    }
}

/** Picks an advisor: the on-device model when reachable, otherwise the rules. Never returns null. */
@Singleton
class AiRouter @Inject constructor(
    private val rules: LocalRuleAdvisor,
    private val local: LocalLlamaAdvisor
) {
    @Volatile private var lastUsed: String = "rules"

    val lastProvider: String get() = lastUsed

    suspend fun ask(question: String, context: String, preferModel: Boolean = true): String? {
        if (preferModel && local.available()) {
            local.ask(question, context)?.let {
                lastUsed = local.id
                return it
            }
        }
        lastUsed = rules.id
        return rules.ask(question, context)
    }

    suspend fun configureLocal(endpoint: String, model: String) = local.configure(endpoint, model)
    suspend fun localAvailable(): Boolean = local.available()
}
