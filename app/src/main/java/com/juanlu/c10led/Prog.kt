package com.juanlu.c10led

import android.content.Context
import java.util.Calendar

/** Programacion dia/noche de ESTE movil (cada movil tiene la suya). */
data class Prog(
    val activa: Boolean = true,          // por defecto ENCENDIDA
    val diaDesde: Int = 7 * 60,          // minutos desde las 00:00
    val nocheDesde: Int = 20 * 60,
    val valorDia: Int = 900,             // escala 0..1000 (cualquier valor)
    val valorNoche: Int = 655,
    val sol: Boolean = false,            // true = dia desde el amanecer hasta el atardecer segun la ubicacion
    val lat: Double = Double.NaN,        // ultima ubicacion conocida (la guarda Sol, no Prog.guardar)
    val lon: Double = Double.NaN
) {
    /** Amanecer y atardecer de un dia (epoch ms), solo si el modo sol es valido (hay ubicacion y sol ese dia). */
    private fun solDe(c: Calendar): Pair<Long, Long>? =
        if (!sol || lat.isNaN() || lon.isNaN()) null else Sol.diaSol(lat, lon, c)

    /** El modo sol esta activo y se puede calcular hoy. Si no, se usa la programacion por hora. */
    fun solValido(): Boolean = solDe(Calendar.getInstance()) != null

    fun esDia(minutos: Int): Boolean =
        if (diaDesde < nocheDesde) minutos >= diaDesde && minutos < nocheDesde
        else minutos >= diaDesde || minutos < nocheDesde

    fun esDiaAhora(): Boolean {
        val c = Calendar.getInstance()
        val sd = solDe(c)
        if (sd != null) { val ahora = c.timeInMillis; return ahora >= sd.first && ahora < sd.second }
        return esDia(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
    }

    fun ordenActual(): String = if (esDiaAhora()) "DAY:$valorDia" else "NIGHT:$valorNoche"

    /** Proximo cambio de periodo (milisegundos epoch). */
    fun proximoCambio(): Long {
        val c = Calendar.getInstance()
        if (solDe(c) != null) {
            val ahora = c.timeInMillis
            val eventos = ArrayList<Long>()
            for (d in 0..2) {
                val x = Calendar.getInstance(); x.add(Calendar.DAY_OF_YEAR, d)
                solDe(x)?.let { eventos.add(it.first); eventos.add(it.second) }
            }
            eventos.filter { it > ahora + 1000L }.minOrNull()?.let { return it }
            return ahora + 6 * 3600_000L        // sin eventos proximos: se vuelve a calcular en unas horas
        }
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
                sp.getInt("sch_nv", d.valorNoche).coerceIn(0, 1000),
                sp.getBoolean("sch_sol", false),
                Sol.lat(ctx), Sol.lon(ctx)
            )
        }
        fun guardar(ctx: Context, p: Prog) {
            ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
                .putBoolean("sch_on", p.activa).putInt("sch_day", p.diaDesde).putInt("sch_night", p.nocheDesde)
                .putInt("sch_dv", p.valorDia).putInt("sch_nv", p.valorNoche).putBoolean("sch_sol", p.sol).apply()
        }
        fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
        fun hhmmMs(ms: Long): String { val c = Calendar.getInstance(); c.timeInMillis = ms; return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) }
    }
}
