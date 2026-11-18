package app.wattsontv

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NAME = "settings"
    const val K_CORNER = "corner"
    const val K_SIZE = "size_sp"
    const val K_OPACITY = "opacity"
    const val K_DOT = "show_dot"
    const val K_LAST = "last_pedal"
    const val K_IDLE = "idle_min_v2"

    /** Minutos de inactividad antes de desconectar. 0 = nunca. */
    val IDLE_OPTIONS = intArrayOf(5, 10, 15, 30, 45, 60, 0)

    fun get(c: Context): SharedPreferences = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun corner(c: Context) = get(c).getInt(K_CORNER, 1) // 0 AI, 1 AD, 2 BI, 3 BD
    fun sizeSp(c: Context) = get(c).getInt(K_SIZE, 48)
    fun opacity(c: Context) = get(c).getInt(K_OPACITY, 90)
    fun showDot(c: Context) = get(c).getBoolean(K_DOT, true)
    fun idleMin(c: Context) = get(c).getInt(K_IDLE, 30)
    fun lastPedal(c: Context): String? = get(c).getString(K_LAST, null)
}
