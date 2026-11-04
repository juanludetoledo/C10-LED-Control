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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlin.math.pow
import kotlin.math.roundToInt
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
        else ble.mensaje = "m_perm"
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
        ble.conn == Conn.BUSCANDO -> t("p_search")
        ble.conn == Conn.CONECTANDO -> t("p_connecting")
        !conectado -> t("p_nocon")
        apagada -> t("p_off")
        else -> t("p_normal")
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
        ble.conn == Conn.BUSCANDO -> t("s_search")
        ble.conn == Conn.CONECTANDO -> t("s_connecting")
        !conectado -> t("s_nocon")
        apagada -> t("s_off")
        else -> t("s_normal")
    }

    // ---- Estado de la prueba de brillo (solo en la app; el sketch no guarda nada) ----
    var brillo by rememberSaveable { mutableStateOf(655) }       // escala 0..1000
    var valorDia by rememberSaveable { mutableStateOf(900) }
    var ultimoEnvio by remember { mutableStateOf(0L) }

    fun aplicar(v: Int, forzar: Boolean = true) {
        val n = v.coerceIn(0, 1000)
        brillo = n
        val ahora = System.currentTimeMillis()
        if (forzar || ahora - ultimoEnvio >= 60) {
            ultimoEnvio = ahora
            ble.enviarRapido("BR:$n")
        }
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
                // ---- Selector de idioma ----
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    I18n.idiomas.forEach { (cod, nombre) ->
                        val activo = I18n.codigo == cod
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (activo) Color(0xFF0C4A6E) else Color(0xFF0D1421))
                                .border(1.dp, if (activo) Cyan else Line, RoundedCornerShape(10.dp))
                                .clickable { I18n.poner(ctx, cod) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(nombre, color = if (activo) Color(0xFFE0F2FE) else Muted,
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

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
                        Text(t("sub"), color = Muted, fontSize = 13.sp)
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
                        Text(t("quick"), color = Muted, fontSize = 11.sp,
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
                    Text(t("hero"), color = Color(0xFFF4F7FB), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text(t("desc"),
                        color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(19.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        BigButton(
                            Modifier.weight(1f), t("off_t"), t("off_s"),
                            Color(0xFFFF5967), Color(0xFFC81E3A), Icons.Filled.Close, conectado
                        ) { ble.enviar("OFF") }
                        BigButton(
                            Modifier.weight(1f), t("on_t"), t("on_s"),
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
                        Text(t("st_title"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(estadoTexto, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (ble.mensaje.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(I18n.tm(ble.mensaje), color = Color(0xFFFBBF24), fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(t("st_hold"),
                            color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }

                    if (!conectado) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { pedirYConectar() },
                            enabled = ble.conn == Conn.DESCONECTADO,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(13.dp)
                        ) { Text(t("connect"), fontWeight = FontWeight.ExtraBold) }
                    }
                }

                Spacer(Modifier.height(14.dp))
                TarjetaBrillo(
                    conectado = conectado,
                    brillo = brillo,
                    valorDia = valorDia,
                    onCambio = { aplicar(it, forzar = false) },
                    onFinal = { aplicar(brillo, forzar = true) },
                    onPaso = { aplicar(brillo + it) },
                    onNoche = { brillo = 655; ble.enviar("NOCHE") },
                    onDia = { aplicar(valorDia) },
                    onFijarDia = { valorDia = brillo },
                    onDiaPaso = { valorDia = (valorDia + it).coerceIn(0, 1000) }
                )

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


/** Convierte la escala 0..1000 al PWM de 12 bits, igual que el sketch de prueba. */
private fun pwmDe(v: Int): Int {
    if (v == 655) return 0x0650   // 655 = valor de noche exacto (igual que en el sketch)
    val p = (4095.0 * (v / 1000.0).pow(2.2)).roundToInt()
    return p.coerceIn(3, 0x0FFF)
}

@Composable
private fun TarjetaBrillo(
    conectado: Boolean,
    brillo: Int,
    valorDia: Int,
    onCambio: (Int) -> Unit,
    onFinal: () -> Unit,
    onPaso: (Int) -> Unit,
    onNoche: () -> Unit,
    onDia: () -> Unit,
    onFijarDia: () -> Unit,
    onDiaPaso: (Int) -> Unit
) {
    val pwm = pwmDe(brillo)
    val hex = "0x" + pwm.toString(16).uppercase().padStart(4, '0')
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (conectado) 1f else 0.55f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF131D2E), Color(0xFF0C121E))))
            .border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(t("b_eyebrow"), color = Muted, fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        Text(t("b_title"), color = Color(0xFFF4F7FB), fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(t("b_desc"),
            color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(16.dp))

        // Lectura
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom) {
            Column {
                Text(t("b_scale"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("$brillo", color = Cyan, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(t("b_level"), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(String.format("%.1f", brillo / 10.0), color = Color(0xFFF4F7FB), fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold)
                Text("HEX $hex  ·  PWM $pwm", color = Color(0xFFE2E8F0), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold)
            }
        }

        Slider(
            value = brillo.toFloat(),
            onValueChange = { onCambio(it.roundToInt()) },
            onValueChangeFinished = onFinal,
            valueRange = 0f..1000f,
            enabled = conectado
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PasoBtn(Modifier.weight(1f), "−−", conectado) { onPaso(-10) }
            PasoBtn(Modifier.weight(1f), "−", conectado) { onPaso(-1) }
            PasoBtn(Modifier.weight(1f), "+", conectado) { onPaso(1) }
            PasoBtn(Modifier.weight(1f), "++", conectado) { onPaso(10) }
        }
        Spacer(Modifier.height(4.dp))
        Text(t("b_hint"), color = Muted, fontSize = 11.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Button(
                onClick = onNoche, enabled = conectado,
                modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(13.dp)
            ) { Text(t("b_night") + "\n0x0650 · 655", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, textAlign = TextAlign.Center) }
            Button(
                onClick = onDia, enabled = conectado,
                modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(13.dp)
            ) { Text(t("b_day") + "\n$valorDia", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, textAlign = TextAlign.Center) }
        }

        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF0A111D))
                .border(1.dp, Line, RoundedCornerShape(13.dp))
                .padding(12.dp)
        ) {
            Text(t("b_dayval"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("$valorDia  ·  ${t("b_lvl")} ${String.format("%.1f", valorDia / 10.0)}  ·  0x" +
                pwmDe(valorDia).toString(16).uppercase().padStart(4, '0'),
                color = Color(0xFFE2E8F0), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PasoBtn(Modifier.weight(1f), "−", true) { onDiaPaso(-1) }
                PasoBtn(Modifier.weight(1f), "+", true) { onDiaPaso(1) }
                Button(onClick = onFijarDia, modifier = Modifier.weight(2.2f).height(44.dp),
                    shape = RoundedCornerShape(11.dp)) {
                    Text(t("b_use"), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun PasoBtn(modifier: Modifier, texto: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(0.dp)) {
        Text(texto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}
