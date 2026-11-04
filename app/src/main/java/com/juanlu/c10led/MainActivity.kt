package com.juanlu.c10led

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.UUID

// ---- UUIDs del sketch del ESP32-S3 ----
private val SERVICE_UUID: UUID = UUID.fromString("5b8f0001-7a2d-4c61-9b21-7c10c10a0001")
private val COMMAND_UUID: UUID = UUID.fromString("5b8f0002-7a2d-4c61-9b21-7c10c10a0001")
private val STATUS_UUID: UUID = UUID.fromString("5b8f0003-7a2d-4c61-9b21-7c10c10a0001")
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class Conn { DESCONECTADO, BUSCANDO, CONECTANDO, CONECTADO }

class BleController(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    var conn by mutableStateOf(Conn.DESCONECTADO)
    var estadoBarra by mutableStateOf("?")
    var mensaje by mutableStateOf("")

    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var scanning = false

    val bluetoothActivo get() = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun conectar() {
        val ad = adapter ?: run { mensaje = "m_nobt"; return }
        if (!ad.isEnabled) { mensaje = "m_btoff"; return }
        val scanner = ad.bluetoothLeScanner ?: run { mensaje = "m_noscan"; return }
        if (scanning || conn == Conn.CONECTADO) return

        mensaje = ""
        conn = Conn.BUSCANDO
        scanning = true
        val filtros = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val ajustes = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filtros, ajustes, scanCallback)

        main.postDelayed({
            if (scanning) {
                pararScan()
                conn = Conn.DESCONECTADO
                mensaje = "m_notfound"
            }
        }, 10_000)
    }

    @SuppressLint("MissingPermission")
    private fun pararScan() {
        if (!scanning) return
        scanning = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            pararScan()
            main.post {
                conn = Conn.CONECTANDO
                gatt = result.device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            main.post { conn = Conn.DESCONECTADO; mensaje = "m_scanerr:$errorCode" }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else {
                g.close()
                gatt = null
                commandChar = null
                main.post { conn = Conn.DESCONECTADO; estadoBarra = "?" }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val servicio = g.getService(SERVICE_UUID)
            commandChar = servicio?.getCharacteristic(COMMAND_UUID)
            val st = servicio?.getCharacteristic(STATUS_UUID)
            if (commandChar == null || st == null) {
                main.post { mensaje = "m_nosvc"; }
                g.disconnect()
                return
            }
            g.setCharacteristicNotification(st, true)
            st.getDescriptor(CCCD_UUID)?.let { d ->
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(d)
                }
            }
            main.post { conn = Conn.CONECTADO }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            // Tras activar notificaciones, leemos el estado actual.
            leerEstado(g)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Android < 13")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33 && c.uuid == STATUS_UUID) actualizarEstado(c.value)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int
        ) {
            if (c.uuid == STATUS_UUID) actualizarEstado(value)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Android < 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33 && c.uuid == STATUS_UUID) actualizarEstado(c.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray
        ) {
            if (c.uuid == STATUS_UUID) actualizarEstado(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun leerEstado(g: BluetoothGatt) {
        g.getService(SERVICE_UUID)?.getCharacteristic(STATUS_UUID)?.let { g.readCharacteristic(it) }
    }

    private fun actualizarEstado(v: ByteArray?) {
        val txt = v?.toString(Charsets.UTF_8)?.trim()?.uppercase() ?: "?"
        main.post { estadoBarra = txt }
    }

    @SuppressLint("MissingPermission")
    fun enviar(cmd: String) {
        val g = gatt ?: return
        val c = commandChar ?: return
        val bytes = cmd.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            c.value = bytes
            @Suppress("DEPRECATION")
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    /** Escritura sin respuesta: para el deslizador de brillo (muchas ordenes seguidas). */
    @SuppressLint("MissingPermission")
    fun enviarRapido(cmd: String) {
        val g = gatt ?: return
        val c = commandChar ?: return
        val bytes = cmd.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            c.value = bytes
            @Suppress("DEPRECATION")
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    @SuppressLint("MissingPermission")
    fun desconectar() {
        pararScan()
        gatt?.disconnect()
        if (gatt == null) conn = Conn.DESCONECTADO
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var ble: BleController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.iniciar(applicationContext)
        ble = BleController(applicationContext)
        setContent { C10App(ble) }
    }

    override fun onDestroy() {
        ble.desconectar()
        super.onDestroy()
    }
}

internal fun permisosNecesarios(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
