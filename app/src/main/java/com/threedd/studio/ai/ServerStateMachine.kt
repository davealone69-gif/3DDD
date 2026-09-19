package com.threedd.studio.ai

/**
 * The truth about whether an AI server is actually reachable.
 *
 * Kept pure and free of Android or network types so every transition is unit testable. The
 * rule it enforces is the one that matters: ONLINE is only ever entered because a health
 * check succeeded. Requesting a start moves to STARTING, never to ONLINE.
 */
class ServerStateMachine(
    private val startupTimeoutMs: Long = 45_000L,
    private val offlineAfterMisses: Int = 3
) {

    enum class State { OFFLINE, STARTING, ONLINE, UNREACHABLE }

    data class Status(
        val state: State = State.OFFLINE,
        val detail: String = "Not checked yet",
        val consecutiveMisses: Int = 0,
        val startedAt: Long = 0L,
        val lastHealthyAt: Long = 0L,
        /** True when the user asked us to start it and we are still waiting. */
        val startingRequested: Boolean = false
    ) {
        val online: Boolean get() = state == State.ONLINE
    }

    private var status = Status()

    val current: Status get() = status

    /** The user asked for a start. We are not online until a probe says so. */
    fun onStartRequested(now: Long, detail: String = "Requesting server start"): Status {
        status = status.copy(
            state = State.STARTING,
            detail = detail,
            startedAt = now,
            startingRequested = true,
            consecutiveMisses = 0
        )
        return status
    }

    /** The start request itself failed (no Termux, permission refused, ...). */
    fun onStartRefused(now: Long, detail: String): Status {
        status = status.copy(
            state = State.UNREACHABLE,
            detail = detail,
            startingRequested = false,
            consecutiveMisses = 0
        )
        return status
    }

    /**
     * A health probe completed. [healthy] is the only thing that can produce ONLINE.
     * While STARTING, misses are tolerated until the startup timeout elapses.
     */
    fun onProbe(healthy: Boolean, now: Long, detail: String = ""): Status {
        status = if (healthy) {
            status.copy(
                state = State.ONLINE,
                detail = detail.ifBlank { "Server answered its health check" },
                consecutiveMisses = 0,
                lastHealthyAt = now,
                startingRequested = false
            )
        } else {
            val misses = status.consecutiveMisses + 1
            val withinStartup = status.startingRequested &&
                status.startedAt > 0L &&
                now - status.startedAt < startupTimeoutMs
            when {
                withinStartup -> status.copy(
                    state = State.STARTING,
                    detail = detail.ifBlank { "Waiting for the server to answer" },
                    consecutiveMisses = misses
                )
                status.state == State.ONLINE || misses >= offlineAfterMisses -> status.copy(
                    state = State.OFFLINE,
                    detail = detail.ifBlank { "Server is not answering" },
                    consecutiveMisses = misses,
                    startingRequested = false
                )
                else -> status.copy(
                    consecutiveMisses = misses,
                    detail = detail.ifBlank { "Health check did not respond" }
                )
            }
        }
        return status
    }

    /** A start was requested and the timeout elapsed without a health response. */
    fun onStartupTimedOut(now: Long): Status {
        if (status.state == State.STARTING && now - status.startedAt >= startupTimeoutMs) {
            status = status.copy(
                state = State.OFFLINE,
                detail = "Timed out waiting for the server after ${startupTimeoutMs / 1000}s",
                startingRequested = false
            )
        }
        return status
    }

    /** The process vanished while we were online: stop claiming it is up. */
    fun onDisappeared(now: Long): Status {
        if (status.state == State.ONLINE) {
            status = status.copy(
                state = State.OFFLINE,
                detail = "The server stopped responding",
                consecutiveMisses = offlineAfterMisses,
                startingRequested = false
            )
        }
        return status
    }
}
