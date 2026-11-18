package app.wattsontv

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log

data class FoundPedal(val address: String, var name: String, var rssi: Int, var lastSeen: Long)

/** Escanea medidores de potencia BLE (servicio 0x1818) cercanos. */
@SuppressLint("MissingPermission")
class PedalScanner(private val ctx: Context) {
    val found = LinkedHashMap<String, FoundPedal>()
    var active = false
        private set

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) {
            val name = r.scanRecord?.deviceName ?: r.device.name ?: "Medidor de potencia"
            val now = SystemClock.elapsedRealtime()
            val f = found[r.device.address]
            if (f == null) found[r.device.address] = FoundPedal(r.device.address, name, r.rssi, now)
            else { f.name = name; f.rssi = r.rssi; f.lastSeen = now }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("PedalScanner", "scan failed $errorCode")
            active = false
        }
    }

    fun start() {
        if (active) return
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(PowerBle.CYCLING_POWER)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
            active = true
        } catch (e: Exception) {
            Log.e("PedalScanner", "startScan", e)
        }
    }

    fun stop() {
        if (!active) return
        try {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {}
        active = false
    }

    /** Elimina pedales no vistos en los últimos [maxAgeMs]. */
    fun prune(maxAgeMs: Long = 12_000) {
        val now = SystemClock.elapsedRealtime()
        found.values.removeAll { now - it.lastSeen > maxAgeMs }
    }
}
