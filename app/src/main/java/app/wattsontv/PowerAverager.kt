package app.wattsontv

/**
 * Media móvil de potencia ponderada por tiempo.
 * Cada muestra se mantiene hasta que llega la siguiente.
 * Si no llegan datos durante la ventana completa, devuelve 0.
 */
class PowerAverager(private val windowMs: Long = 3000L) {
    private val times = ArrayList<Long>()
    private val watts = ArrayList<Int>()

    @Synchronized
    fun add(timeMs: Long, w: Int) {
        times.add(timeMs)
        watts.add(w)
        // conservar solo una muestra anterior al inicio de la ventana
        val start = timeMs - windowMs
        while (times.size > 2 && times[1] <= start) {
            times.removeAt(0)
            watts.removeAt(0)
        }
    }

    @Synchronized
    fun average(nowMs: Long): Int {
        if (times.isEmpty()) return 0
        val last = times.size - 1
        if (nowMs - times[last] > windowMs) return 0
        val start = nowMs - windowMs
        var sum = 0.0
        var dur = 0L
        for (i in 0..last) {
            val s = maxOf(times[i], start)
            val e = if (i < last) times[i + 1] else nowMs
            if (e > s) {
                sum += watts[i].toDouble() * (e - s)
                dur += (e - s)
            }
        }
        return if (dur > 0) Math.round(sum / dur).toInt() else watts[last]
    }

    @Synchronized
    fun clear() {
        times.clear()
        watts.clear()
    }
}
