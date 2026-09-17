package com.threedd.studio.repair

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the operations that can fail - imports, model loads, renderer setup - and repairs
 * them instead of just reporting.
 *
 * A [Repair] is a concrete action, not advice: clear the generated parts, rebuild the material,
 * re-copy the file. When one of them makes the retry succeed, the pairing of failure signature
 * and repair is written to disk, so the next time that same failure appears the fix that worked
 * is tried first. That is what the app learns: which recovery works, for which failure.
 */
@Singleton
class RepairSupervisor @Inject constructor(@ApplicationContext private val context: Context) {

    /** A concrete, executable recovery. Returning false means "not applicable". */
    interface Repair {
        val id: String
        val description: String
        fun apply(): Boolean
    }

    data class Failure(
        val stage: String,
        val signature: String,
        val message: String,
        val at: Long,
        val repairedBy: String?
    ) {
        val repaired: Boolean get() = repairedBy != null
    }

    data class State(
        val failures: List<Failure> = emptyList(),
        val learned: Map<String, String> = emptyMap(),
        val repairsApplied: Int = 0
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val learned = ConcurrentHashMap<String, String>()
    private val history = ArrayDeque<Failure>()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        loadLearned()
        publish()
    }

    /** Normalises an error into a stable key, so transient detail does not split the learning. */
    fun signatureOf(stage: String, error: String): String {
        val cleaned = error
            .lowercase()
            .replace(Regex("[0-9]+"), "#")
            .replace(Regex("[^a-z# ]"), "")
            .trim()
            .take(80)
        return "$stage|$cleaned"
    }

    /**
     * Runs [block]. On failure, applies candidate repairs in the order the app has learned
     * works, retrying after each. Returns null when nothing recovered.
     */
    fun <T> attempt(stage: String, repairs: List<Repair>, block: () -> T): T? {
        return try {
            block()
        } catch (t: Throwable) {
            val message = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
            val signature = signatureOf(stage, message)
            for (repair in order(signature, repairs)) {
                val applied = runCatching { repair.apply() }.getOrDefault(false)
                if (!applied) continue
                val retried = runCatching { block() }.getOrNull()
                if (retried != null) {
                    learn(signature, repair.id)
                    record(Failure(stage, signature, message, System.currentTimeMillis(), repair.id))
                    return retried
                }
            }
            record(Failure(stage, signature, message, System.currentTimeMillis(), null))
            null
        }
    }

    /** Suspending variant, for operations that do I/O. */
    suspend fun <T> attemptSuspend(stage: String, repairs: List<Repair>, block: suspend () -> T): T? {
        return try {
            block()
        } catch (t: Throwable) {
            val message = t.message?.takeIf { it.isNotBlank() } ?: t::class.java.simpleName
            val signature = signatureOf(stage, message)
            for (repair in order(signature, repairs)) {
                val applied = runCatching { repair.apply() }.getOrDefault(false)
                if (!applied) continue
                val retried = runCatching { block() }.getOrNull()
                if (retried != null) {
                    learn(signature, repair.id)
                    record(Failure(stage, signature, message, System.currentTimeMillis(), repair.id))
                    return retried
                }
            }
            record(Failure(stage, signature, message, System.currentTimeMillis(), null))
            null
        }
    }

    /** Learned repair first, then the remaining candidates in their declared order. */
    fun order(signature: String, repairs: List<Repair>): List<Repair> {
        val known = learned[signature] ?: return repairs
        val preferred = repairs.firstOrNull { it.id == known } ?: return repairs
        return listOf(preferred) + repairs.filter { it.id != known }
    }

    private fun learn(signature: String, repairId: String) {
        if (learned[signature] == repairId) return
        learned[signature] = repairId
        saveLearned()
    }

    private fun record(failure: Failure) {
        synchronized(history) {
            history.addFirst(failure)
            while (history.size > MAX_HISTORY) history.removeLast()
        }
        publish()
    }

    fun clearHistory() {
        synchronized(history) { history.clear() }
        publish()
    }

    /** Forgets what was learned; useful after the app is updated and fixes may differ. */
    fun forgetLearned() {
        learned.clear()
        prefs.edit().remove(KEY_LEARNED).apply()
        publish()
    }

    private fun publish() {
        synchronized(history) {
            _state.value = State(
                failures = history.toList(),
                learned = learned.toMap(),
                repairsApplied = history.count { it.repaired }
            )
        }
    }

    private fun loadLearned() {
        val raw = prefs.getString(KEY_LEARNED, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            json.keys().forEach { key -> learned[key] = json.optString(key) }
        }
    }

    private fun saveLearned() {
        val json = JSONObject()
        learned.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_LEARNED, json.toString()).apply()
    }

    private companion object {
        const val PREFS = "repair_memory"
        const val KEY_LEARNED = "learned_repairs"
        const val MAX_HISTORY = 50
    }
}
