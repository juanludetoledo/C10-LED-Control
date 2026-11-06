package com.juanlu.c10led

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt

private val Bg = Color(0xFF080C14)
private val Muted = Color(0xFF9BA9BD)
private val Cyan = Color(0xFF38BDF8)
private val Line = Color(0x2B94A3B8)
private val Txt = Color(0xFFF4F7FB)

/** PWM de una escala 0..1000 (igual que el sketch: 655 = 0x0650 exacto). */
private fun pwmDe(v: Int): Int =
    if (v == 655) 0x0650 else (4095.0 * (v / 1000.0).pow(2.2)).roundToInt().coerceIn(3, 4095)

private data class Ajustes(val e: Int, val r: Int, val p: Int, val k: Int, val w: Int, val d: Int)

@Composable
fun C10App(ble: BleController) {
    val ctx = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) { ble.conectar(); Auto.programar(ctx.applicationContext) } else ble.mensaje = "m_perm"
    }
    fun pedirYConectar() {
        val faltan = permisosNecesarios().any { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (faltan) launcher.launch(permisosNecesarios()) else ble.conectar()
    }
    LaunchedEffect(Unit) { pedirYConectar() }

    val conectado = ble.conn == Conn.CONECTADO
    val est = ble.estado
    val apagada = est?.off == true
    val showOn = (est?.h ?: 0) > 0

    var pestana by rememberSaveable { mutableStateOf(0) }
    var tecnica by rememberSaveable { mutableStateOf(false) }
    var toques by remember { mutableStateOf(listOf<Long>()) }

    // ---- Programacion (por movil) ----
    var prog by remember { mutableStateOf(Prog.cargar(ctx)) }
    fun cambiarProg(p: Prog) { prog = p; Prog.guardar(ctx, p); Auto.programar(ctx.applicationContext) }
    // Ubicacion para el modo sol: se refresca al abrir la app y al activar el modo
    fun refrescarUbicacion() { Sol.actualizar(ctx) { prog = Prog.cargar(ctx).copy(); Auto.programar(ctx.applicationContext) } }
    val lanzaUbic = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refrescarUbicacion() }
    LaunchedEffect(prog.sol) { if (prog.sol) { if (Sol.tienePermiso(ctx)) refrescarUbicacion() else lanzaUbic.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) } }

    // ---- Brillo ----
    var brillo by rememberSaveable { mutableStateOf(655) }
    var arrastrando by remember { mutableStateOf(false) }
    LaunchedEffect(est?.n, arrastrando) { if (!arrastrando && est != null) brillo = est.n }
    var ultimoEnvio by remember { mutableStateOf(0L) }
    fun poner(v: Int, forzar: Boolean) {
        val n = v.coerceIn(0, 1000)
        brillo = n
        val ahora = System.currentTimeMillis()
        if (forzar || ahora - ultimoEnvio >= 60) { ultimoEnvio = ahora; ble.enviar("BR:$n") }
    }

    // ---- Aplicacion automatica con la app abierta ----
    // Al conectar (y al cambiar de periodo, y al acabar un show) se aplica el brillo del periodo actual
    // con expansion. Un brillo manual manda hasta el siguiente cambio de periodo.
    val progActual by rememberUpdatedState(prog)
    LaunchedEffect(conectado, prog.activa, prog.diaDesde, prog.nocheDesde, prog.sol, prog.lat, prog.lon) {
        var perAplicado: Boolean? = null
        var vistoShow = false
        while (conectado) {
            val e = ble.estado
            if (e != null && !e.off) {
                if (e.h > 0) vistoShow = true
                else if (progActual.activa) {
                    val dia = progActual.esDiaAhora()
                    if (dia != perAplicado || vistoShow) {
                        perAplicado = dia
                        vistoShow = false
                        ble.enviar(progActual.ordenActual())
                    }
                }
            }
            delay(2000)
        }
    }

    // ---- Ajustes en edicion ----
    var aj by remember { mutableStateOf<Ajustes?>(null) }
    LaunchedEffect(est != null) {
        val e = ble.estado
        if (aj == null && e != null) aj = Ajustes(e.e, e.r, e.p, e.k, e.w, e.d)
    }
    var avisoAj by remember { mutableStateOf("") }
    var confirmarReset by remember { mutableStateOf(false) }
    var recordarOff by remember { mutableStateOf(ctx.getSharedPreferences("c10prefs", 0).getBoolean("rem_off", false)) }
    var duracionShow by rememberSaveable { mutableStateOf(0) }

    // Medidor del sensor: se pide mientras se ve la pestana de ajustes o la pantalla tecnica.
    LaunchedEffect(conectado, pestana, tecnica) {
        while (conectado && (pestana == 2 || tecnica)) { ble.enviar("METER:1"); delay(8000) }
    }

    val pillTexto = when {
        ble.conn == Conn.BUSCANDO -> t("p_search")
        ble.conn == Conn.CONECTANDO -> t("p_conn")
        !conectado -> t("p_nocon")
        apagada -> t("p_off")
        showOn -> t("p_show")
        else -> t("p_normal")
    }
    val pillColor = when {
        !conectado -> Color(0xFFCBD5E1)
        apagada -> Color(0xFFFF8790)
        showOn -> Color(0xFFFDE68A)
        else -> Color(0xFF86EFAC)
    }
    val pillFondo = when {
        !conectado -> Color(0xFF1F2937)
        apagada -> Color(0xFF3B1720)
        showOn -> Color(0xFF3A2A08)
        else -> Color(0xFF123021)
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                // ---- Idiomas ----
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    I18n.idiomas.forEach { (cod, nombre) ->
                        Chip(nombre, I18n.codigo == cod) { I18n.poner(ctx, cod) }
                    }
                }
                // ---- Cabecera (5 toques en el titulo = pantalla tecnica) ----
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF2563EB)))),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.ShowChart, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("JUANLU · C10 LED", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text(
                            "C10 LED Control", color = Txt, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                                val ahora = System.currentTimeMillis()
                                val l = toques.filter { ahora - it < 3000 } + ahora
                                if (l.size >= 5) { tecnica = !tecnica; toques = emptyList() } else toques = l
                            }
                        )
                        Text(t("sub"), color = Muted, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (ble.mensaje.isNotEmpty() && !conectado) {
                        Text(I18n.tm(ble.mensaje), color = Color(0xFFFBBF24), fontSize = 11.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(pillFondo).padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(pillColor))
                        Spacer(Modifier.width(7.dp))
                        Text(pillTexto, color = pillColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                if (!conectado) {
                    Boton(t("connect"), Modifier.fillMaxWidth().padding(bottom = 12.dp), enabled = ble.conn == Conn.DESCONECTADO, principal = true) { pedirYConectar() }
                }

                // ---- Pestanas ----
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("tab_ctl", "tab_fx", "tab_set", "tab_sys").forEachIndexed { i, k ->
                        Chip(t(k), pestana == i, Modifier.weight(1f)) { pestana = i }
                    }
                }

                when (pestana) {
                    // =============================== CONTROL ===============================
                    0 -> {
                        Tarjeta {
                            Etiq(t("quick"))
                            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                BigButton(Modifier.weight(1f), t("off_t"), t("off_s"), Color(0xFFFF5967), Color(0xFFC81E3A), Icons.Filled.Close, conectado) {
                                    ble.enviar(if (recordarOff) "OFFSAVE" else "OFF")
                                }
                                BigButton(Modifier.weight(1f), t("on_t"), t("on_s"), Color(0xFF2BD576), Color(0xFF12844A), Icons.Filled.PowerSettingsNew, conectado) {
                                    ble.enviar("ON")
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                                Checkbox(checked = recordarOff, onCheckedChange = {
                                    recordarOff = it
                                    ctx.getSharedPreferences("c10prefs", 0).edit().putBoolean("rem_off", it).apply()
                                })
                                Column(Modifier.padding(top = 10.dp)) {
                                    Text(t("se_remember"), color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(t("se_remember_h"), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                                }
                            }
                            if (apagada && est?.o == true) {
                                Aviso(t("off_saved"))
                            }
                            Nota(t("hold"))
                        }
                        Spacer(Modifier.height(12.dp))
                        Tarjeta {
                            Etiq(t("b_title"))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${(brillo / 10.0).roundToInt()}", color = Cyan, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                                Text("0 – 100", color = Muted, fontSize = 11.sp)
                            }
                            Slider(
                                value = (brillo / 10f), onValueChange = { arrastrando = true; poner((it.roundToInt()) * 10, false) },
                                onValueChangeFinished = { poner(brillo, true); arrastrando = false },
                                valueRange = 0f..100f, enabled = conectado
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(t("b_fine"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("$brillo", color = Txt, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Slider(
                                value = brillo.toFloat(), onValueChange = { arrastrando = true; poner(it.roundToInt(), false) },
                                onValueChangeFinished = { poner(brillo, true); arrastrando = false },
                                valueRange = 0f..1000f, enabled = conectado
                            )
                            Nota(t("b_desc"))
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Boton("${t("b_night")} · ${prog.valorNoche}", Modifier.fillMaxWidth(), enabled = conectado) {
                                        brillo = prog.valorNoche; ble.enviar("NIGHT:${prog.valorNoche}")
                                    }
                                    CampoNumero(prog.valorNoche) { cambiarProg(prog.copy(valorNoche = it)) }
                                    Boton(t("b_use"), Modifier.fillMaxWidth(), pequeno = true) { cambiarProg(prog.copy(valorNoche = brillo)) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Boton("${t("b_day")} · ${prog.valorDia}", Modifier.fillMaxWidth(), enabled = conectado) {
                                        brillo = prog.valorDia; ble.enviar("DAY:${prog.valorDia}")
                                    }
                                    CampoNumero(prog.valorDia) { cambiarProg(prog.copy(valorDia = it)) }
                                    Boton(t("b_use"), Modifier.fillMaxWidth(), pequeno = true) { cambiarProg(prog.copy(valorDia = brillo)) }
                                }
                            }
                            Nota(t("b_range"))
                        }
                        Spacer(Modifier.height(12.dp))
                        Tarjeta {
                            Etiq(t("sc_title"))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(t("sc_on"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Switch(checked = prog.activa, onCheckedChange = { cambiarProg(prog.copy(activa = it)) })
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Boton(t("sc_modo_hora"), Modifier.weight(1f), principal = !prog.sol) { cambiarProg(prog.copy(sol = false)) }
                                Boton(t("sc_modo_sol"), Modifier.weight(1f), principal = prog.sol) { cambiarProg(prog.copy(sol = true)) }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (prog.sol) {
                                val sd = if (prog.lat.isNaN() || prog.lon.isNaN()) null else Sol.diaSol(prog.lat, prog.lon, java.util.Calendar.getInstance())
                                if (sd != null) Nota(t("sc_sol_hoy", Prog.hhmmMs(sd.first) + " / " + Prog.hhmmMs(sd.second)))
                                else if (!Sol.tienePermiso(ctx)) Nota(t("sc_sol_perm"))
                                else Nota(t("sc_sol_noloc"))
                                Nota(t("sc_sol_hint"))
                            }
                            if (!prog.sol) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(t("sc_dayfrom"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Boton(Prog.hhmm(prog.diaDesde), Modifier.fillMaxWidth()) {
                                        TimePickerDialog(ctx, { _, h, m -> cambiarProg(prog.copy(diaDesde = h * 60 + m)) }, prog.diaDesde / 60, prog.diaDesde % 60, true).show()
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(t("sc_nightfrom"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Boton(Prog.hhmm(prog.nocheDesde), Modifier.fillMaxWidth()) {
                                        TimePickerDialog(ctx, { _, h, m -> cambiarProg(prog.copy(nocheDesde = h * 60 + m)) }, prog.nocheDesde / 60, prog.nocheDesde % 60, true).show()
                                    }
                                }
                            }
                            if (prog.activa) Nota(t(if (prog.esDiaAhora()) "sc_period_day" else "sc_period_night"))
                            Nota(t("sc_hint"))
                            Nota(t("sc_bg"))
                        }
                    }
                    // =============================== EFECTOS ===============================
                    1 -> {
                        Tarjeta {
                            Etiq(t("fx_title"))
                            Nota(t("fx_desc"))
                            Spacer(Modifier.height(8.dp))
                            for (fila in 0 until 3) {
                                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (col in 0 until 3) {
                                        val i = fila * 3 + col + 1
                                        Chip(t("th$i"), est?.h == i, Modifier.weight(1f), alto = 46) {
                                            if (conectado) ble.enviar("SHOW:$i,$duracionShow")
                                        }
                                    }
                                }
                            }
                            Text(t("fx_dur"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60, 0).forEach { m ->
                                    Chip(if (m == 0) t("fx_inf") else t("fx_min", "$m"), duracionShow == m, Modifier.weight(1f)) { duracionShow = m }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Boton(t("fx_stop"), Modifier.fillMaxWidth(), enabled = conectado, peligro = true) { ble.enviar("SHOW:STOP") }
                            if (showOn) Nota(t("fx_running", t("th${est?.h ?: 1}")))
                            Nota(t("fx_note"))
                        }
                    }
                    // =============================== AJUSTES ===============================
                    2 -> {
                        val a = aj
                        Tarjeta {
                            Etiq(t("se_title"))
                            Nota(t("se_desc"))
                            if (a == null) {
                                Nota(t("s_nocon"))
                            } else {
                                Spacer(Modifier.height(8.dp))
                                FilaValor(t("se_base"), "${(a.d / 10.0).roundToInt()}")
                                Slider(value = a.d / 10f, onValueChange = { aj = a.copy(d = (it.roundToInt() * 10).coerceAtLeast(30)) }, valueRange = 3f..100f)
                                Nota(t("se_base_h"))
                                FilaValor(t("se_wait"), t("se_sec", "${a.e}"))
                                Slider(value = a.e.toFloat(), onValueChange = { aj = a.copy(e = it.roundToInt()) }, valueRange = 5f..300f)
                                FilaValor(t("se_breath"), if (a.r == 0) "0" else t("se_sec", "${a.r}"))
                                Slider(value = a.r.toFloat(), onValueChange = {
                                    val v = (it / 5f).roundToInt() * 5
                                    aj = a.copy(r = if (v in 1..9) 10 else v)
                                }, valueRange = 0f..600f)
                                Nota(t("se_breath0"))
                                Text(t("se_pp"), color = Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Boton(t("se_pp_off"), Modifier.weight(1f), principal = a.p == 0, pequeno = true) { aj = a.copy(p = 0) }
                                    Boton(t("se_pp_cl"), Modifier.weight(1f), principal = a.p == 1, pequeno = true) { aj = a.copy(p = 1) }
                                    Boton(t("se_pp_co"), Modifier.weight(1f), principal = a.p == 2, pequeno = true) { aj = a.copy(p = 2) }
                                }
                                FilaValor(t("se_sens"), "${a.k}")
                                Slider(value = a.k.toFloat(), onValueChange = { aj = a.copy(k = it.roundToInt()) }, valueRange = 1f..10f, steps = 8)
                                Nota(t("se_sens_hint"))
                                Text(t("se_meter"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                LinearProgressIndicator(
                                    progress = { (ble.medidor / 200f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(12.dp).padding(top = 4.dp).clip(RoundedCornerShape(50))
                                )
                                Nota(t("se_meter_hint") + "  (100 = ${t("te_meter").substringAfter("= ").trimEnd(')')})")
                                Text(t("se_welcome"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                for (fila in 0 until 2) {
                                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (col in 0 until 3) {
                                            val i = fila * 3 + col + 1
                                            if (i <= 5) Chip(t("w$i"), a.w == i, Modifier.weight(1f), alto = 44) {
                                                aj = a.copy(w = i); ble.enviar("WSEL:$i")
                                            } else Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                                Boton(t("se_preview"), Modifier.fillMaxWidth(), enabled = conectado) { ble.enviar("WPLAY:${a.w}") }
                                Spacer(Modifier.height(14.dp))
                                fun cmdSet() = "SET:${a.e},${a.r},${a.p},${a.k},${a.d}"
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Boton(t("se_test"), Modifier.weight(1f), enabled = conectado) { ble.enviar(cmdSet()); avisoAj = t("se_tried") }
                                    Boton(t("se_save"), Modifier.weight(1f), enabled = conectado, principal = true) {
                                        ble.enviar(cmdSet()); ble.enviar("SAVE"); avisoAj = t("se_saved")
                                    }
                                }
                                if (avisoAj.isNotEmpty()) Nota(avisoAj)
                                Spacer(Modifier.height(10.dp))
                                Boton(t("se_reset"), Modifier.fillMaxWidth(), enabled = conectado, peligro = true) { confirmarReset = true }
                            }
                        }
                    }
                    // =============================== SISTEMA ===============================
                    else -> {
                        Tarjeta {
                            Etiq(t("sy_info"))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text(t("sy_version"), color = Muted, fontSize = 11.sp); Text(est?.v?.ifEmpty { "-" } ?: "-", color = Txt, fontWeight = FontWeight.ExtraBold) }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(t("sy_sensor"), color = Muted, fontSize = 11.sp)
                                    Text(if (est == null) "-" else if (est.m) t("sy_sens_ok") else t("sy_sens_no"), color = Txt, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Tarjeta {
                            Etiq(t("sy_fw"))
                            Nota(t("sy_wifi"))
                            Spacer(Modifier.height(10.dp))
                            Boton(t("sy_wifi_b"), Modifier.fillMaxWidth(), principal = true) {
                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://192.168.4.1")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                            }
                            Nota(t("sy_orig_d"))
                        }
                        if (Build.VERSION.SDK_INT >= 31 && !Auto.puedeAlarmaExacta(ctx)) {
                            Spacer(Modifier.height(12.dp))
                            Tarjeta {
                                Etiq(t("sc_title"))
                                Nota(t("sc_bg"))
                                Spacer(Modifier.height(8.dp))
                                Boton(t("sc_perm"), Modifier.fillMaxWidth()) {
                                    try { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                                }
                            }
                        }
                        if (conectado) {
                            Spacer(Modifier.height(12.dp))
                            Boton(t("disconnect"), Modifier.fillMaxWidth()) { ble.desconectar() }
                        }
                    }
                }

                // =============================== TECNICA (oculta) ===============================
                if (tecnica) {
                    Spacer(Modifier.height(14.dp))
                    Tarjeta(borde = Cyan) {
                        Etiq(t("te_title"))
                        Nota(t("te_hint"))
                        Text(t("te_scale"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CampoNumero(brillo, Modifier.weight(1f)) { brillo = it }
                            Boton(t("te_send"), Modifier.width(110.dp), enabled = conectado, principal = true) { ble.enviar("BR:$brillo") }
                        }
                        val p = pwmDe(brillo)
                        Text("PWM $p · 0x" + p.toString(16).uppercase().padStart(4, '0'), color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(8.dp))
                        var crudo by remember { mutableStateOf("") }
                        Text(t("te_cmd"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = crudo, onValueChange = { crudo = it }, singleLine = true, modifier = Modifier.weight(1f))
                            Boton(t("te_send"), Modifier.width(110.dp), enabled = conectado) { if (crudo.isNotBlank()) ble.enviar(crudo.trim()) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${t("te_meter")}: ${ble.medidor}", color = Txt, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(t("te_state"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(est?.toString() ?: "-", color = Color(0xFF9FE3B0), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(6.dp))
                        Text(t("te_conn"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(ble.registro, color = Color(0xFF9FE3B0), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("C10 LED CONTROL · BLUETOOTH", color = Color(0xFF64748B), fontSize = 10.sp, letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            if (confirmarReset) {
                AlertDialog(
                    onDismissRequest = { confirmarReset = false },
                    title = { Text(t("se_reset")) },
                    text = { Text(t("se_reset_c")) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmarReset = false
                            ble.enviar("RESET"); avisoAj = t("se_reseted"); aj = null
                        }) { Text(t("ok")) }
                    },
                    dismissButton = { TextButton(onClick = { confirmarReset = false }) { Text(t("back")) } }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------------------------------------
@Composable
private fun Tarjeta(borde: Color = Line, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF131D2E), Color(0xFF0C121E))))
            .border(1.dp, borde, RoundedCornerShape(22.dp)).padding(18.dp),
        content = content
    )
}

@Composable private fun Etiq(x: String) {
    Text(x, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable private fun Nota(x: String) {
    Text(x, color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable private fun Aviso(x: String) {
    Text(x, color = Color(0xFFFDE68A), fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A1A06)).border(1.dp, Color(0xFF92400E), RoundedCornerShape(12.dp)).padding(12.dp))
}

@Composable private fun FilaValor(titulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(titulo, color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Text(valor, color = Txt, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Chip(texto: String, activo: Boolean, modifier: Modifier = Modifier, alto: Int = 0, onClick: () -> Unit) {
    Box(
        modifier
            .then(if (alto > 0) Modifier.height(alto.dp) else Modifier)
            .clip(RoundedCornerShape(11.dp))
            .background(if (activo) Color(0xFF0C4A6E) else Color(0xFF0D1421))
            .border(1.dp, if (activo) Cyan else Line, RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, color = if (activo) Color(0xFFE0F2FE) else Muted, fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun Boton(
    texto: String, modifier: Modifier = Modifier, enabled: Boolean = true, principal: Boolean = false,
    peligro: Boolean = false, pequeno: Boolean = false, onClick: () -> Unit
) {
    val fondo = when { peligro -> Color(0xFF3B0D18); principal -> Color(0xFF0B7FC0); else -> Color(0xFF15213A) }
    val borde = when { peligro -> Color(0xFF9F1239); principal -> Cyan; else -> Color(0xFF2C3D57) }
    Box(
        modifier.height(if (pequeno) 36.dp else 46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (enabled) fondo else Color(0xFF111827))
            .border(1.dp, if (enabled) borde else Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, color = if (enabled) Color(0xFFE5EDF8) else Muted, fontSize = if (pequeno) 11.sp else 12.5.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Campo numerico 0..1000 (cualquier valor). */
@Composable
private fun CampoNumero(valor: Int, modifier: Modifier = Modifier.fillMaxWidth(), onCambio: (Int) -> Unit) {
    var texto by remember(valor) { mutableStateOf(valor.toString()) }
    OutlinedTextField(
        value = texto,
        onValueChange = { nuevo ->
            val limpio = nuevo.filter { it.isDigit() }.take(4)
            texto = limpio
            limpio.toIntOrNull()?.let { onCambio(it.coerceIn(0, 1000)) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun BigButton(
    modifier: Modifier, titulo: String, sub: String, c1: Color, c2: Color,
    icono: ImageVector, enabled: Boolean, onClick: () -> Unit
) {
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(c1, c2)))
            .clickable(enabled = enabled) { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icono, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(5.dp))
        Text(titulo, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(sub, color = Color(0xCCFFFFFF), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
