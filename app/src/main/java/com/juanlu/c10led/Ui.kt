package com.juanlu.c10led

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Bg = Color(0xFF080C14)
private val Muted = Color(0xFF9BA9BD)
private val Cyan = Color(0xFF38BDF8)
private val Line = Color(0x2B94A3B8)

@Composable
fun C10App(ble: BleController) {
    val ctx = LocalContext.current

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

    // Conectar automaticamente al abrir la app.
    LaunchedEffect(Unit) { pedirYConectar() }

    val conectado = ble.conn == Conn.CONECTADO
    val apagada = ble.estadoBarra == "OFF"

    val pillTexto = when {
        ble.conn == Conn.BUSCANDO -> "BUSCANDO"
        ble.conn == Conn.CONECTANDO -> "CONECTANDO"
        !conectado -> "SIN CONEXIÓN"
        apagada -> "BARRA APAGADA"
        else -> "MODO NORMAL"
    }
    val pillColorTexto = when {
        !conectado -> Color(0xFFCBD5E1)
        apagada -> Color(0xFFFF8790)
        else -> Color(0xFF86EFAC)
    }
    val pillFondo = when {
        !conectado -> Color(0xFF1F2937)
        apagada -> Color(0xFF3B1720)
        else -> Color(0xFF123021)
    }
    val estadoTexto = when {
        ble.conn == Conn.BUSCANDO -> "Buscando la centralita…"
        ble.conn == Conn.CONECTANDO -> "Conectando…"
        !conectado -> "No conectado a la centralita"
        apagada -> "ESTADO: BARRA APAGADA"
        else -> "ESTADO: MODO NORMAL"
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 22.dp)
            ) {
                // ---- Cabecera ----
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 22.dp)) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF2563EB)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ShowChart, null, tint = Color.White, modifier = Modifier.size(29.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text("JUANLUDETOLEDO · GARAGE", color = Cyan, fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text("C10 LED Control", color = Color(0xFFF4F7FB), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Centralita de iluminación · ESP32-S3", color = Muted, fontSize = 13.sp)
                    }
                }

                // ---- Tarjeta de control ----
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF131D2E), Color(0xFF0C121E))))
                        .border(1.dp, Line, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CONTROL RÁPIDO", color = Muted, fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(pillFondo)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(pillColorTexto))
                            Spacer(Modifier.width(7.dp))
                            Text(pillTexto, color = pillColorTexto, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Spacer(Modifier.height(17.dp))
                    Text("Control de la barra", color = Color(0xFFF4F7FB), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Apaga toda la barra o devuelve la centralita a su funcionamiento normal.",
                        color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(19.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        BigButton(
                            Modifier.weight(1f), "OFF · APAGAR BARRA", "Bloqueo manual",
                            Color(0xFFFF5967), Color(0xFFC81E3A), Icons.Filled.Close, conectado
                        ) { ble.enviar("OFF") }
                        BigButton(
                            Modifier.weight(1f), "ON · MODO NORMAL", "Reactivar control",
                            Color(0xFF2BD576), Color(0xFF12844A), Icons.Filled.PowerSettingsNew, conectado
                        ) { ble.enviar("ON") }
                    }

                    Spacer(Modifier.height(15.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                            .background(Color(0xFF0A111D))
                            .border(1.dp, Line, RoundedCornerShape(13.dp))
                            .padding(12.dp)
                    ) {
                        Text("Estado de la centralita", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(estadoTexto, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (ble.mensaje.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(ble.mensaje, color = Color(0xFFFBBF24), fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("El bloqueo manual se mantiene hasta que pulses MODO NORMAL.",
                            color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }

                    if (!conectado) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { pedirYConectar() },
                            enabled = ble.conn == Conn.DESCONECTADO,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(13.dp)
                        ) { Text("CONECTAR", fontWeight = FontWeight.ExtraBold) }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("C10 LED CONTROL · BLUETOOTH", color = Color(0xFF64748B), fontSize = 10.sp,
                    letterSpacing = 0.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BigButton(
    modifier: Modifier,
    titulo: String,
    subtitulo: String,
    c1: Color,
    c2: Color,
    icono: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(108.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(c1, c2)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            Icon(icono, null, tint = Color.White, modifier = Modifier.size(27.dp))
            Spacer(Modifier.height(9.dp))
            Text(titulo, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(subtitulo, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
