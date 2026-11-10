package kr.heartblink.car.app

import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeBandTest {

    private fun band(value: String) = TimeBand.of(value)!!

    @Test
    fun spokenDropsTheSecondHalfOfDayWhenItIsTheSame() {
        assertEquals("오후 6시~7시 30분", band("custom:1800-1930").spoken)
        assertEquals("오전 7시~11시", band("morning").spoken)
    }

    @Test
    fun spokenKeepsBothHalvesWhenTheyDiffer() {
        assertEquals("오후 8시~오전 4시", band("night").spoken)
        assertEquals("오후 2시~오전 10시", band("custom:1400-1000").spoken)
        assertEquals("오후 11시~밤 12시", band("custom:2300-0000").spoken)
    }

    @Test
    fun spokenSaysNextDayWhenTheRangeComesBackToTheSameHalf() {
        assertEquals("오전 11시~다음 날 오전 10시", band("custom:1100-1000").spoken)
        assertEquals("오후 6시~다음 날 오후 5시", band("custom:1800-1700").spoken)
    }

    @Test
    fun aRangeThatPassesMidnightContainsBothSides() {
        val b = band("custom:1100-1000")
        assertTrue(b.contains(LocalTime(9, 15)))
        assertTrue(b.contains(LocalTime(23, 0)))
        assertFalse(b.contains(LocalTime(10, 30)))
    }
}
