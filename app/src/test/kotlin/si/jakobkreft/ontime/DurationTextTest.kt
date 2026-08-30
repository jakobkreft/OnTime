package si.jakobkreft.ontime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.jakobkreft.ontime.domain.DurationText

class DurationTextTest {

    @Test
    fun `bare number is seconds`() {
        assertEquals(90_000L, DurationText.parse("90"))
    }

    @Test
    fun `colon forms`() {
        assertEquals(90_000L, DurationText.parse("1:30"))
        assertEquals(3_720_000L, DurationText.parse("1:02:00"))
    }

    @Test
    fun `dots spaces and commas separate like colons`() {
        assertEquals(90_000L, DurationText.parse("1.30"))
        assertEquals(90_000L, DurationText.parse("1 30"))
        assertEquals(90_000L, DurationText.parse("1,30"))
    }

    @Test
    fun `unit forms, long and short`() {
        assertEquals(25 * 60_000L, DurationText.parse("25m"))
        assertEquals(25 * 60_000L, DurationText.parse("25 minutes"))
        assertEquals(3_600_000L, DurationText.parse("1h"))
        assertEquals(5_400_000L, DurationText.parse("1h 30m"))
        assertEquals(5_400_000L, DurationText.parse("1 hour 30 mins"))
        assertEquals(45_000L, DurationText.parse("45 sec"))
    }

    @Test
    fun `units are case insensitive`() {
        assertEquals(5_400_000L, DurationText.parse("1H 30M"))
    }

    @Test
    fun `unreadable input is null, never zero`() {
        assertNull(DurationText.parse(""))
        assertNull(DurationText.parse("   "))
        assertNull(DurationText.parse("soon"))
        assertNull(DurationText.parse("25 potatoes"))
        assertNull(DurationText.parse("1:2:3:4"))
        assertNull(DurationText.parse("-5"))
        assertNull(DurationText.parse("1h junk"))
    }

    @Test
    fun `zero is a readable duration`() {
        assertEquals(0L, DurationText.parse("0"))
        assertEquals(0L, DurationText.parse("0:00"))
    }

    @Test
    fun `absurd durations are rejected`() {
        assertNull(DurationText.parse("100h"))
        assertNull(DurationText.parse("999999:00:00"))
    }

    @Test
    fun `format drops the hour until it is needed`() {
        assertEquals("0:00", DurationText.format(0))
        assertEquals("0:07", DurationText.format(7_000))
        assertEquals("25:00", DurationText.format(25 * 60_000L))
        assertEquals("1:02:03", DurationText.format(3_723_000L))
    }

    @Test
    fun `format clamps negatives rather than printing a minus`() {
        assertEquals("0:00", DurationText.format(-5_000))
    }

    @Test
    fun `parse and format round-trip`() {
        listOf(0L, 1_000L, 59_000L, 60_000L, 1_500_000L, 3_723_000L).forEach { millis ->
            assertEquals(millis, DurationText.parse(DurationText.format(millis)))
        }
    }

    @Test
    fun `rounding helpers`() {
        assertEquals(1_000L, DurationText.ceilToSecond(1))
        assertEquals(1_000L, DurationText.ceilToSecond(1_000))
        assertEquals(2_000L, DurationText.ceilToSecond(1_001))
        assertEquals(0L, DurationText.floorToSecond(999))
        assertEquals(1_000L, DurationText.floorToSecond(1_999))
    }
}
