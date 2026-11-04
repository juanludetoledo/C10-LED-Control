package com.juanlu.c10led

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Idiomas de la app: español, inglés, portugués (Brasil) y chino simplificado. */
object I18n {
    val idiomas = listOf("es" to "ES", "en" to "EN", "pt" to "PT", "zh" to "中文")
    private val indice = mapOf("es" to 0, "en" to 1, "pt" to 2, "zh" to 3)
    private const val PREFS = "c10prefs"
    private const val CLAVE = "idioma"

    var codigo by mutableStateOf("es")
        private set

    fun iniciar(ctx: Context) {
        val guardado = try { ctx.getSharedPreferences(PREFS, 0).getString(CLAVE, null) } catch (e: Exception) { null }
        codigo = if (guardado != null && indice.containsKey(guardado)) guardado
        else when (Locale.getDefault().language) {
            "en" -> "en"; "pt" -> "pt"; "zh" -> "zh"; else -> "es"
        }
    }

    fun poner(ctx: Context, c: String) {
        if (!indice.containsKey(c)) return
        codigo = c
        try { ctx.getSharedPreferences(PREFS, 0).edit().putString(CLAVE, c).apply() } catch (e: Exception) { }
    }

    fun t(clave: String): String = TEXTOS[clave]?.get(indice[codigo] ?: 0) ?: clave

    /** Traduce un mensaje con argumento opcional: "clave" o "clave:arg" (sustituye {0}). */
    fun tm(msg: String): String {
        if (msg.isEmpty()) return ""
        val i = msg.indexOf(':')
        return if (i < 0) t(msg) else t(msg.substring(0, i)).replace("{0}", msg.substring(i + 1))
    }

