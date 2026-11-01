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
        val ad = adapter ?: run { mensaje = "Este móvil no tiene Bluetooth"; return }
        if (!ad.isEnabled) { mensaje = "Activa el Bluetooth"; return }
        val scanner = ad.bluetoothLeScanner ?: run { mensaje = "Escáner BLE no disponible"; return }
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
                mensaje = "No se encontró C10_LED_CONTROL"
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
            main.post { conn = Conn.DESCONECTADO; mensaje = "Error de escaneo ($errorCode)" }
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
                main.post { mensaje = "Servicio BLE no encontrado"; }
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
        ble = BleController(applicationContext)
        setContent { C10App(ble) }
    }

    override fun onDestroy() {
        ble.desconectar()
        super.onDestroy()
    }
}

private fun permisosNecesarios(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun C10App(ble: BleController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.all { it }) ble.conectar()
        else ble.mensaje = "Faltan permisos de Bluetooth"
    }

    fun pedirYConectar() {
        val faltan = permisosNecesarios().any {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltan) launcher.launch(permisosNecesarios()) else ble.conectar()
    }

    val verde = Color(0xFF16A34A)
    val rojo = Color(0xFFDC2638)
    val conectado = ble.conn == Conn.CONECTADO
    val apagada = ble.estadoBarra == "OFF"

    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF080C14), surface = Color(0xFF111827))) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080C14)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("C10 LED CONTROL", color = Color(0xFF38BDF8), fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text("Barra LED", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))

                val etiqueta = when (ble.conn) {
                    Conn.DESCONECTADO -> "Desconectado"
                    Conn.BUSCANDO -> "Buscando centralita…"
                    Conn.CONECTANDO -> "Conectando…"
                    Conn.CONECTADO -> if (apagada) "Conectado · barra APAGADA" else "Conectado · modo NORMAL"
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(
                        when {
                            !conectado -> Color(0xFF1F2937)
                            apagada -> Color(0xFF3B1720)
                            else -> Color(0xFF123021)
                        }
                    ).padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(etiqueta, color = Color(0xFFE2E8F0), fontSize = 13.sp, fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(32.dp))

                if (!conectado) {
                    Button(
                        onClick = { pedirYConectar() },
                        enabled = ble.conn == Conn.DESCONECTADO,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("CONECTAR", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                } else {
                    Button(
                        onClick = { ble.enviar("ON") },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = verde)
                    ) { Text("ENCENDER", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { ble.enviar("OFF") },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = rojo)
                    ) { Text("APAGAR", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }

                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = { ble.desconectar() }) { Text("Desconectar") }
                }

                if (ble.mensaje.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(ble.mensaje, color = Color(0xFFFBBF24), fontSize = 13.sp)
                }
            }
        }
    }
}
