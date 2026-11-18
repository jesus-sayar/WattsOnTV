package app.wattsontv

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {

    companion object {
        @Volatile var running = false
        @Volatile var lastWatts = 0
        @Volatile var status = "Detenido"
        @Volatile var connected = false
        @Volatile var deviceName = ""
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_NAME = "name"
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private val averager = PowerAverager(3000L)
    private var ble: PowerBle? = null
    private var wm: WindowManager? = null
    private var view: TextView? = null
    private var lastActive = 0L

    private val tick = object : Runnable {
        override fun run() {
            refreshText()
            checkIdle()
            handler.postDelayed(this, 250)
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.K_LAST) return@OnSharedPreferenceChangeListener
        applyStyle()
        refreshText()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: Prefs.lastPedal(this)
        if (address == null) { stopSelf(); return START_NOT_STICKY }
        deviceName = intent?.getStringExtra(EXTRA_NAME) ?: ""
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Falta el permiso de mostrar sobre otras apps", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        createOverlay()
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefListener)
        running = true
        lastActive = SystemClock.elapsedRealtime()
        handler.post(tick)
        startBle(address)
        return START_STICKY
    }

    /** Si no hay potencia > 0 durante N minutos, se detiene y libera el pedal. */
    private fun checkIdle() {
        val min = Prefs.idleMin(this)
        val now = SystemClock.elapsedRealtime()
        if (lastWatts > 0) lastActive = now
        if (min > 0 && now - lastActive > min * 60_000L) {
            Toast.makeText(this, "WattsOnTV detenido por inactividad", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Vatios", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("WattsOnTV activo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun hasBlePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startBle(address: String) {
        if (!hasBlePermissions()) {
            status = "Falta permiso Bluetooth"
            return
        }
        ble = PowerBle(this, address, averager) { s, c ->
            status = s
            connected = c
            if (!c) averager.clear()
        }.also { it.start() }
    }

    private fun createOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        view = tv
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        wm!!.addView(tv, lp)
        applyStyle()
        refreshText()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun applyStyle() {
        val tv = view ?: return
        val lp = tv.layoutParams as WindowManager.LayoutParams
        lp.gravity = when (Prefs.corner(this)) {
            0 -> Gravity.TOP or Gravity.START
            1 -> Gravity.TOP or Gravity.END
            2 -> Gravity.BOTTOM or Gravity.START
            else -> Gravity.BOTTOM or Gravity.END
        }
        lp.x = dp(32)
        lp.y = dp(24)
        tv.textSize = Prefs.sizeSp(this).toFloat()
        tv.alpha = Prefs.opacity(this) / 100f
        tv.setPadding(dp(16), dp(6), dp(16), dp(6))
        tv.background = GradientDrawable().apply {
            setColor(0x99000000.toInt())
            cornerRadius = dp(12).toFloat()
        }
        wm?.updateViewLayout(tv, lp)
    }

    private fun refreshText() {
        val tv = view ?: return
        val w = averager.average(SystemClock.elapsedRealtime())
        lastWatts = w
        val sb = SpannableStringBuilder()
        if (Prefs.showDot(this)) {
            sb.append("● ")
            sb.setSpan(
                ForegroundColorSpan(if (connected) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        sb.append("$w W")
        tv.text = sb
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        ble?.stop()
        ble = null
        view?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        view = null
        status = "Detenido"
        connected = false
        super.onDestroy()
    }
}