    private val TEXTOS: Map<String, Array<String>> = mapOf(
        "sub" to arrayOf("Centralita de iluminación · ESP32-S3", "Lighting controller · ESP32-S3", "Central de iluminação · ESP32-S3", "照明控制器 · ESP32-S3"),
        "quick" to arrayOf("CONTROL RÁPIDO", "QUICK CONTROL", "CONTROLE RÁPIDO", "快速控制"),
        "hero" to arrayOf("Control de la barra", "Bar control", "Controle da barra", "灯条控制"),
        "desc" to arrayOf("Apaga toda la barra o devuelve la centralita a su funcionamiento normal.", "Turns the whole bar off or returns the controller to normal operation.", "Desliga toda a barra ou devolve a central ao funcionamento normal.", "关闭整条灯条,或让控制器恢复正常工作。"),
        "off_t" to arrayOf("OFF · APAGAR BARRA", "OFF · TURN BAR OFF", "OFF · DESLIGAR BARRA", "关闭 · 关灯条"),
        "off_s" to arrayOf("Bloqueo manual", "Manual lock", "Bloqueio manual", "手动锁定"),
        "on_t" to arrayOf("ON · MODO NORMAL", "ON · NORMAL MODE", "ON · MODO NORMAL", "开启 · 正常模式"),
        "on_s" to arrayOf("Reactivar control", "Resume control", "Reativar controle", "恢复控制"),
        "st_title" to arrayOf("Estado de la centralita", "Controller status", "Estado da central", "控制器状态"),
        "st_hold" to arrayOf("El bloqueo manual se mantiene hasta que pulses MODO NORMAL.", "The manual lock stays until you press NORMAL MODE.", "O bloqueio manual permanece até você tocar em MODO NORMAL.", "手动锁定会一直保持,直到您点按“正常模式”。"),
        "p_search" to arrayOf("BUSCANDO", "SEARCHING", "PROCURANDO", "搜索中"),
        "p_connecting" to arrayOf("CONECTANDO", "CONNECTING", "CONECTANDO", "连接中"),
        "p_nocon" to arrayOf("SIN CONEXIÓN", "NOT CONNECTED", "SEM CONEXÃO", "未连接"),
        "p_off" to arrayOf("BARRA APAGADA", "BAR OFF", "BARRA DESLIGADA", "灯条已关闭"),
        "p_normal" to arrayOf("MODO NORMAL", "NORMAL MODE", "MODO NORMAL", "正常模式"),
        "s_search" to arrayOf("Buscando la centralita…", "Looking for the controller…", "Procurando a central…", "正在搜索控制器…"),
        "s_connecting" to arrayOf("Conectando…", "Connecting…", "Conectando…", "正在连接…"),
        "s_nocon" to arrayOf("No conectado a la centralita", "Not connected to the controller", "Não conectado à central", "未连接到控制器"),
        "s_off" to arrayOf("ESTADO: BARRA APAGADA", "STATUS: BAR OFF", "ESTADO: BARRA DESLIGADA", "状态:灯条已关闭"),
        "s_normal" to arrayOf("ESTADO: MODO NORMAL", "STATUS: NORMAL MODE", "ESTADO: MODO NORMAL", "状态:正常模式"),
        "connect" to arrayOf("CONECTAR", "CONNECT", "CONECTAR", "连接"),
        "m_perm" to arrayOf("Faltan permisos de Bluetooth", "Bluetooth permissions missing", "Faltam permissões de Bluetooth", "缺少蓝牙权限"),
        "m_nobt" to arrayOf("Este móvil no tiene Bluetooth", "This phone has no Bluetooth", "Este celular não tem Bluetooth", "此手机没有蓝牙"),
        "m_btoff" to arrayOf("Activa el Bluetooth", "Turn Bluetooth on", "Ative o Bluetooth", "请开启蓝牙"),
        "m_noscan" to arrayOf("Escáner BLE no disponible", "BLE scanner unavailable", "Scanner BLE indisponível", "BLE 扫描不可用"),
        "m_notfound" to arrayOf("No se encontró C10_LED_CONTROL", "C10_LED_CONTROL not found", "C10_LED_CONTROL não encontrado", "未找到 C10_LED_CONTROL"),
        "m_scanerr" to arrayOf("Error de escaneo ({0})", "Scan error ({0})", "Erro de varredura ({0})", "扫描错误({0})"),
        "m_nosvc" to arrayOf("Servicio BLE no encontrado", "BLE service not found", "Serviço BLE não encontrado", "未找到 BLE 服务"),
        "b_eyebrow" to arrayOf("BRILLO", "BRIGHTNESS", "BRILHO", "亮度"),
        "b_title" to arrayOf("Brillo fijo de día y de noche", "Fixed day and night brightness", "Brilho fixo de dia e de noite", "白天与夜间固定亮度"),
        "b_desc" to arrayOf("La barra queda fija en el brillo elegido. Al pulsar ON o cortar la corriente vuelve al modo normal (0x0650).", "The bar stays fixed at the chosen brightness. Pressing ON or cutting the power returns it to normal mode (0x0650).", "A barra fica fixa no brilho escolhido. Ao tocar em ON ou cortar a energia, volta ao modo normal (0x0650).", "灯条将固定在所选亮度。点按“开启”或断电后恢复正常模式(0x0650)。"),
        "b_scale" to arrayOf("ESCALA 0–1000", "SCALE 0–1000", "ESCALA 0–1000", "刻度 0–1000"),
        "b_level" to arrayOf("NIVEL 0–100", "LEVEL 0–100", "NÍVEL 0–100", "等级 0–100"),
        "b_hint" to arrayOf("− / + = 1 punto · −− / ++ = 10 puntos", "− / + = 1 point · −− / ++ = 10 points", "− / + = 1 ponto · −− / ++ = 10 pontos", "− / + = 1 点 · −− / ++ = 10 点"),
        "b_night" to arrayOf("NOCHE", "NIGHT", "NOITE", "夜间"),
        "b_day" to arrayOf("DÍA", "DAY", "DIA", "白天"),
        "b_dayval" to arrayOf("Valor de DÍA", "DAY value", "Valor de DIA", "白天数值"),
        "b_use" to arrayOf("USAR EL ACTUAL COMO DÍA", "USE CURRENT AS DAY", "USAR O ATUAL COMO DIA", "将当前值设为白天"),
        "b_lvl" to arrayOf("nivel", "level", "nível", "等级"),
    )
}

fun t(clave: String): String = I18n.t(clave)
