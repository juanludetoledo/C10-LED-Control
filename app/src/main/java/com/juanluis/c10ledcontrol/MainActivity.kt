package com.juanluis.c10ledcontrol

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {
    private val serviceUuid = UUID.fromString("5b8f0001-7a2d-4c61-9b21-7c10c10a0001")
    private val commandUuid = UUID.fromString("5b8f0002-7a2d-4c61-9b21-7c10c10a0001")
    private val statusUuid = UUID.fromString("5b8f0003-7a2d-4c61-9b21-7c10c10a0001")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val deviceName = "C10_LED_CONTROL"
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var onButton: Button
    private lateinit var offButton: Button
    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var connected = false
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestNeededPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(18, 22, 26))
        }

        val title = TextView(this).apply {
            text = "C10 LED CONTROL"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Control Bluetooth de la barra LED"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        statusText = TextView(this).apply {
            text = "Desconectado"
            textSize = 17f
            setTextColor(Color.rgb(255, 190, 70))
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(22))
        }
        connectButton = Button(this).apply {
            text = "CONECTAR A LA BARRA"
            setOnClickListener { connectToDevice() }
        }
        onButton = Button(this).apply {
            text = "ENCENDER BARRA"
            textSize = 19f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(28, 135, 72))
            isEnabled = false
            setOnClickListener { sendCommand("ON") }
        }
        offButton = Button(this).apply {
            text = "APAGAR BARRA"
            textSize = 19f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(190, 48, 48))
            isEnabled = false
            setOnClickListener { sendCommand("OFF") }
        }
        val note = TextView(this).apply {
            text = "Conecta con C10_LED_CONTROL y utiliza los botones para enviar ON u OFF."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }

        root.addView(title, matchWrap())
        root.addView(subtitle, matchWrap())
        root.addView(statusText, matchWrap())
        root.addView(connectButton, fullWidth())
        root.addView(onButton, fullWidthWithTop(18))
        root.addView(offButton, fullWidthWithTop(12))
        root.addView(note, matchWrap())
        setContentView(root)
    }

    private fun requestNeededPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    private fun connectToDevice() {
        if (!hasPermissions()) {
            requestNeededPermissions()
            showStatus("Faltan permisos Bluetooth", Color.rgb(255, 190, 70))
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            showStatus("Activa el Bluetooth del móvil", Color.rgb(255, 190, 70))
            return
        }
        if (scanning) return
        disconnectGatt()
        showStatus("Buscando $deviceName…", Color.rgb(255, 190, 70))
        connectButton.isEnabled = false
        scanning = true
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            scanning = false
            connectButton.isEnabled = true
            showStatus("Bluetooth LE no disponible", Color.RED)
            return
        }
        try {
            scanner.startScan(scanCallback)
            handler.postDelayed({
                if (scanning) {
                    try { scanner.stopScan(scanCallback) } catch (_: Exception) {}
                    scanning = false
                    connectButton.isEnabled = true
                    if (!connected) showStatus("No encontrado. Acércate y vuelve a conectar.", Color.rgb(255, 190, 70))
                }
            }, 12000)
        } catch (e: SecurityException) {
            scanning = false
            connectButton.isEnabled = true
            showStatus("Permiso Bluetooth denegado", Color.RED)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = try { device.name ?: result.scanRecord?.deviceName } catch (_: SecurityException) { null }
            if (name == deviceName) {
                val scanner = bluetoothAdapter()?.bluetoothLeScanner
                try { scanner?.stopScan(this) } catch (_: Exception) {}
                scanning = false
                handler.post {
                    showStatus("Dispositivo encontrado. Conectando…", Color.rgb(255, 190, 70))
                    connectGatt(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            handler.post {
                connectButton.isEnabled = true
                showStatus("Error al buscar Bluetooth ($errorCode)", Color.RED)
            }
        }
    }

    private fun connectGatt(device: BluetoothDevice) {
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(this, false, gattCallback)
            }
        } catch (e: SecurityException) {
            connectButton.isEnabled = true
            showStatus("No hay permiso para conectar", Color.RED)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true
                try { g.discoverServices() } catch (_: SecurityException) {}
                handler.post { showStatus("Conectado. Preparando control…", Color.rgb(80, 200, 120)) }
            } else {
                connected = false
                commandCharacteristic = null
                statusCharacteristic = null
                try { g.close() } catch (_: Exception) {}
                if (gatt === g) gatt = null
                handler.post {
                    setButtonsEnabled(false)
                    connectButton.isEnabled = true
                    showStatus("Desconectado", Color.rgb(255, 190, 70))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { showStatus("No se pudieron leer los servicios BLE", Color.RED) }
                return
            }
            val service = g.getService(serviceUuid)
            commandCharacteristic = service?.getCharacteristic(commandUuid)
            statusCharacteristic = service?.getCharacteristic(statusUuid)
            if (service == null || commandCharacteristic == null) {
                handler.post { showStatus("El servicio BLE esperado no está disponible", Color.RED) }
                return
            }
            handler.post {
                setButtonsEnabled(true)
                connectButton.isEnabled = true
                showStatus("Conectado a C10_LED_CONTROL", Color.rgb(80, 200, 120))
            }
            statusCharacteristic?.let { ch ->
                try {
                    g.setCharacteristicNotification(ch, true)
                    val descriptor = ch.getDescriptor(cccdUuid)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(descriptor)
                        }
                    }
                    g.readCharacteristic(ch)
                } catch (_: Exception) {}
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (characteristic.uuid == statusUuid && status == BluetoothGatt.GATT_SUCCESS) {
                updateRemoteStatus(String(value, Charsets.UTF_8))
            }
        }

        @Deprecated("Deprecated in Android API")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == statusUuid &&
                status == BluetoothGatt.GATT_SUCCESS) {
                updateRemoteStatus(String(characteristic.value ?: byteArrayOf(), Charsets.UTF_8))
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == statusUuid) updateRemoteStatus(String(value, Charsets.UTF_8))
        }

        @Deprecated("Deprecated in Android API")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == statusUuid) {
                updateRemoteStatus(String(characteristic.value ?: byteArrayOf(), Charsets.UTF_8))
            }
        }
    }

    private fun updateRemoteStatus(value: String) {
        val clean = value.trim().uppercase()
        handler.post {
            when (clean) {
                "OFF" -> showStatus("Conectado · BARRA APAGADA", Color.rgb(255, 110, 110))
                "NORMAL" -> showStatus("Conectado · CONTROL NORMAL", Color.rgb(80, 200, 120))
                else -> showStatus("Conectado · Estado: $clean", Color.rgb(80, 200, 120))
            }
        }
    }

    private fun sendCommand(command: String) {
        if (!hasPermissions()) {
            requestNeededPermissions()
            return
        }
        val g = gatt
        val ch = commandCharacteristic
        if (!connected || g == null || ch == null) {
            showStatus("No conectado. Pulsa CONECTAR.", Color.rgb(255, 190, 70))
            setButtonsEnabled(false)
            return
        }
        val bytes = command.toByteArray(Charsets.UTF_8)
        try {
            val result = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                ch.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            if (result == BluetoothGatt.GATT_SUCCESS) {
                showStatus(if (command == "OFF") "Orden de apagado enviada…" else "Orden de encendido enviada…", Color.rgb(255, 190, 70))
            } else {
                showStatus("No se pudo enviar la orden ($result)", Color.RED)
            }
        } catch (e: SecurityException) {
            showStatus("Permiso Bluetooth denegado", Color.RED)
        }
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun disconnectGatt() {
        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        gatt = null
        connected = false
        commandCharacteristic = null
        statusCharacteristic = null
        setButtonsEnabled(false)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        onButton.isEnabled = enabled
        offButton.isEnabled = enabled
    }

    private fun showStatus(text: String, color: Int) {
        statusText.text = text
        statusText.setTextColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun fullWidth() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62))
    private fun fullWidthWithTop(top: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(68)).apply { topMargin = dp(top) }

    override fun onDestroy() {
        if (scanning) {
            try { bluetoothAdapter()?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
        }
        disconnectGatt()
        super.onDestroy()
    }
}
