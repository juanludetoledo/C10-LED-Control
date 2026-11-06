package com.juanlu.c10led

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Idiomas de la app: español, inglés, portugués (Brasil) y chino simplificado. (Generado desde dic.py) */
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

    /** Texto con argumento: sustituye {0}. */
    fun t(clave: String, arg: String): String = t(clave).replace("{0}", arg)

    /** Traduce un mensaje con argumento opcional: "clave" o "clave:arg" (sustituye {0}). */
    fun tm(msg: String): String {
        if (msg.isEmpty()) return ""
        val i = msg.indexOf(':')
        return if (i < 0) t(msg) else t(msg.substring(0, i)).replace("{0}", msg.substring(i + 1))
    }

    private val TEXTOS: Map<String, Array<String>> = mapOf(
        "app" to arrayOf("C10 LED Control", "C10 LED Control", "C10 LED Control", "C10 LED Control"),
        "sub" to arrayOf("Centralita de iluminación · ESP32-S3", "Lighting controller · ESP32-S3", "Central de iluminação · ESP32-S3", "照明控制器 · ESP32-S3"),
        "tab_ctl" to arrayOf("CONTROL", "CONTROL", "CONTROLE", "控制"),
        "tab_fx" to arrayOf("EFECTOS", "EFFECTS", "EFEITOS", "效果"),
        "tab_set" to arrayOf("AJUSTES", "SETTINGS", "AJUSTES", "设置"),
        "tab_sys" to arrayOf("SISTEMA", "SYSTEM", "SISTEMA", "系统"),
        "p_conn" to arrayOf("CONECTANDO", "CONNECTING", "CONECTANDO", "连接中"),
        "p_nocon" to arrayOf("SIN CONEXIÓN", "NOT CONNECTED", "SEM CONEXÃO", "未连接"),
        "p_off" to arrayOf("BARRA APAGADA", "BAR OFF", "BARRA DESLIGADA", "灯条已关闭"),
        "p_normal" to arrayOf("MODO NORMAL", "NORMAL MODE", "MODO NORMAL", "正常模式"),
        "p_show" to arrayOf("SHOW EN MARCHA", "SHOW RUNNING", "SHOW EM CURSO", "演示进行中"),
        "ok" to arrayOf("Hecho", "Done", "Pronto", "完成"),
        "fail" to arrayOf("No se pudo enviar la orden. Comprueba la conexión y vuelve a pulsar.", "Could not send the command. Check the connection and try again.", "Não foi possível enviar o comando. Verifique a conexão e tente de novo.", "指令发送失败。请检查连接后重试。"),
        "sending" to arrayOf("Enviando…", "Sending…", "Enviando…", "发送中…"),
        "lang" to arrayOf("Idioma", "Language", "Idioma", "语言"),
        "quick" to arrayOf("CONTROL RÁPIDO", "QUICK CONTROL", "CONTROLE RÁPIDO", "快速控制"),
        "off_t" to arrayOf("OFF · APAGAR BARRA", "OFF · TURN BAR OFF", "OFF · DESLIGAR BARRA", "关闭 · 关灯条"),
        "off_s" to arrayOf("Bloqueo manual", "Manual lock", "Bloqueio manual", "手动锁定"),
        "on_t" to arrayOf("ON · MODO NORMAL", "ON · NORMAL MODE", "ON · MODO NORMAL", "开启 · 正常模式"),
        "on_s" to arrayOf("Reinicio limpio", "Clean restart", "Reinício limpo", "干净重启"),
        "hold" to arrayOf("El bloqueo manual se mantiene hasta que pulses ON.", "The manual lock stays until you press ON.", "O bloqueio manual permanece até você tocar em ON.", "手动锁定会一直保持,直到您点按“开启”。"),
        "b_title" to arrayOf("Brillo", "Brightness", "Brilho", "亮度"),
        "b_desc" to arrayOf("La barra queda fija en el brillo elegido. Al apagar y encender el coche vuelve al brillo de arranque.", "The bar stays fixed at the chosen brightness. After switching the car off and on it returns to the start-up level.", "A barra fica fixa no brilho escolhido. Ao desligar e ligar o carro volta ao brilho de arranque.", "灯条保持在所选亮度。熄火再启动后将恢复启动亮度。"),
        "b_fine" to arrayOf("Ajuste fino", "Fine adjust", "Ajuste fino", "精细调节"),
        "b_night" to arrayOf("NOCHE", "NIGHT", "NOITE", "夜间"),
        "b_day" to arrayOf("DÍA", "DAY", "DIA", "白天"),
        "b_dayval" to arrayOf("Brillo de DÍA", "DAY brightness", "Brilho de DIA", "白天亮度"),
        "b_nightval" to arrayOf("Brillo de NOCHE", "NIGHT brightness", "Brilho de NOITE", "夜间亮度"),
        "b_use" to arrayOf("Usar el actual", "Use current", "Usar o atual", "使用当前值"),
        "b_range" to arrayOf("de 0 a 1000", "from 0 to 1000", "de 0 a 1000", "范围 0–1000"),
        "sc_title" to arrayOf("Programación día / noche", "Day / night schedule", "Programação dia / noite", "白天 / 夜间定时"),
        "sc_on" to arrayOf("Automática", "Automatic", "Automática", "自动"),
        "sc_dayfrom" to arrayOf("Día desde", "Day from", "Dia a partir de", "白天开始"),
        "sc_nightfrom" to arrayOf("Noche desde", "Night from", "Noite a partir de", "夜间开始"),
        "sc_hint" to arrayOf("Al cambiar de periodo, la barra pasa al brillo elegido con una expansión desde el centro. Si el show está en marcha, espera a que termine. Un brillo manual manda hasta el siguiente cambio.", "At each change of period the bar moves to the chosen brightness with an expansion from the centre. While a show is running it waits until it ends. A manual brightness wins until the next change.", "A cada mudança de período a barra passa ao brilho escolhido com uma expansão a partir do centro. Com um show em curso, espera que termine. Um brilho manual vale até à mudança seguinte.", "每次切换时段,灯条会以从中心展开的方式变为所选亮度。演示进行时会等待其结束。手动亮度在下一次切换前有效。"),
        "sc_page" to arrayOf("En esta página la programación se aplica mientras esté abierta y es propia de este navegador.", "On this page the schedule applies only while it is open and belongs to this browser.", "Nesta página a programação só se aplica enquanto estiver aberta e é própria deste navegador.", "在本页面中,定时仅在页面打开时生效,且仅属于此浏览器。"),
        "sc_period_day" to arrayOf("Ahora: periodo de DÍA", "Now: DAY period", "Agora: período de DIA", "当前:白天时段"),
        "sc_period_night" to arrayOf("Ahora: periodo de NOCHE", "Now: NIGHT period", "Agora: período de NOITE", "当前:夜间时段"),
        "fx_title" to arrayOf("Show / Fiesta", "Show / Party", "Show / Festa", "演示 / 派对"),
        "fx_desc" to arrayOf("Efectos suaves para cuando el coche está parado. Se detienen solos si el coche se mueve.", "Gentle effects for when the car is parked. They stop on their own if the car moves.", "Efeitos suaves para quando o carro está parado. Param sozinhos se o carro se mover.", "适用于停车时的柔和效果。车辆行驶时会自动停止。"),
        "th1" to arrayOf("Olas", "Waves", "Ondas", "海浪"),
        "th2" to arrayOf("Agua", "Water", "Água", "流水"),
        "th3" to arrayOf("Estrellas", "Stars", "Estrelas", "星光"),
        "th4" to arrayOf("Fiesta", "Party", "Festa", "派对"),
        "th5" to arrayOf("Pacífico", "Pacific", "Pacífico", "太平洋"),
        "th6" to arrayOf("Tranquilo", "Calm", "Tranquilo", "宁静"),
        "th7" to arrayOf("Cometa", "Comet", "Cometa", "彗星"),
        "th8" to arrayOf("Cine", "Cinema", "Cinema", "影院"),
        "th9" to arrayOf("Aleatorio", "Random tour", "Aleatório", "随机巡演"),
        "fx_dur" to arrayOf("Duración", "Duration", "Duração", "时长"),
        "fx_inf" to arrayOf("Hasta parar", "Until stopped", "Até parar", "直到停止"),
        "fx_min" to arrayOf("{0} min", "{0} min", "{0} min", "{0} 分钟"),
        "fx_stop" to arrayOf("PARAR SHOW", "STOP SHOW", "PARAR SHOW", "停止演示"),
        "fx_running" to arrayOf("En marcha: {0}", "Running: {0}", "Em curso: {0}", "进行中:{0}"),
        "fx_note" to arrayOf("Mientras dura el show, la programación día/noche queda en pausa; al terminar se aplica el brillo del periodo actual.", "While the show runs the day/night schedule is paused; when it ends the brightness of the current period is applied.", "Enquanto dura o show, a programação dia/noite fica em pausa; ao terminar aplica-se o brilho do período atual.", "演示期间,白天/夜间定时暂停;结束后将应用当前时段的亮度。"),
        "se_title" to arrayOf("Comportamiento en parado", "Behaviour when parked", "Comportamento parado", "停车时的行为"),
        "se_desc" to arrayOf("Prueba los valores y, cuando te gusten, pulsa GUARDAR. Hasta entonces son temporales: al apagar el coche se pierden.", "Try the values and, when you like them, press SAVE. Until then they are temporary: they are lost when the car is switched off.", "Teste os valores e, quando gostar, toque em GUARDAR. Até lá são temporários: perdem-se ao desligar o carro.", "先试用这些数值,满意后点按“保存”。在此之前它们是临时的,熄火后会丢失。"),
        "se_wait" to arrayOf("Espera antes de respirar", "Wait before breathing", "Espera antes de respirar", "呼吸前等待"),
        "se_breath" to arrayOf("Duración de la respiración", "Breathing duration", "Duração da respiração", "呼吸时长"),
        "se_breath0" to arrayOf("0 = sin respiración", "0 = no breathing", "0 = sem respiração", "0 = 不呼吸"),
        "se_sec" to arrayOf("{0} s", "{0} s", "{0} s", "{0} 秒"),
        "se_pp" to arrayOf("Ping-pong en parado", "Ping-pong when parked", "Ping-pong parado", "停车乒乓效果"),
        "se_pp_on" to arrayOf("Activado", "On", "Ativado", "开启"),
        "se_pp_off" to arrayOf("Desactivado", "Off", "Desativado", "关闭"),
        "se_sens" to arrayOf("Sensibilidad del sensor", "Sensor sensitivity", "Sensibilidade do sensor", "传感器灵敏度"),
        "se_sens_hint" to arrayOf("5 es la sensibilidad de siempre. Más alta = detecta movimientos más suaves.", "5 is the usual sensitivity. Higher = detects gentler movements.", "5 é a sensibilidade de sempre. Mais alta = deteta movimentos mais suaves.", "5 为默认灵敏度。数值越大,越能检测到轻微晃动。"),
        "se_meter" to arrayOf("Medidor en vivo", "Live meter", "Medidor ao vivo", "实时检测条"),
        "se_meter_hint" to arrayOf("Mueve o golpea suavemente el coche: al llegar a la línea, se detecta movimiento.", "Gently move or tap the car: when the bar reaches the line, movement is detected.", "Mova ou toque suavemente no carro: ao chegar à linha, deteta-se movimento.", "轻轻晃动或敲击车辆:当检测条到达标线时即判定为移动。"),
        "se_base" to arrayOf("Brillo fijo de arranque", "Start-up fixed brightness", "Brilho fixo de arranque", "启动固定亮度"),
        "se_base_h" to arrayOf("Es el brillo por defecto con el que la barra SIEMPRE arranca, sin día ni noche (0–100; de fábrica 66 = 0x0650). Después la app puede cambiarlo con el modo día/noche. Se graba con GUARDAR.", "This is the default brightness the bar ALWAYS starts with, with no day or night (0–100; factory 66 = 0x0650). Afterwards the app can change it with day/night mode. Stored with SAVE.", "Este é o brilho padrão com que a barra SEMPRE arranca, sem dia nem noite (0–100; de fábrica 66 = 0x0650). Depois a app pode alterá-lo com o modo dia/noite. Grava-se com GUARDAR.", "这是灯条每次启动时始终使用的默认亮度,与白天/夜间无关(0–100;出厂为 66 = 0x0650)。之后应用可通过白天/夜间模式改变它。点按“保存”后写入。"),
        "se_welcome" to arrayOf("Bienvenida al encender", "Welcome animation", "Animação de boas-vindas", "开机欢迎动画"),
        "w1" to arrayOf("Clásica", "Classic", "Clássica", "经典"),
        "w2" to arrayOf("Película", "Film", "Filme", "电影"),
        "w3" to arrayOf("Aurora", "Aurora", "Aurora", "极光"),
        "w4" to arrayOf("Cascada", "Cascade", "Cascata", "瀑布"),
        "w5" to arrayOf("Rayo", "Lightning", "Raio", "闪电"),
        "se_preview" to arrayOf("VER AHORA", "PREVIEW", "VER AGORA", "立即预览"),
        "se_test" to arrayOf("PROBAR", "TRY", "TESTAR", "试用"),
        "se_save" to arrayOf("GUARDAR", "SAVE", "GUARDAR", "保存"),
        "se_reset" to arrayOf("Restablecer ajustes", "Reset settings", "Repor ajustes", "恢复默认设置"),
        "se_reset_c" to arrayOf("¿Restablecer todos los ajustes a los valores de fábrica?", "Reset all settings to factory values?", "Repor todos os ajustes aos valores de fábrica?", "将所有设置恢复为出厂值?"),
        "se_tried" to arrayOf("Probando (sin guardar)", "Trying (not saved)", "A testar (sem guardar)", "试用中(未保存)"),
        "se_saved" to arrayOf("Guardado en la centralita", "Saved in the controller", "Guardado na central", "已保存到控制器"),
        "se_reseted" to arrayOf("Ajustes restablecidos", "Settings reset", "Ajustes repostos", "设置已恢复"),
        "se_remember" to arrayOf("Recordar apagado", "Remember off state", "Lembrar desligado", "记住关闭状态"),
        "se_remember_h" to arrayOf("Si la dejas apagada con esta casilla marcada, seguirá apagada cada vez que arranque el coche (sin bienvenida) hasta que pulses ON.", "If you turn it off with this box ticked, it stays off every time the car starts (no welcome) until you press ON.", "Se a desligar com esta caixa marcada, fica desligada sempre que o carro arrancar (sem boas-vindas) até tocar em ON.", "勾选后关闭灯条,每次启动车辆时都会保持关闭(无欢迎动画),直到您点按“开启”。"),
        "off_saved" to arrayOf("Apagada y guardada: no se enciende al arrancar. Pulsa ON para encenderla con la bienvenida.", "Off and saved: it will not turn on at start-up. Press ON to switch it on with the welcome.", "Desligada e guardada: não acende ao arrancar. Toque em ON para acender com as boas-vindas.", "已关闭并保存:启动时不会点亮。点按“开启”即可带欢迎动画点亮。"),
        "off_turn" to arrayOf("APAGAR Y RECORDAR", "TURN OFF AND REMEMBER", "DESLIGAR E LEMBRAR", "关闭并记住"),
        "sy_info" to arrayOf("Información", "Information", "Informação", "信息"),
        "sy_version" to arrayOf("Versión del sketch", "Sketch version", "Versão do sketch", "固件版本"),
        "sy_sensor" to arrayOf("Sensor de movimiento", "Motion sensor", "Sensor de movimento", "运动传感器"),
        "sy_sens_ok" to arrayOf("Detectado", "Detected", "Detetado", "已检测到"),
        "sy_sens_no" to arrayOf("No detectado (luz fija)", "Not detected (fixed light)", "Não detetado (luz fixa)", "未检测到(常亮)"),
        "sy_fw" to arrayOf("Actualizar firmware", "Update firmware", "Atualizar firmware", "更新固件"),
        "sy_fw_desc" to arrayOf("Selecciona un archivo .bin compilado para tu ESP32-S3. Durante la actualización no desconectes la alimentación.", "Select a .bin file compiled for your ESP32-S3. Do not disconnect the power during the update.", "Selecione um arquivo .bin compilado para o seu ESP32-S3. Durante a atualização não desligue a alimentação.", "请选择为 ESP32-S3 编译的 .bin 文件。更新期间请勿断开电源。"),
        "sy_fw_btn" to arrayOf("ACTUALIZAR FIRMWARE", "UPDATE FIRMWARE", "ATUALIZAR FIRMWARE", "更新固件"),
        "sy_badbin" to arrayOf("Selecciona un archivo .bin válido.", "Select a valid .bin file.", "Selecione um arquivo .bin válido.", "请选择有效的 .bin 文件。"),
        "sy_fw_conf" to arrayOf("¿Actualizar la centralita con {0}? No desconectes la alimentación durante el proceso.", "Update the controller with {0}? Do not disconnect the power during the process.", "Atualizar a central com {0}? Não desligue a alimentação durante o processo.", "使用 {0} 更新控制器?过程中请勿断开电源。"),
        "sy_orig" to arrayOf("Seguridad · volver al firmware original", "Safety · back to the original firmware", "Segurança · voltar ao firmware original", "安全 · 恢复原始固件"),
        "sy_orig_d" to arrayOf("Si una actualización saliera mal, este botón devuelve la centralita al firmware original de fábrica (el que se grabó por cable) y borra los ajustes guardados. La barra se reinicia.", "If an update goes wrong, this button returns the controller to the original factory firmware (the one flashed by cable) and erases the saved settings. The bar restarts.", "Se uma atualização correr mal, este botão devolve a central ao firmware original de fábrica (o gravado por cabo) e apaga os ajustes guardados. A barra reinicia.", "如果更新出错,此按钮会让控制器恢复到出厂原始固件(通过数据线烧录的版本)并清除已保存的设置。灯条将重启。"),
        "sy_orig_b" to arrayOf("VOLVER AL FIRMWARE ORIGINAL", "BACK TO ORIGINAL FIRMWARE", "VOLTAR AO FIRMWARE ORIGINAL", "恢复原始固件"),
        "sy_orig_c" to arrayOf("¿Volver al firmware original? Se borrarán los ajustes guardados y la centralita se reiniciará.", "Go back to the original firmware? Saved settings will be erased and the controller will restart.", "Voltar ao firmware original? Os ajustes guardados serão apagados e a central reiniciará.", "恢复原始固件?已保存的设置将被清除,控制器将重启。"),
        "sy_orig_ok" to arrayOf("Reiniciando con el firmware original…", "Restarting with the original firmware…", "A reiniciar com o firmware original…", "正在以原始固件重启…"),
        "sy_orig_no" to arrayOf("Esta placa no tiene firmware original guardado.", "This board has no stored original firmware.", "Esta placa não tem firmware original guardado.", "此板没有保存原始固件。"),
        "sy_up_ok" to arrayOf("ACTUALIZACIÓN CORRECTA. La centralita se reiniciará…", "UPDATE OK. The controller will restart…", "ATUALIZAÇÃO CONCLUÍDA. A central reiniciará…", "更新成功。控制器即将重启…"),
        "sy_up_err" to arrayOf("ERROR EN LA ACTUALIZACIÓN. La barra vuelve a modo normal.", "UPDATE ERROR. The bar returns to normal mode.", "ERRO NA ATUALIZAÇÃO. A barra volta ao modo normal.", "更新失败。灯条已恢复正常模式。"),
        "back" to arrayOf("Volver", "Back", "Voltar", "返回"),
        "te_title" to arrayOf("Pantalla técnica", "Technical screen", "Ecrã técnico", "技术界面"),
        "te_hint" to arrayOf("Pulsa 5 veces el título para abrir/cerrar esta pantalla.", "Tap the title 5 times to open/close this screen.", "Toque 5 vezes no título para abrir/fechar este ecrã.", "连续点按标题 5 次可打开/关闭此界面。"),
        "te_scale" to arrayOf("Escala 0–1000", "Scale 0–1000", "Escala 0–1000", "0–1000 刻度"),
        "te_send" to arrayOf("Enviar", "Send", "Enviar", "发送"),
        "te_cmd" to arrayOf("Orden directa", "Direct command", "Comando direto", "直接指令"),
        "te_log" to arrayOf("Registro", "Log", "Registo", "日志"),
        "te_state" to arrayOf("Estado en bruto", "Raw state", "Estado bruto", "原始状态"),
        "te_meter" to arrayOf("Sensor (100 = umbral)", "Sensor (100 = threshold)", "Sensor (100 = limiar)", "传感器(100 = 阈值)"),
        "p_search" to arrayOf("BUSCANDO", "SEARCHING", "PROCURANDO", "搜索中"),
        "connect" to arrayOf("CONECTAR", "CONNECT", "CONECTAR", "连接"),
        "s_search" to arrayOf("Buscando la centralita…", "Looking for the controller…", "Procurando a central…", "正在搜索控制器…"),
        "s_connecting" to arrayOf("Conectando…", "Connecting…", "Conectando…", "正在连接…"),
        "s_nocon" to arrayOf("No conectado a la centralita", "Not connected to the controller", "Não conectado à central", "未连接到控制器"),
        "m_perm" to arrayOf("Faltan permisos de Bluetooth", "Bluetooth permissions missing", "Faltam permissões de Bluetooth", "缺少蓝牙权限"),
        "m_nobt" to arrayOf("Este móvil no tiene Bluetooth", "This phone has no Bluetooth", "Este celular não tem Bluetooth", "此手机没有蓝牙"),
        "m_btoff" to arrayOf("Activa el Bluetooth", "Turn Bluetooth on", "Ative o Bluetooth", "请开启蓝牙"),
        "m_noscan" to arrayOf("Escáner BLE no disponible", "BLE scanner unavailable", "Scanner BLE indisponível", "BLE 扫描不可用"),
        "m_notfound" to arrayOf("No se encontró JUANLU_C10LED_BLE. Con el coche encendido, espera a que termine la bienvenida.", "JUANLU_C10LED_BLE not found. With the car on, wait for the welcome to finish.", "JUANLU_C10LED_BLE não encontrado. Com o carro ligado, espere a animação de boas-vindas terminar.", "未找到 JUANLU_C10LED_BLE。请在车辆启动后等待欢迎动画结束。"),
        "m_scanerr" to arrayOf("Error de escaneo ({0})", "Scan error ({0})", "Erro de varredura ({0})", "扫描错误({0})"),
        "m_nosvc" to arrayOf("Servicio BLE no encontrado", "BLE service not found", "Serviço BLE não encontrado", "未找到 BLE 服务"),
        "sy_wifi" to arrayOf("Actualizar firmware y volver al original: solo por Wi-Fi. Conecta el móvil a la red JUANLU_C10LED_WiFi (clave juanludetoledo) y abre la página.", "Firmware update and restore-original: Wi-Fi only. Connect the phone to the JUANLU_C10LED_WiFi network (password juanludetoledo) and open the page.", "Atualizar firmware e voltar ao original: só por Wi-Fi. Ligue o celular à rede JUANLU_C10LED_WiFi (senha juanludetoledo) e abra a página.", "固件更新与恢复原始固件:仅通过 Wi-Fi。请将手机连接到 JUANLU_C10LED_WiFi 网络(密码 juanludetoledo)后打开页面。"),
        "sy_wifi_b" to arrayOf("ABRIR PÁGINA WI-FI (192.168.4.1)", "OPEN WI-FI PAGE (192.168.4.1)", "ABRIR PÁGINA WI-FI (192.168.4.1)", "打开 Wi-Fi 页面 (192.168.4.1)"),
        "sc_bg" to arrayOf("Funciona también con la app cerrada en la mayoría de móviles. Si tu móvil la detiene en segundo plano, se aplica en cuanto abras la app.", "It also works with the app closed on most phones. If your phone stops it in the background, it is applied as soon as you open the app.", "Funciona também com a app fechada na maioria dos celulares. Se o seu a parar em segundo plano, aplica-se assim que abrir a app.", "大多数手机在应用关闭时也能工作。如果系统在后台停止了它,打开应用时会立即应用。"),
        "sc_perm" to arrayOf("Permitir alarmas exactas", "Allow exact alarms", "Permitir alarmes exatos", "允许精确闹钟"),
        "te_pwm" to arrayOf("PWM", "PWM", "PWM", "PWM"),
        "te_conn" to arrayOf("Registro de conexión", "Connection log", "Registo de ligação", "连接日志"),
        "fine_hint" to arrayOf("Valor fino 0–1000", "Fine value 0–1000", "Valor fino 0–1000", "精细值 0–1000"),
        "disconnect" to arrayOf("DESCONECTAR", "DISCONNECT", "DESLIGAR", "断开"),
        "se_try_ok" to arrayOf("Probando (sin guardar)", "Trying (not saved)", "A testar (sem guardar)", "试用中(未保存)"),
        "car_on" to arrayOf("Encender", "Turn on", "Ligar", "开灯"),
        "car_off" to arrayOf("Apagar", "Turn off", "Desligar", "关灯"),
        "car_ctl" to arrayOf("Control", "Control", "Controle", "控制"),
        "car_br" to arrayOf("Brillo", "Brightness", "Brilho", "亮度"),
        "car_fx" to arrayOf("Shows", "Shows", "Shows", "演示"),
        "car_stop" to arrayOf("Parar show", "Stop show", "Parar show", "停止演示"),
        "car_wait" to arrayOf("Sin conexión con la centralita", "No connection to the controller", "Sem ligação à central", "未连接控制器"),
        "sc_modo_hora" to arrayOf("Por hora", "By time", "Por hora", "按时间"),
        "sc_modo_sol" to arrayOf("Por sol", "By sun", "Pelo sol", "按日出日落"),
        "sc_sol_hoy" to arrayOf("Hoy sale / se pone el sol: {0}", "Today sunrise / sunset: {0}", "Hoje nasce / põe-se o sol: {0}", "今天日出 / 日落:{0}"),
        "sc_sol_perm" to arrayOf("Permite la ubicación aproximada para calcular el amanecer y el atardecer.", "Allow approximate location to calculate sunrise and sunset.", "Permita a localização aproximada para calcular o nascer e o pôr do sol.", "请允许大致位置,以计算日出和日落时间。"),
        "sc_sol_noloc" to arrayOf("Obteniendo la ubicación… Mientras tanto se usa la programación por hora.", "Getting your location… The time schedule is used meanwhile.", "A obter a localização… Entretanto usa-se a programação por hora.", "正在获取位置…期间使用按时间定时。"),
        "sc_sol_hint" to arrayOf("DÍA va del amanecer al atardecer y NOCHE del atardecer al amanecer, según el lugar donde esté el móvil. Se calcula en el propio móvil y se actualiza al viajar.", "DAY runs from sunrise to sunset and NIGHT from sunset to sunrise, wherever the phone is. It is calculated on the phone and updates as you travel.", "DIA vai do nascer ao pôr do sol e NOITE do pôr ao nascer do sol, onde o telemóvel estiver. Calcula-se no próprio telemóvel e atualiza-se ao viajar.", "白天为日出到日落,夜间为日落到日出,以手机所在位置为准。在手机上本地计算,旅行时自动更新。"),
        "se_pp_cl" to arrayOf("Clásico", "Classic", "Clássico", "经典"),
        "se_pp_co" to arrayOf("Cometa", "Comet", "Cometa", "彗星"),
        "lv_title" to arrayOf("Vista en vivo de la barra", "Live view of the bar", "Vista em direto da barra", "灯条实时画面"),
        "lv_note" to arrayOf("Muestra lo que está haciendo la barra en este momento (solo con la conexión activa).", "Shows what the bar is doing right now (only while connected).", "Mostra o que a barra está a fazer neste momento (só com a ligação ativa).", "显示灯条此刻的状态(仅在连接时)。"),
        "se_rmin" to arrayOf("Respiración: brillo mínimo", "Breathing: minimum brightness", "Respiração: brilho mínimo", "呼吸:最低亮度"),
        "se_rmax" to arrayOf("Respiración: brillo máximo", "Breathing: maximum brightness", "Respiração: brilho máximo", "呼吸:最高亮度"),
        "se_rminmax_h" to arrayOf("Hasta dónde baja y hasta dónde sube la respiración (0–100). Se refiere al brillo de arranque; si el brillo fijo es menor (modo noche), se reducen en proporción.", "How low and how high the breathing goes (0–100). Relative to the start-up brightness; if the fixed brightness is lower (night mode) they scale down in proportion.", "Até onde desce e até onde sobe a respiração (0–100). Refere-se ao brilho de arranque; se o brilho fixo for menor (modo noite), reduzem-se na mesma proporção.", "呼吸降到多低、升到多高(0–100)。以启动亮度为基准;若固定亮度更低(夜间模式),将按比例降低。"),
        "se_warn" to arrayOf("⚠ AJUSTES DEFINITIVOS. Lo que cambies aquí y guardes con GUARDAR se queda grabado en la centralita y se usará cada vez que arranque el coche. Sin GUARDAR, solo son pruebas temporales.", "⚠ PERMANENT SETTINGS. What you change here and store with SAVE is written to the controller and used every time the car starts. Without SAVE, changes are only temporary tests.", "⚠ AJUSTES DEFINITIVOS. O que alterar aqui e guardar com GUARDAR fica gravado na central e é usado sempre que o carro arranca. Sem GUARDAR, são apenas testes temporários.", "⚠ 永久设置。您在此更改并点按“保存”的内容会写入控制器,每次启动车辆都会使用。未保存时仅为临时试用。"),
        "se_warn_save" to arrayOf("GUARDAR graba estos valores como definitivos para cada arranque.", "SAVE stores these values as permanent for every start-up.", "GUARDAR grava estes valores como definitivos para cada arranque.", "“保存”会将这些值永久写入,用于每次启动。"),
        "sc_lat" to arrayOf("Latitud", "Latitude", "Latitude", "纬度"),
        "sc_lon" to arrayOf("Longitud", "Longitude", "Longitude", "经度"),
        "sc_geo" to arrayOf("USAR MI UBICACIÓN", "USE MY LOCATION", "USAR A MINHA LOCALIZAÇÃO", "使用我的位置"),
        "sc_geo_fail" to arrayOf("El navegador no da la ubicación en esta página: escribe latitud y longitud a mano (por ejemplo, de Google Maps).", "The browser does not give the location on this page: type latitude and longitude by hand (for example from Google Maps).", "O navegador não fornece a localização nesta página: escreva a latitude e a longitude à mão (por exemplo, do Google Maps).", "浏览器在此页面无法获取位置:请手动输入纬度和经度(例如来自谷歌地图)。"),
        "sc_sol_web" to arrayOf("En esta página el modo Por sol necesita las coordenadas y solo se aplica mientras esté abierta.", "On this page By sun needs the coordinates and only applies while it is open.", "Nesta página o modo Pelo sol precisa das coordenadas e só se aplica enquanto estiver aberta.", "在此页面,“按日出日落”需要坐标,且仅在页面打开时生效。"),
    )
}

fun t(clave: String): String = I18n.t(clave)
fun t(clave: String, arg: String): String = I18n.t(clave, arg)
