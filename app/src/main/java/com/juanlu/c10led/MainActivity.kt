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
private val METER_UUID: UUID = UUID.fromString("5b8f0004-7a2d-4c61-9b21-7c10c10a0001")
private val VIVO_UUID: UUID = UUID.fromString("5b8f0005-7a2d-4c61-9b21-7c10c10a0001")
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

enum class Conn { DESCONECTADO, BUSCANDO, CONECTANDO, CONECTADO }

class BleController(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val prefs = ctx.getSharedPreferences("c10prefs", Context.MODE_PRIVATE)

    var conn by mutableStateOf(Conn.DESCONECTADO)
    var estado by mutableStateOf<EstadoC?>(null)
    var medidor by mutableIntStateOf(0)
    var vivo by mutableStateOf(IntArray(34))      // vista en vivo de la barra (0..255 por canal)
    var mensaje by mutableStateOf("")
    var registro by mutableStateOf("")
    private val cola = ArrayDeque<String>()
    private var escribiendo = false
    private var tokenEscritura = 0
    private var releido = false

    private fun log(x: String) {
        val h = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        main.post { registro = (h + " " + x + "\n" + registro).take(1800) }
    }

    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var scanning = false

    // Control de intentos: cada "sesion" invalida los temporizadores de la anterior.
    private var sesion = 0
    private var intento = 0
    private var ronda = 0          // cada fase (directo/escaneo/enganche) invalida los temporizadores de la anterior
    private var usuarioDesconecta = false

    private companion object {
        const val MAX_INTENTOS = 3
        const val TIEMPO_DIRECTO_MS = 6_000L     // conexion directa a la ultima centralita conocida
        const val TIEMPO_ESCANEO_MS = 8_000L
        const val TIEMPO_CONEXION_MS = 8_000L    // desde que se encuentra hasta tener servicios
        const val NOMBRE_BLE = "JUANLU_C10LED_BLE"
        const val CLAVE_DIR = "ultima_centralita"
    }

    val bluetoothActivo get() = adapter?.isEnabled == true

    // ------------------------------------------------------------------
    // Conexion
    // ------------------------------------------------------------------
    fun conectar() {
        val ad = adapter ?: run { mensaje = "m_nobt"; return }
        if (!ad.isEnabled) { mensaje = "m_btoff"; return }
        if (ad.bluetoothLeScanner == null) { mensaje = "m_noscan"; return }
        if (conn != Conn.DESCONECTADO) return

        mensaje = ""
        usuarioDesconecta = false
        sesion++
        intento = 1
        empezarIntento(sesion)
    }

    /** Primer intento: directo a la ultima centralita (sin escanear). Si no, escaneo. */
    private fun empezarIntento(s: Int) {
        if (s != sesion) return
        cerrarGatt()
        val dir = prefs.getString(CLAVE_DIR, null)
        if (dir != null && intento == 1) conectarDirecto(dir, s) else buscar(s)
    }

    @SuppressLint("MissingPermission")
    private fun conectarDirecto(dir: String, s: Int) {
        val dev = try { adapter?.getRemoteDevice(dir) } catch (e: IllegalArgumentException) { null }
        if (dev == null) { buscar(s); return }
        conn = Conn.CONECTANDO
        abrirGatt(dev)
        val r = ++ronda
        main.postDelayed({
            // Si en 6 s no hay conexion, se abandona el directo y se busca por escaneo.
            if (s == sesion && r == ronda && conn != Conn.CONECTADO) {
                cerrarGatt()
                buscar(s)
            }
        }, TIEMPO_DIRECTO_MS)
    }

    @SuppressLint("MissingPermission")
    private fun buscar(s: Int) {
        if (s != sesion) return
        val scanner = adapter?.bluetoothLeScanner ?: run { conn = Conn.DESCONECTADO; mensaje = "m_noscan"; return }
        pararScan()
        val r = ++ronda
        conn = Conn.BUSCANDO
        scanning = true
        // Dos filtros (se cumple cualquiera): por servicio y por nombre. Algunos moviles
        // filtran mal los UUID de 128 bits y asi se encuentra igualmente.
        val filtros = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build(),
            ScanFilter.Builder().setDeviceName(NOMBRE_BLE).build()
        )
        val ajustes = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .build()
        try {
            scanner.startScan(filtros, ajustes, scanCallback)
        } catch (e: Exception) {
            scanning = false
            conn = Conn.DESCONECTADO
            mensaje = "m_noscan"
            return
        }
        main.postDelayed({
            if (s == sesion && r == ronda && scanning) {
                pararScan()
                fallo(s)
            }
        }, TIEMPO_ESCANEO_MS)
    }

    @SuppressLint("MissingPermission")
    private fun pararScan() {
        if (!scanning) return
        scanning = false
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (e: Exception) { }
    }

    @SuppressLint("MissingPermission")
    private fun abrirGatt(dev: BluetoothDevice) {
        gatt = dev.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    private fun cerrarGatt() {
        val g = gatt
        gatt = null
        commandChar = null
        cola.clear(); escribiendo = false
        if (g != null) {
            try { g.disconnect() } catch (e: Exception) { }
            try { g.close() } catch (e: Exception) { }
        }
    }

    /** Un intento ha fallado: se reintenta hasta MAX_INTENTOS veces antes de rendirse. */
    private fun fallo(s: Int) {
        if (s != sesion) return
        ronda++
        cerrarGatt()
        if (!usuarioDesconecta && intento < MAX_INTENTOS) {
            intento++
            conn = Conn.BUSCANDO
            main.postDelayed({ buscar(s) }, 400)
        } else {
            conn = Conn.DESCONECTADO
            estado = null
            if (mensaje.isEmpty()) mensaje = "m_notfound"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Solo el primer resultado cuenta; los siguientes (ya en cola) se ignoran.
            if (!scanning) return
            pararScan()
            val s = sesion
            main.post {
                if (s != sesion) return@post
                conn = Conn.CONECTANDO
                abrirGatt(result.device)
                val r = ++ronda
                main.postDelayed({
                    if (s == sesion && r == ronda && conn != Conn.CONECTADO) fallo(s)
                }, TIEMPO_CONEXION_MS)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            val s = sesion
            main.post {
                if (s != sesion) return@post
                mensaje = "m_scanerr:$errorCode"
                fallo(s)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            val s = sesion
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                // Recuerda esta centralita: la proxima vez se conecta directo, sin escanear.
                try { prefs.edit().putString(CLAVE_DIR, g.device.address).apply() } catch (e: Exception) { }
                // Intervalo de conexion corto y descubrimiento de servicios inmediato.
                try { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) } catch (e: Exception) { }
                log("conectado; pidiendo MTU")
                // MTU grande: el estado cabe entero en cada aviso. Los servicios se descubren al responder.
                val pidio = try { g.requestMtu(185) } catch (e: Exception) { false }
                if (!pidio) g.discoverServices()
                main.postDelayed({
                    // Si no se completa el enganche, se corta y se reintenta.
                    if (s == sesion && gatt === g && conn != Conn.CONECTADO) g.disconnect()
                }, TIEMPO_CONEXION_MS)
            } else {
                // Desconexion o error (p. ej. status 133): se cierra y se actua en el hilo principal.
                try { g.close() } catch (e: Exception) { }
                main.post {
                    // Solo importa la conexion actual; avisos de una ya cerrada se ignoran.
                    if (gatt !== g) return@post
                    gatt = null
                    commandChar = null
                    if (s != sesion) return@post
                    if (conn == Conn.CONECTADO) {
                        // Perdida inesperada con la app abierta: reconexion automatica.
                        conn = Conn.DESCONECTADO
                        estado = null
                        if (!usuarioDesconecta) {
                            sesion++
                            intento = 1
                            val s2 = sesion
                            main.postDelayed({ if (s2 == sesion && conn == Conn.DESCONECTADO) empezarIntento(s2) }, 600)
                        }
                    } else if (conn == Conn.CONECTANDO) {
                        // Fallo durante el enganche (p. ej. status 133): siguiente intento.
                        fallo(s)
                    } else {
                        estado = null
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            log("MTU $mtu")
            g.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val servicio = g.getService(SERVICE_UUID)
            val cmd = servicio?.getCharacteristic(COMMAND_UUID)
            val st = servicio?.getCharacteristic(STATUS_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || cmd == null || st == null) {
                // Servicios incompletos (a veces la cache del movil): se corta y se reintenta.
                g.disconnect()
                return
            }
            commandChar = cmd
            g.setCharacteristicNotification(st, true)
            servicio.getCharacteristic(METER_UUID)?.let { g.setCharacteristicNotification(it, true) }
            servicio.getCharacteristic(VIVO_UUID)?.let { g.setCharacteristicNotification(it, true) }
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
            main.post {
                conn = Conn.CONECTADO
                intento = 1
                mensaje = ""
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            // Cadena de avisos: estado -> medidor del sensor -> vista en vivo (si el sketch los tiene) -> leer estado.
            when (d.characteristic.uuid) {
                STATUS_UUID -> if (habilitar(g, METER_UUID) || habilitar(g, VIVO_UUID)) return
                METER_UUID -> if (habilitar(g, VIVO_UUID)) return
            }
            leerEstado(g)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post { escribiendo = false; tokenEscritura++; siguienteEscritura() }
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
            if (Build.VERSION.SDK_INT < 33) {
                if (c.uuid == STATUS_UUID) actualizarEstado(c.value)
                else if (c.uuid == METER_UUID) actualizarMedidor(c.value)
                else if (c.uuid == VIVO_UUID) actualizarVivo(c.value)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray
        ) {
            if (c.uuid == STATUS_UUID) actualizarEstado(value)
            else if (c.uuid == METER_UUID) actualizarMedidor(value)
            else if (c.uuid == VIVO_UUID) actualizarVivo(value)
        }

        @SuppressLint("MissingPermission")
        private fun habilitar(g: BluetoothGatt, uuid: UUID): Boolean {
            val c = g.getService(SERVICE_UUID)?.getCharacteristic(uuid) ?: return false
            val dd = c.getDescriptor(CCCD_UUID) ?: return false
            g.setCharacteristicNotification(c, true)
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(dd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                dd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(dd)
            }
            return true
        }
    }

    @SuppressLint("MissingPermission")
    private fun leerEstado(g: BluetoothGatt) {
        g.getService(SERVICE_UUID)?.getCharacteristic(STATUS_UUID)?.let { g.readCharacteristic(it) }
    }

    private fun actualizarEstado(v: ByteArray?) {
        val txt = v?.toString(Charsets.UTF_8)?.trim() ?: return
        val e = EstadoC.parse(txt)
        main.post {
            if (e != null) estado = e
            // Un aviso cortado (MTU pequeno) no trae la version al final: se relee completo (una sola vez).
            if (e != null && e.v.isNotEmpty()) releido = false
            else if (!releido) { releido = true; gatt?.let { leerEstado(it) } }
        }
    }

    private fun actualizarVivo(v: ByteArray?) {
        if (v == null || v.size < 34) return
        val a = IntArray(34) { v[it].toInt() and 0xFF }
        main.post { vivo = a }
    }

    private fun actualizarMedidor(v: ByteArray?) {
        val n = v?.toString(Charsets.UTF_8)?.trim()?.toIntOrNull() ?: return
        main.post { medidor = n }
    }

    /** Las ordenes van en cola (una sola escritura GATT a la vez). Una orden BR: nueva sustituye a la anterior. */
    fun enviar(cmd: String) {
        main.post {
            if (commandChar == null || gatt == null) return@post
            if (cmd.startsWith("BR:")) cola.removeAll { it.startsWith("BR:") }
            if (cola.size < 40) cola.addLast(cmd)
            log("> $cmd")
            siguienteEscritura()
        }
    }

    fun enviarRapido(cmd: String) = enviar(cmd)

    @SuppressLint("MissingPermission")
    private fun siguienteEscritura() {
        if (escribiendo) return
        val cmd = cola.removeFirstOrNull() ?: return
        val g = gatt ?: return
        val c = commandChar ?: return
        escribiendo = true
        val tk = ++tokenEscritura
        val bytes = cmd.toByteArray(Charsets.UTF_8)
        try {
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
        } catch (e: Exception) { escribiendo = false }
        // Si el movil no confirma la escritura en 1,5 s, se sigue con la siguiente.
        main.postDelayed({ if (escribiendo && tokenEscritura == tk) { escribiendo = false; siguienteEscritura() } }, 1500)
    }

    fun desconectar() {
        usuarioDesconecta = true
        sesion++
        pararScan()
        cerrarGatt()
        conn = Conn.DESCONECTADO
        estado = null
        cola.clear(); escribiendo = false
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var ble: BleController

    companion object {
        @Volatile private var instancia: MainActivity? = null

        /**
         * Android Auto y el widget necesitan la centralita libre (solo admite un movil a la vez):
         * si la app principal esta abierta (aunque sea en segundo plano), se cierra y suelta el Bluetooth.
         * Devuelve true si habia una app abierta.
         */
        fun cerrarPorCoche(): Boolean {
            val a = instancia ?: return false
            a.runOnUiThread { try { a.finishAndRemoveTask() } catch (e: Exception) { } }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instancia = this
        I18n.iniciar(applicationContext)
        ble = BleController(applicationContext)
        Auto.programar(applicationContext)     // alarmas y vigilancia en segundo plano (mejor esfuerzo)
        setContent { C10App(ble) }
    }

    override fun onDestroy() {
        ble.desconectar()
        if (instancia === this) instancia = null
        super.onDestroy()
    }
}

internal fun permisosNecesarios(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

/** Estado de la centralita (texto "NORMAL;n=655;e=15;..." del sketch). */
data class EstadoC(
    val off: Boolean = false, val n: Int = 655, val e: Int = 15, val r: Int = 45, val rn: Int = 314, val rx: Int = 730, val p: Int = 1,
    val k: Int = 5, val w: Int = 1, val d: Int = 655, val m: Boolean = false, val o: Boolean = false,
    val h: Int = 0, val b: Boolean = true, val v: String = ""
) {
    companion object {
        fun parse(txt: String): EstadoC? {
            val partes = txt.trim().split(";")
            if (partes.isEmpty() || (partes[0] != "OFF" && partes[0] != "NORMAL")) return null
            val mp = HashMap<String, String>()
            for (x in partes.drop(1)) {
                val i = x.indexOf('=')
                if (i > 0) mp[x.substring(0, i)] = x.substring(i + 1)
            }
            fun i(k: String, def: Int) = mp[k]?.toIntOrNull() ?: def
            return EstadoC(
                off = partes[0] == "OFF", n = i("n", 655), e = i("e", 15), r = i("r", 45), rn = i("rn", 314), rx = i("rx", 730), p = i("p", 1),
                k = i("k", 5), w = i("w", 1), d = i("d", 655), m = i("m", 0) != 0, o = i("o", 0) != 0,
                h = i("h", 0), b = i("b", 1) != 0, v = mp["v"] ?: ""
            )
        }
    }
}
