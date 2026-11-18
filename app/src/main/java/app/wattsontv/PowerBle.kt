package app.wattsontv

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.UUID

/** Conecta a un pedal concreto, se suscribe a Cycling Power Measurement y reconecta solo si se cae. */
@SuppressLint("MissingPermission")
class PowerBle(
    private val ctx: Context,
    private val address: String,
    private val averager: PowerAverager,
    private val onState: (String, Boolean) -> Unit, // (texto, conectado)
) {
    companion object {
        private const val TAG = "PowerBle"
        val CYCLING_POWER: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        val POWER_MEASUREMENT: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var stopped = true

    private val adapter: BluetoothAdapter?
        get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    fun start() {
        stopped = false
        connect(auto = false)
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        gatt?.let { try { it.disconnect() } catch (_: Exception) {}; try { it.close() } catch (_: Exception) {} }
        gatt = null
        onState("Desconectado", false)
    }

    private fun connect(auto: Boolean) {
        if (stopped || gatt != null) return
        val a = adapter
        if (a == null || !a.isEnabled) {
            onState("Bluetooth apagado", false)
            handler.postDelayed({ connect(auto) }, 3000)
            return
        }
        onState(if (auto) "Esperando al pedal…" else "Conectando…", false)
        try {
            gatt = a.getRemoteDevice(address).connectGatt(ctx, auto, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(TAG, "connectGatt", e)
            handler.postDelayed({ connect(true) }, 3000)
        }
    }

    private fun dropGatt(g: BluetoothGatt) {
        handler.post {
            try { g.close() } catch (_: Exception) {}
            if (gatt === g) gatt = null
            if (stopped) return@post
            onState("Reconectando…", false)
            handler.postDelayed({ connect(true) }, 1500)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "state=$newState status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.discoverServices()
            } else {
                dropGatt(g)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(CYCLING_POWER)?.getCharacteristic(POWER_MEASUREMENT)
            if (status != BluetoothGatt.GATT_SUCCESS || ch == null) {
                Log.e(TAG, "Servicio 0x1818 no disponible (status=$status)")
                g.disconnect()
                return
            }
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CCCD)
            @Suppress("DEPRECATION")
            run {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "Notificaciones activadas status=$status")
            handler.post { onState("Conectado", status == BluetoothGatt.GATT_SUCCESS) }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid != POWER_MEASUREMENT) return
            @Suppress("DEPRECATION")
            val w = PowerParser.parse(ch.value) ?: return
            averager.add(SystemClock.elapsedRealtime(), w)
        }
    }
}
