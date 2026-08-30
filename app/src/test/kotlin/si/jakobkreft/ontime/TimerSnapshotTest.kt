package si.jakobkreft.ontime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.jakobkreft.ontime.data.Preset
import si.jakobkreft.ontime.domain.Phase
import si.jakobkreft.ontime.domain.TimerSnapshot

class TimerSnapshotTest {

    private val talk = Preset(
        id = "test",
        name = "Talk",
        totalMillis = 25 * 60_000L,
        yellowMillis = 10 * 60_000L,
        redMillis = 5 * 60_000L,
    )

    private fun at(elapsedMillis: Long) = TimerSnapshot.of(talk, elapsedMillis)

    @Test
    fun `starts green and full`() {
        val snapshot = at(0)
        assertEquals(Phase.GREEN, snapshot.phase)
        assertEquals(25 * 60_000L, snapshot.remainingMillis)
        assertEquals(0f, snapshot.progress, 0f)
        assertFalse(snapshot.isOvertime)
    }

    @Test
    fun `turns yellow on the tick the clock reads the threshold`() {
        assertEquals(Phase.GREEN, at(15 * 60_000L - 1).phase)
        assertEquals(Phase.YELLOW, at(15 * 60_000L).phase)
    }

    @Test
    fun `turns red on the tick the clock reads the threshold`() {
        assertEquals(Phase.YELLOW, at(20 * 60_000L - 1).phase)
        assertEquals(Phase.RED, at(20 * 60_000L).phase)
    }

    @Test
    fun `remaining rounds up so the clock holds a value for its whole second`() {
        assertEquals(25 * 60_000L, at(1).remainingMillis)
        assertEquals(25 * 60_000L, at(999).remainingMillis)
        assertEquals(25 * 60_000L - 1_000, at(1_000).remainingMillis)
    }

    @Test
    fun `overtime counts up from zero and stays red`() {
        val atZero = at(25 * 60_000L)
        assertTrue(atZero.isOvertime)
        assertEquals(Phase.RED, atZero.phase)
        assertEquals(0L, atZero.remainingMillis)
        assertEquals(0L, atZero.overtimeMillis)
        assertEquals(1f, atZero.progress, 0f)

        assertEquals(0L, at(25 * 60_000L + 999).overtimeMillis)
        assertEquals(1_000L, at(25 * 60_000L + 1_000).overtimeMillis)
        assertEquals(90_000L, at(25 * 60_000L + 90_000).overtimeMillis)
    }

    @Test
    fun `overtime keeps its own clock long past the end`() {
        val hourOver = at(25 * 60_000L + 3_600_000L)
        assertEquals(3_600_000L, hourOver.overtimeMillis)
        assertEquals(1f, hourOver.progress, 0f)
    }

    @Test
    fun `a warning set to zero turns that phase off`() {
        val noYellow = talk.copy(yellowMillis = 0, redMillis = 0)
        assertEquals(Phase.GREEN, TimerSnapshot.of(noYellow, 24 * 60_000L).phase)
        assertEquals(Phase.RED, TimerSnapshot.of(noYellow, 25 * 60_000L).phase)
    }

    @Test
    fun `equal warnings collapse to a single red phase`() {
        val redOnly = talk.copy(yellowMillis = 5 * 60_000L, redMillis = 5 * 60_000L)
        assertEquals(Phase.GREEN, TimerSnapshot.of(redOnly, 20 * 60_000L - 1).phase)
        assertEquals(Phase.RED, TimerSnapshot.of(redOnly, 20 * 60_000L).phase)
    }

    @Test
    fun `negative elapsed is treated as not started`() {
        assertEquals(25 * 60_000L, at(-5_000).remainingMillis)
    }

    @Test
    fun `preset validity rules`() {
        assertTrue(talk.isValid)
        assertFalse(talk.copy(totalMillis = 0).isValid)
        assertFalse(talk.copy(yellowMillis = talk.totalMillis).isValid)
        assertFalse(talk.copy(redMillis = talk.yellowMillis + 1).isValid)
        assertTrue(talk.copy(yellowMillis = 0, redMillis = 0).isValid)
    }
}
