package com.juanlu.c10led

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Envio puntual de una orden: conecta a la ultima centralita conocida, escribe y se desconecta.
 * Lo usan el widget y la pantalla del coche. Si la app principal esta conectada (la centralita
 * solo admite un movil a la vez), la conexion falla y se avisa con ok = false.
 */
class EnviarBle(private val ctx: Context, private val ordenes: List<String>, private val fin: (Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var terminado = false
    private var cmd: BluetoothGattCharacteristic? = null
    private var indice = 0
    private var ok = false

    @SuppressLint("MissingPermission")
    fun ejecutar() {
        val dir = ctx.getSharedPreferences("c10prefs", Context.MODE_PRIVATE).getString("ultima_centralita", null)
        if (dir == null) { terminar(); return }
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { terminar(); return }
        val ad = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (ad == null || !ad.isEnabled) { terminar(); return }
        val dev = try { ad.getRemoteDevice(dir) } catch (e: Exception) { null }
        if (dev == null) { terminar(); return }
        // Si la app principal estaba abierta se cierra primero (la centralita solo admite un movil).
        val espera = if (MainActivity.cerrarPorCoche()) 900L else 0L
        main.postDelayed({ if (!terminado) gatt = dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) }, espera)
        main.postDelayed({ terminar() }, 10_000 + espera)
    }

    @SuppressLint("MissingPermission")
    private fun terminar() {
        if (terminado) return
        terminado = true
        try { gatt?.disconnect() } catch (e: Exception) { }
        try { gatt?.close() } catch (e: Exception) { }
        gatt = null
        fin(ok)
    }

    @SuppressLint("MissingPermission")
    private fun siguiente(g: BluetoothGatt) {
        if (indice >= ordenes.size) { ok = true; main.postDelayed({ terminar() }, 250); return }
        val c = cmd ?: run { terminar(); return }
        val bytes = ordenes[indice++].toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION") c.value = bytes
            @Suppress("DEPRECATION") c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION") g.writeCharacteristic(c)
        }
    }

    private val cb = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) g.discoverServices()
            else main.post { terminar() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            cmd = g.getService(Auto.SERVICIO)?.getCharacteristic(UUID.fromString("5b8f0002-7a2d-4c61-9b21-7c10c10a0001"))
            if (cmd == null) main.post { terminar() } else main.post { siguiente(g) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post { if (status == BluetoothGatt.GATT_SUCCESS) siguiente(g) else terminar() }
        }
    }
}
