package app.wattsontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test fun parsesRealPackets() {
        assertEquals(99, PowerParser.parse(hex("230063005E5C004324")))
        assertEquals(133, PowerParser.parse(hex("23008500585E00AF29")))
        assertEquals(141, PowerParser.parse(hex("23008D005049009BF2")))
        assertEquals(50, PowerParser.parse(hex("230032006865007C27")))
    }

    @Test fun parsesLargeAndNegative() {
        assertEquals(250, PowerParser.parse(hex("2300FA00")))
        assertEquals(1000, PowerParser.parse(hex("2300E803")))
        assertEquals(-1, PowerParser.parse(hex("2300FFFF")))
    }

    @Test fun rejectsShort() {
        assertNull(PowerParser.parse(hex("2300")))
        assertNull(PowerParser.parse(null))
    }

    @Test fun averageWeightedByTime() {
        val a = PowerAverager(3000)
        a.add(0, 100)
        a.add(1000, 200)   // 100 W durante 1 s
        // a t=3000: 100W*1s + 200W*2s = 500/3 = 167 (ventana 0..3000)
        assertEquals(167, a.average(3000))
    }

    @Test fun zeroWhenNoData() {
        val a = PowerAverager(3000)
        assertEquals(0, a.average(0))
        a.add(1000, 200)
        assertEquals(200, a.average(2000))
        assertEquals(0, a.average(4500))
    }

    @Test fun oldSamplesDropOutOfWindow() {
        val a = PowerAverager(3000)
        a.add(0, 500)
        a.add(1000, 100)
        a.add(2000, 100)
        a.add(3000, 100)
        a.add(4000, 100)
        assertEquals(100, a.average(4500))
    }
}
