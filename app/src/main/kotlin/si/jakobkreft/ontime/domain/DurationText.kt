package si.jakobkreft.ontime.domain

import java.util.Locale

/**
 * Reading and writing the durations the user types.
 *
 * [parse] returns `null` for anything it cannot read rather than falling back to zero, so a
 * caller can always tell "not a duration" apart from "no time".
 */
object DurationText {

    /** Longest duration the app accepts. Past this the display and the arithmetic stop being useful. */
    const val MAX_MILLIS: Long = 99L * 60 * 60 * 1000

    private val UNIT_TOKEN = Regex(
        """(\d{1,7})\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)""",
        RegexOption.IGNORE_CASE,
    )

    private val SEPARATORS = charArrayOf(':', '.', ',', ' ', '\t')

    /**
     * Accepts `90`, `1:30`, `1.30`, `1:02:00`, `90s`, `25m`, `1h 30m`, `1 hour 5 min`.
     * Returns the duration in milliseconds, or `null` if the text is not a duration the app
     * can read or is longer than [MAX_MILLIS].
     */
    fun parse(input: String): Long? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val millis = parseWithUnits(text) ?: parseClock(text) ?: return null
        return millis.takeIf { it in 0..MAX_MILLIS }
    }

    /** `H:MM:SS` past an hour, `M:SS` below it. Never locale-dependent digits. */
    fun format(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * A string with the same shape as the longest value a countdown from [totalMillis] can show.
     * Digits are tabular, so laying the timer out against this keeps the type size steady for the
     * whole run instead of resizing every time a digit drops.
     */
    fun widestForCountdown(totalMillis: Long): String = format(totalMillis)

    /** Whole seconds, rounded up: the display reads 25:00 until a full second has passed. */
    fun ceilToSecond(millis: Long): Long = ((millis + 999) / 1000) * 1000

    /** Whole seconds, rounded down: overtime shows +0:00 for its first second. */
    fun floorToSecond(millis: Long): Long = (millis / 1000) * 1000

    private fun parseWithUnits(text: String): Long? {
        if (text.none { it.isLetter() }) return null
        var index = 0
        var total = 0L
        var matched = false
        while (index < text.length) {
            val character = text[index]
            if (character.isWhitespace() || character == ',' || character == '+') {
                index++
                continue
            }
            val match = UNIT_TOKEN.matchAt(text, index) ?: return null
            val value = match.groupValues[1].toLongOrNull() ?: return null
            total += value * unitMillis(match.groupValues[2])
            if (total > MAX_MILLIS) return null
            index = match.range.last + 1
            matched = true
        }
        return total.takeIf { matched }
    }

    private fun unitMillis(unit: String): Long = when (unit.lowercase().first()) {
        'h' -> 3_600_000L
        'm' -> 60_000L
        else -> 1_000L
    }

    private fun parseClock(text: String): Long? {
        val parts = text.split(*SEPARATORS).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 3) return null
        if (parts.any { part -> part.length > 6 || !part.all(Char::isDigit) }) return null
        val values = parts.map { it.toLongOrNull() ?: return null }
        val (hours, minutes, seconds) = when (values.size) {
            1 -> Triple(0L, 0L, values[0])
            2 -> Triple(0L, values[0], values[1])
            else -> Triple(values[0], values[1], values[2])
        }
        return (hours * 3600 + minutes * 60 + seconds) * 1000
    }
}
