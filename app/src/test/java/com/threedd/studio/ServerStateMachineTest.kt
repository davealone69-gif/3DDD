package com.threedd.studio

import com.threedd.studio.ai.ServerStateMachine
import com.threedd.studio.ai.ServerStateMachine.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule under test is the one the user asked for: never report ONLINE unless a health check
 * actually succeeded, and never leave the UI claiming a server is up when it has gone away.
 */
class ServerStateMachineTest {

    @Test
    fun `starts offline`() {
        assertEquals(State.OFFLINE, ServerStateMachine().current.state)
    }

    @Test
    fun `requesting a start moves to starting not online`() {
        val machine = ServerStateMachine()
        val status = machine.onStartRequested(now = 1000L)
        assertEquals(State.STARTING, status.state)
        assertFalse("a request is not evidence of a running server", status.online)
    }

    @Test
    fun `only a passing health check produces online`() {
        val machine = ServerStateMachine()
        machine.onStartRequested(1000L)
        assertEquals(State.STARTING, machine.onProbe(healthy = false, now = 1500L).state)
        val online = machine.onProbe(healthy = true, now = 2000L)
        assertEquals(State.ONLINE, online.state)
        assertTrue(online.online)
        assertEquals(0, online.consecutiveMisses)
    }

    @Test
    fun `a refused start is reported as unreachable with its reason`() {
        val machine = ServerStateMachine()
        machine.onStartRequested(1000L)
        val status = machine.onStartRefused(1000L, "Termux is not installed")
        assertEquals(State.UNREACHABLE, status.state)
        assertTrue(status.detail.contains("Termux"))
        assertFalse(status.online)
    }

    @Test
    fun `misses during startup stay in starting until the timeout`() {
        val machine = ServerStateMachine(startupTimeoutMs = 10_000L)
        machine.onStartRequested(1000L)
        // 5 seconds in, still inside the window
        assertEquals(State.STARTING, machine.onProbe(false, 6000L).state)
        // 12 seconds in, past the window
        assertEquals(State.STARTING, machine.current.state)
        machine.onStartupTimedOut(12_000L)
        assertEquals(State.OFFLINE, machine.current.state)
        assertTrue(machine.current.detail.contains("Timed out"))
    }

    @Test
    fun `an online server that stops answering goes offline`() {
        val machine = ServerStateMachine()
        machine.onStartRequested(0L)
        machine.onProbe(true, 100L)
        assertEquals(State.ONLINE, machine.current.state)
        machine.onDisappeared(200L)
        assertEquals(State.OFFLINE, machine.current.state)
        assertFalse(machine.current.online)
        assertTrue(machine.current.detail.contains("stopped responding"))
    }

    @Test
    fun `an online server recovers when health returns`() {
        val machine = ServerStateMachine()
        machine.onStartRequested(0L)
        machine.onProbe(true, 100L)
        machine.onDisappeared(200L)
        assertEquals(State.OFFLINE, machine.current.state)
        machine.onProbe(true, 300L)
        assertEquals(State.ONLINE, machine.current.state)
    }

    @Test
    fun `transient misses while online do not immediately drop the state`() {
        val machine = ServerStateMachine(offlineAfterMisses = 3)
        machine.onStartRequested(0L)
        machine.onProbe(true, 100L)
        assertEquals(State.ONLINE, machine.onProbe(false, 200L).state)
        assertEquals(State.ONLINE, machine.onProbe(false, 300L).state)
        // the third consecutive miss is treated as a real outage
        assertEquals(State.OFFLINE, machine.onProbe(false, 400L).state)
    }
}
