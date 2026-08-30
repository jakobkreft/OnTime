package si.jakobkreft.ontime.domain

import si.jakobkreft.ontime.data.Preset

/** Which colour the whole screen is showing. */
enum class Phase { GREEN, YELLOW, RED }

/**
 * Everything the run screen draws, for one instant.
 *
 * This is a pure function of the preset and how long it has been running, which is the whole point:
 * there is no timer state to save, restore or lose. A rotation, a process death or a trip through
 * the background all reduce to computing this again from a timestamp.
 */
data class TimerSnapshot(
    val phase: Phase,
    /** Time left, rounded up to a whole second and never negative. */
    val remainingMillis: Long,
    /** Time past zero, rounded down to a whole second and never negative. */
    val overtimeMillis: Long,
    /** 0f at the start, 1f at zero and beyond. */
    val progress: Float,
    val isOvertime: Boolean,
) {
    companion object {
        fun of(preset: Preset, elapsedMillis: Long): TimerSnapshot {
            val elapsed = elapsedMillis.coerceAtLeast(0)
            val left = preset.totalMillis - elapsed
            val isOvertime = left <= 0L

            // Round up so the colour changes on the same tick the display reaches the threshold:
            // when the clock reads 5:00 the screen is already red.
            val remaining = if (isOvertime) 0L else DurationText.ceilToSecond(left)
            val overtime = if (isOvertime) DurationText.floorToSecond(-left) else 0L

            val phase = when {
                isOvertime -> Phase.RED
                remaining <= preset.redMillis -> Phase.RED
                remaining <= preset.yellowMillis -> Phase.YELLOW
                else -> Phase.GREEN
            }

            val progress = when {
                preset.totalMillis <= 0L -> 1f
                else -> (elapsed.toFloat() / preset.totalMillis).coerceIn(0f, 1f)
            }

            return TimerSnapshot(phase, remaining, overtime, progress, isOvertime)
        }
    }
}
