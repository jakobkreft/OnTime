package si.jakobkreft.ontime.data

import kotlinx.serialization.Serializable
import si.jakobkreft.ontime.domain.DurationText

/** A saved timer: a name and the three durations that drive the traffic light. */
@Serializable
data class Preset(
    val id: String,
    val name: String,
    val totalMillis: Long,
    val yellowMillis: Long,
    val redMillis: Long,
) {
    /**
     * The warnings are "time remaining", so both must fall inside the run and red must not come
     * before yellow. Either warning may be zero, which turns that phase off.
     */
    val isValid: Boolean
        get() = totalMillis in 1..DurationText.MAX_MILLIS &&
            yellowMillis in 0 until totalMillis &&
            redMillis in 0..yellowMillis

    companion object {
        const val DEFAULT_TOTAL_MILLIS = 25L * 60 * 1000
        const val DEFAULT_YELLOW_MILLIS = 10L * 60 * 1000
        const val DEFAULT_RED_MILLIS = 5L * 60 * 1000
    }
}

/** Whether a preset is counting, held, or has not been started. */
@Serializable
enum class RunStatus { IDLE, RUNNING, PAUSED }

/**
 * When a preset was started, not how far it has got.
 *
 * [startedAtRealtimeMillis] comes from `SystemClock.elapsedRealtime`, which is monotonic and keeps
 * counting through sleep, so a running timer is immune to the wall clock being changed underneath
 * it. [startedAtWallMillis] is kept alongside only to notice a reboot, after which the realtime
 * clock has restarted and the stored value means nothing.
 */
@Serializable
data class RunState(
    val status: RunStatus = RunStatus.IDLE,
    val startedAtRealtimeMillis: Long = 0L,
    val startedAtWallMillis: Long = 0L,
    val accumulatedMillis: Long = 0L,
) {
    fun elapsedMillis(nowRealtimeMillis: Long): Long = when (status) {
        RunStatus.RUNNING -> accumulatedMillis + (nowRealtimeMillis - startedAtRealtimeMillis)
        RunStatus.PAUSED -> accumulatedMillis
        RunStatus.IDLE -> 0L
    }

    val isActive: Boolean get() = status != RunStatus.IDLE

    /**
     * True unless the device rebooted while this timer was running, which is the one event that
     * invalidates [startedAtRealtimeMillis]. Both clocks advance together, so a large disagreement
     * between them means the realtime origin is gone.
     */
    fun isCoherent(nowRealtimeMillis: Long, nowWallMillis: Long): Boolean {
        if (status != RunStatus.RUNNING) return true
        val byRealtime = nowRealtimeMillis - startedAtRealtimeMillis
        val byWall = nowWallMillis - startedAtWallMillis
        return byRealtime >= 0 && kotlin.math.abs(byWall - byRealtime) < REBOOT_TOLERANCE_MILLIS
    }

    private companion object {
        const val REBOOT_TOLERANCE_MILLIS = 60_000L
    }
}

/** Everything the app persists, in one document. */
@Serializable
data class AppData(
    val presets: List<Preset>,
    val selectedId: String,
    val runs: Map<String, RunState> = emptyMap(),
) {
    val selected: Preset
        get() = presets.firstOrNull { it.id == selectedId } ?: presets.first()

    fun runOf(presetId: String): RunState = runs[presetId] ?: RunState()
}
