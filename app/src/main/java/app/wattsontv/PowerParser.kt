package app.wattsontv

/** Parser de la característica Cycling Power Measurement (0x2A63). */
object PowerParser {
    /** Bytes 0-1: flags (uint16 LE). Bytes 2-3: potencia instantánea (sint16 LE, vatios). */
    fun parse(data: ByteArray?): Int? {
        if (data == null || data.size < 4) return null
        val lo = data[2].toInt() and 0xFF
        val hi = data[3].toInt()          // con signo
        return (hi shl 8) or lo
    }
}
