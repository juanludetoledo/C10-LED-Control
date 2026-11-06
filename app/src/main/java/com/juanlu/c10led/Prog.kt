package com.juanlu.c10led

import android.content.Context
import java.util.Calendar

/** Programacion dia/noche de ESTE movil (cada movil tiene la suya). */
data class Prog(
    val activa: Boolean = true,          // por defecto ENCENDIDA
    val diaDesde: Int = 7 * 60,          // minutos desde las 00:00
    val nocheDesde: Int = 20 * 60,
    val valorDia: Int = 900,             // escala 0..1000 (cualquier valor)
    val valorNoche: Int = 655
) {
    fun esDia(minutos: Int): Boolean =
        if (diaDesde < nocheDesde) minutos >= diaDesde && minutos < nocheDesde
        else minutos >= diaDesde || minutos < nocheDesde

    fun esDiaAhora(): Boolean {
        val c = Calendar.getInstance()
        return esDia(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
    }

    fun ordenActual(): String = if (esDiaAhora()) "DAY:$valorDia" else "NIGHT:$valorNoche"

    /** Proximo cambio de periodo (milisegundos epoch). */
    fun proximoCambio(): Long {
        val c = Calendar.getInstance()
        val ahora = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val destino = if (esDiaAhora()) nocheDesde else diaDesde
        var delta = destino - ahora
        if (delta <= 0) delta += 24 * 60
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MINUTE, delta)
        return c.timeInMillis
    }

    companion object {
        private const val P = "c10prefs"
        fun cargar(ctx: Context): Prog {
            val sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            val d = Prog()
            return Prog(
                sp.getBoolean("sch_on", d.activa),
                sp.getInt("sch_day", d.diaDesde).coerceIn(0, 1439),
                sp.getInt("sch_night", d.nocheDesde).coerceIn(0, 1439),
                sp.getInt("sch_dv", d.valorDia).coerceIn(0, 1000),
                sp.getInt("sch_nv", d.valorNoche).coerceIn(0, 1000)
            )
        }
        fun guardar(ctx: Context, p: Prog) {
            ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
                .putBoolean("sch_on", p.activa).putInt("sch_day", p.diaDesde).putInt("sch_night", p.nocheDesde)
                .putInt("sch_dv", p.valorDia).putInt("sch_nv", p.valorNoche).apply()
        }
        fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
    }
}
