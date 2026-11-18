package app.wattsontv

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    companion object {
        const val BG = 0xFF0B0E14.toInt()
        const val CARD = 0xFF151A23.toInt()
        const val BTN = 0xFF222A37.toInt()
        const val ACCENT = 0xFFFFC400.toInt()
        const val HOT = 0xFFFF4D2E.toInt()
        const val GREEN = 0xFF2ECC71.toInt()
        const val MUTED = 0xFF8A94A6.toInt()
    }

    private lateinit var scanner: PedalScanner
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false

    private lateinit var banner: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var pedalList: LinearLayout
    private var listSignature = ""

    private lateinit var previewBox: FrameLayout
    private lateinit var previewText: TextView
    private val cornerBtns = ArrayList<Button>()
    private lateinit var sizeVal: TextView
    private lateinit var opacityVal: TextView
    private lateinit var idleVal: TextView
    private lateinit var dotBtn: Button

    private val refresher = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 500) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- helpers de estilo ----------

    private fun rect(fill: Int, stroke: Int = 0, strokeDp: Int = 0, radiusDp: Int = 12) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
        }

    /** Botón con foco muy visible (relleno amarillo y borde blanco). */
    private fun styled(text: String, primary: Boolean = false, selected: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            minHeight = dp(46); minimumHeight = dp(46)
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnClickListener { onClick() }
            restyle(this, primary, selected)
        }

    private fun restyle(b: Button, primary: Boolean, selected: Boolean) {
        val normal = when {
            primary -> rect(HOT)
            selected -> rect(BTN, ACCENT, 3)
            else -> rect(BTN)
        }
        b.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rect(ACCENT, Color.WHITE, 3))
            addState(intArrayOf(android.R.attr.state_pressed), rect(ACCENT))
            addState(intArrayOf(), normal)
        }
        val idle = if (selected && !primary) ACCENT else Color.WHITE
        b.setTextColor(ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(BG, BG, idle)))
    }

    private fun label(text: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = sp; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun lp(w: Int = LinearLayout.LayoutParams.MATCH_PARENT, h: Int = LinearLayout.LayoutParams.WRAP_CONTENT,
                   top: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = dp(top) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rect(CARD, radiusDp = 18)
        setPadding(dp(24), dp(20), dp(24), dp(20))
    }

    // ---------- construcción ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = PedalScanner(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(48), dp(28), dp(48), dp(24))
        }

        // cabecera
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("⚡ ", 30f, ACCENT, true))
        header.addView(label("WattsOnTV", 26f, Color.WHITE, true))
        root.addView(header, lp())

        // aviso de permisos (solo si falta alguno)
        banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(0xFF3A2A12.toInt(), ACCENT, 2)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            visibility = View.GONE
        }
        root.addView(banner, lp(top = 12))

        val cols = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(cols, lp(h = 0, top = 16, weight = 1f))

        // ----- columna izquierda: pedal -----
        val left = card()
        left.addView(label("PEDAL", 14f, MUTED, true))
        statusTitle = label("", 30f, Color.WHITE, true)
        statusSub = label("", 17f, MUTED)
        left.addView(statusTitle, lp(top = 6))
        left.addView(statusSub, lp(top = 2))
        pedalList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val leftScroll = ScrollView(this).apply { addView(pedalList); isFillViewport = false }
        left.addView(leftScroll, lp(h = 0, top = 16, weight = 1f))
        cols.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        // ----- columna derecha: apariencia -----
        val right = card()
        right.addView(label("APARIENCIA", 14f, MUTED, true))

        previewBox = FrameLayout(this).apply { background = rect(0xFF05070B.toInt(), 0xFF2A3342.toInt(), 2, 10) }
        previewText = TextView(this).apply {
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        previewBox.addView(previewText)
        val pw = dp(240)
        val names = arrayOf("↖ Arriba izq.", "↗ Arriba der.", "↙ Abajo izq.", "↘ Abajo der.")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0..1) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..1) {
                val idx = r * 2 + c
                val b = styled(names[idx]) {
                    Prefs.get(this).edit().putInt(Prefs.K_CORNER, idx).apply(); refresh()
                }
                b.textSize = 15f
                b.setPadding(dp(8), dp(4), dp(8), dp(4))
                cornerBtns.add(b)
                row.addView(b, lp(w = 0, weight = 1f).apply { if (c == 1) leftMargin = dp(8) })
            }
            grid.addView(row, lp(top = if (r == 0) 0 else 8))
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topRow.addView(previewBox, lp(w = pw, h = pw * 9 / 16))
        val gridCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gridCol.addView(label("Esquina", 15f, MUTED), lp())
        gridCol.addView(grid, lp(top = 4))
        topRow.addView(gridCol, lp(w = 0, weight = 1f).apply { leftMargin = dp(16) })
        right.addView(topRow, lp(top = 8))

        sizeVal = label("", 19f, Color.WHITE, true)
        right.addView(stepper("Tamaño de letra", sizeVal,
            { step(Prefs.K_SIZE, Prefs.sizeSp(this), -4, 20, 140) },
            { step(Prefs.K_SIZE, Prefs.sizeSp(this), +4, 20, 140) }), lp(top = 8))
        opacityVal = label("", 19f, Color.WHITE, true)
        right.addView(stepper("Opacidad", opacityVal,
            { step(Prefs.K_OPACITY, Prefs.opacity(this), -10, 10, 100) },
            { step(Prefs.K_OPACITY, Prefs.opacity(this), +10, 10, 100) }), lp(top = 6))
        idleVal = label("", 19f, Color.WHITE, true)
        right.addView(stepper("Desconectar tras", idleVal, { moveIdle(-1) }, { moveIdle(+1) }), lp(top = 6))
        dotBtn = styled("") {
            Prefs.get(this).edit().putBoolean(Prefs.K_DOT, !Prefs.showDot(this)).apply(); refresh()
        }
        right.addView(dotBtn, lp(top = 10))

        val rightScroll = ScrollView(this).apply { addView(right) }
        cols.addView(rightScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f)
            .apply { leftMargin = dp(20) })

        setContentView(root)
    }

    private fun stepper(title: String, value: TextView, minus: () -> Unit, plus: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(title, 17f, Color.WHITE), lp(w = 0, weight = 1f))
        row.addView(styled("−") { minus() }, lp(w = dp(64)))
        value.gravity = Gravity.CENTER
        row.addView(value, lp(w = dp(110)))
        row.addView(styled("+") { plus() }, lp(w = dp(64)))
        return row
    }

    private fun step(key: String, cur: Int, d: Int, min: Int, max: Int) {
        Prefs.get(this).edit().putInt(key, (cur + d).coerceIn(min, max)).apply(); refresh()
    }

    private fun moveIdle(d: Int) {
        val o = Prefs.IDLE_OPTIONS
        val i = (o.indexOf(Prefs.idleMin(this)).let { if (it < 0) 3 else it } + d).coerceIn(0, o.size - 1)
        Prefs.get(this).edit().putInt(Prefs.K_IDLE, o[i]).apply(); refresh()
    }

    // ---------- permisos ----------

    private fun blePerms(): Array<String> {
        val l = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            l.add(Manifest.permission.BLUETOOTH_SCAN); l.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else l.add(Manifest.permission.ACCESS_FINE_LOCATION)
        return l.toTypedArray()
    }

    private fun hasBlePerm() = blePerms().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun requestOverlayPermission() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this,
                "Sin pantalla de permiso. Usa: adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun requestBlePermission() {
        val p = ArrayList(blePerms().toList())
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(p.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) { refresh() }

    // ---------- acciones ----------

    private fun connect(p: FoundPedal) {
        if (!Settings.canDrawOverlays(this) || !hasBlePerm()) {
            Toast.makeText(this, "Concede primero los permisos del aviso naranja", Toast.LENGTH_LONG).show(); return
        }
        scanner.stop()
        Prefs.get(this).edit().putString(Prefs.K_LAST, p.address).apply()
        val i = Intent(this, OverlayService::class.java)
            .putExtra(OverlayService.EXTRA_ADDRESS, p.address)
            .putExtra(OverlayService.EXTRA_NAME, p.name)
        startForegroundService(i)
        handler.postDelayed({ refresh() }, 300)
    }

    private fun disconnect() {
        stopService(Intent(this, OverlayService::class.java))
        handler.postDelayed({ refresh() }, 300)
    }

    // ---------- refresco ----------

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val bleOk = hasBlePerm()
        updateBanner(overlayOk, bleOk)

        val running = OverlayService.running
        if (running) scanner.stop()
        else if (resumed && bleOk) scanner.start()
        scanner.prune()

        // estado
        when {
            running && OverlayService.connected -> {
                statusTitle.text = "● Conectado"; statusTitle.setTextColor(GREEN)
                statusSub.text = "${OverlayService.deviceName}   ·   ${OverlayService.lastWatts} W (media 3 s)"
            }
            running -> {
                statusTitle.text = "● ${OverlayService.status}"; statusTitle.setTextColor(ACCENT)
                statusSub.text = "${OverlayService.deviceName}   ·   gira la biela para despertar el pedal"
            }
            !bleOk -> {
                statusTitle.text = "Sin permiso Bluetooth"; statusTitle.setTextColor(HOT)
                statusSub.text = "Pulsa Conceder en el aviso de arriba"
            }
            else -> {
                statusTitle.text = if (scanner.found.isEmpty()) "Buscando pedales…" else "Pedales encontrados"
                statusTitle.setTextColor(Color.WHITE)
                statusSub.text = if (scanner.found.isEmpty()) "Gira la biela para despertar el pedal" else "Elige uno para conectar"
            }
        }
        rebuildList(running)

        // apariencia
        val corner = Prefs.corner(this)
        cornerBtns.forEachIndexed { i, b -> restyle(b, false, i == corner) }
        sizeVal.text = "${Prefs.sizeSp(this)} sp"
        opacityVal.text = "${Prefs.opacity(this)} %"
        val im = Prefs.idleMin(this)
        idleVal.text = if (im == 0) "Nunca" else "$im min"
        dotBtn.text = "Punto de conexión (verde/rojo):  ${if (Prefs.showDot(this)) "SÍ" else "NO"}"
        updatePreview(running)
    }

    private fun updateBanner(overlayOk: Boolean, bleOk: Boolean) {
        val sig = "$overlayOk$bleOk"
        if (banner.tag == sig) return
        banner.tag = sig
        banner.removeAllViews()
        if (overlayOk && bleOk) { banner.visibility = View.GONE; return }
        banner.visibility = View.VISIBLE
        if (!overlayOk) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(label("Falta el permiso de mostrar sobre otras apps", 17f), lp(w = 0, weight = 1f))
            r.addView(styled("Conceder", primary = true) { requestOverlayPermission() }, lp(w = LinearLayout.LayoutParams.WRAP_CONTENT))
            banner.addView(r, lp())
        }
        if (!bleOk) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(label("Falta el permiso de Bluetooth", 17f), lp(w = 0, weight = 1f))
            r.addView(styled("Conceder", primary = true) { requestBlePermission() }, lp(w = LinearLayout.LayoutParams.WRAP_CONTENT))
            banner.addView(r, lp(top = if (!overlayOk) 8 else 0))
        }
    }

    private fun rebuildList(running: Boolean) {
        val sig = if (running) "R" else "S" + scanner.found.keys.joinToString(",") + Prefs.lastPedal(this)
        if (sig == listSignature) return
        listSignature = sig
        val hadFocus = pedalList.hasFocus() || currentFocus == null
        pedalList.removeAllViews()
        var first: View? = null
        if (running) {
            val b = styled("Desconectar", primary = true) { disconnect() }
            pedalList.addView(b, lp()); first = b
        } else {
            val last = Prefs.lastPedal(this)
            for (p in scanner.found.values.sortedByDescending { it.rssi }) {
                val tag = if (p.address == last) "  (último usado)" else ""
                val b = styled("${p.name}$tag   ·   Conectar", primary = true) { connect(p) }
                pedalList.addView(b, lp(top = 8)); if (first == null) first = b
            }
        }
        if (hadFocus) first?.requestFocus()
    }

    private fun updatePreview(running: Boolean) {
        val pw = dp(240).toFloat()
        val ratio = pw / resources.displayMetrics.widthPixels
        val px = Prefs.sizeSp(this) * resources.displayMetrics.scaledDensity * ratio
        previewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px.coerceAtLeast(6f))
        val pad = (dp(16) * ratio).toInt(); val padV = (dp(6) * ratio).toInt()
        previewText.setPadding(pad, padV, pad, padV)
        previewText.background = GradientDrawable().apply {
            setColor(0x99000000.toInt()); cornerRadius = dp(12) * ratio
        }
        previewText.alpha = Prefs.opacity(this) / 100f
        val w = if (running && OverlayService.connected) OverlayService.lastWatts else 245
        previewText.text = if (Prefs.showDot(this)) "● $w W" else "$w W"
        val m = (dp(32) * ratio).toInt(); val mv = (dp(24) * ratio).toInt()
        val lpp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = when (Prefs.corner(this@MainActivity)) {
                0 -> Gravity.TOP or Gravity.START
                1 -> Gravity.TOP or Gravity.END
                2 -> Gravity.BOTTOM or Gravity.START
                else -> Gravity.BOTTOM or Gravity.END
            }
            setMargins(m, mv, m, mv)
        }
        previewText.layoutParams = lpp
    }

    override fun onResume() { super.onResume(); resumed = true; listSignature = ""; banner.tag = null; handler.post(refresher) }
    override fun onPause() { super.onPause(); resumed = false; handler.removeCallbacks(refresher); scanner.stop() }
}
