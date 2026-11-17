package com.liskovsoft.youtubeapi.app.prewarm

import java.util.concurrent.ConcurrentHashMap

/**
 * leanview: remembers which YouTube players could not be solved, so that a player the solver cannot handle is not tried again for
 * every video and every prewarm (each try can take minutes on an interpreter).
 *
 * A player url is OPEN (not to be tried) after [maxFailures] failures in a row, or at once after a timeout; it is tried again
 * once [cooldownMs] have passed (one try; a failure opens it again), and a success clears it. No clock or thread in here but the
 * ones the caller passes.
 */
class PlayerFailureBreaker(private val maxFailures: Int = 3, private val cooldownMs: Long = 30 * 60 * 1000L) {
    private class Entry(var failures: Int, var openedAtMs: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun isOpen(playerUrl: String?, nowMs: Long): Boolean {
        val entry = entries[playerUrl ?: return false] ?: return false
        synchronized(entry) {
            return entry.failures >= maxFailures && nowMs - entry.openedAtMs < cooldownMs
        }
    }

    fun recordFailure(playerUrl: String?, nowMs: Long) {
        val entry = entries.getOrPut(playerUrl ?: return) { Entry(0, 0) }
        synchronized(entry) {
            entry.failures++
            if (entry.failures >= maxFailures) {
                entry.openedAtMs = nowMs
            }
        }
    }

    /** A timeout opens the circuit at once: another try would burn the same minutes again. */
    fun recordTimeout(playerUrl: String?, nowMs: Long) {
        val entry = entries.getOrPut(playerUrl ?: return) { Entry(0, 0) }
        synchronized(entry) {
            entry.failures = maxOf(entry.failures + 1, maxFailures)
            entry.openedAtMs = nowMs
        }
    }

    fun recordSuccess(playerUrl: String?) {
        entries.remove(playerUrl ?: return)
    }

    companion object {
        /** The one of the app: the solvers and the prewarm share what they learned about a player. */
        @JvmField
        val shared = PlayerFailureBreaker()
    }
}
