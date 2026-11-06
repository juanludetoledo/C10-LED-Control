package com.juanlu.c10led

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Aplicacion automatica de la programacion dia/noche en segundo plano (MEJOR ESFUERZO).
 *  - Alarma exacta en cada cambio de periodo.
 *  - Vigilancia BLE de bajo consumo: cuando la centralita aparece (coche encendido) se conecta un
 *    instante, aplica el brillo del periodo actual y se desconecta.
 * Si el sistema del movil detiene la app en segundo plano, no pasa nada: se aplica al abrirla.
 */
object Auto {
    const val ACC_ALARMA = "com.juanlu.c10led.ALARMA"
    const val ACC_SCAN = "com.juanlu.c10led.SCAN"
    val SERVICIO: UUID = UUID.fromString("5b8f0001-7a2d-4c61-9b21-7c10c10a0001")

    private fun tienePermisoBt(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun puedeAlarmaExacta(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    @SuppressLint("MissingPermission")
    fun programar(ctx: Context) {
        try { programarAlarma(ctx) } catch (e: Exception) { }
        try { vigilar(ctx) } catch (e: Exception) { }
    }

    private fun programarAlarma(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 10, Intent(ctx, AutoReceiver::class.java).setAction(ACC_ALARMA),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val p = Prog.cargar(ctx)
        if (!p.activa) { am.cancel(pi); return }
        val cuando = p.proximoCambio()
        if (puedeAlarmaExacta(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
    }

    @SuppressLint("MissingPermission")
    private fun vigilar(ctx: Context) {
        if (!tienePermisoBt(ctx)) return
        val ad = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        if (!ad.isEnabled) return
        val sc = ad.bluetoothLeScanner ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, 11, Intent(ctx, AutoReceiver::class.java).setAction(ACC_SCAN),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        )
        val p = Prog.cargar(ctx)
        if (!p.activa) { try { sc.stopScan(pi) } catch (e: Exception) { }; return }
        val filtros = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICIO)).build())
        val aj = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .build()
        sc.startScan(filtros, aj, pi)
    }
}

class AutoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> { Auto.programar(app); return }
            Auto.ACC_ALARMA -> Auto.programar(app)           // deja preparada la siguiente
            Auto.ACC_SCAN -> { }
            else -> return
        }
        val p = Prog.cargar(app)
        if (!p.activa) return
        val sp = app.getSharedPreferences("c10prefs", Context.MODE_PRIVATE)
        val ahora = System.currentTimeMillis()
        if (intent.action == Auto.ACC_SCAN && ahora - sp.getLong("bg_ultimo", 0L) < 30_000L) return
        sp.edit().putLong("bg_ultimo", ahora).apply()
        val pend = goAsync()
        AplicadorBle(app) { try { pend.finish() } catch (e: Exception) { } }.ejecutar()
    }
}

/** Conexion corta: conecta, lee el estado, aplica el brillo del periodo y se desconecta. */
class AplicadorBle(private val ctx: Context, private val fin: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var terminado = false
    private var cmdChar: BluetoothGattCharacteristic? = null
    private var orden = ""

    @SuppressLint("MissingPermission")
    fun ejecutar() {
        val dir = ctx.getSharedPreferences("c10prefs", Context.MODE_PRIVATE).getString("ultima_centralita", null)
        if (dir == null) { terminar(); return }
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { terminar(); return }
        val ad = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (ad == null || !ad.isEnabled) { terminar(); return }
        orden = Prog.cargar(ctx).ordenActual()
        val dev = try { ad.getRemoteDevice(dir) } catch (e: Exception) { null }
        if (dev == null) { terminar(); return }
        gatt = dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)
        main.postDelayed({ terminar() }, 12_000)
    }

    @SuppressLint("MissingPermission")
    private fun terminar() {
        if (terminado) return
        terminado = true
        try { gatt?.disconnect() } catch (e: Exception) { }
        try { gatt?.close() } catch (e: Exception) { }
        gatt = null
        fin()
    }

    private val cb = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) g.discoverServices()
            else main.post { terminar() }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val sv = g.getService(Auto.SERVICIO)
            val st = sv?.getCharacteristic(UUID.fromString("5b8f0003-7a2d-4c61-9b21-7c10c10a0001"))
            cmdChar = sv?.getCharacteristic(UUID.fromString("5b8f0002-7a2d-4c61-9b21-7c10c10a0001"))
            if (st == null || cmdChar == null) { main.post { terminar() }; return }
            g.readCharacteristic(st)
        }

        private fun decidir(g: BluetoothGatt, v: ByteArray?) {
            val e = v?.toString(Charsets.UTF_8)?.let { EstadoC.parse(it) }
            // Apagada o con un show en marcha: no se toca nada.
            if (e == null || e.off || e.h > 0) { main.post { terminar() }; return }
            escribir(g, orden)
        }

        @SuppressLint("MissingPermission")
        private fun escribir(g: BluetoothGatt, cmd: String) {
            val c = cmdChar ?: run { main.post { terminar() }; return }
            val bytes = cmd.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION") c.value = bytes
                @Suppress("DEPRECATION") c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION") g.writeCharacteristic(c)
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            decidir(g, value)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Android < 13")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) decidir(g, c.value)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.postDelayed({ terminar() }, 300)
        }
    }
}
