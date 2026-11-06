package com.juanlu.c10led

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.*

/**
 * Amanecer y atardecer calculados en el propio movil (sin internet) a partir de la ubicacion
 * aproximada. Algoritmo "sunrise equation" (error de uno o dos minutos).
 */
object Sol {
    /** Epoch ms de (amanecer, atardecer) del dia local indicado; null si no hay (sol polar). */
    fun diaSol(lat: Double, lon: Double, dia: Calendar): Pair<Long, Long>? {
        val c = dia.clone() as Calendar
        // Numero de dia (a mediodia UTC) del dia civil local
        val epochDay = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
        }.timeInMillis / 86400000.0
        val jd = epochDay + 2440587.5
        val n = Math.round(jd - 2451545.0 + 0.0008).toDouble()
        val js = n + 0.0009 - lon / 360.0
        val m = Math.toRadians((357.5291 + 0.98560028 * js) % 360.0)
        val cc = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lam = Math.toRadians((Math.toDegrees(m) + cc + 180.0 + 102.9372) % 360.0)
        val jt = 2451545.0 + js + 0.0053 * sin(m) - 0.0069 * sin(2 * lam)
        val sinD = sin(lam) * sin(Math.toRadians(23.4397))
        val cosD = cos(asin(sinD))
        val phi = Math.toRadians(lat)
        val cosW = (sin(Math.toRadians(-0.833)) - sin(phi) * sinD) / (cos(phi) * cosD)
        if (cosW.isNaN() || cosW < -1.0 || cosW > 1.0) return null
        val w = Math.toDegrees(acos(cosW))
        val sale = jt - w / 360.0
        val pone = jt + w / 360.0
        fun ms(j: Double) = ((j - 2440587.5) * 86400000.0).toLong()
        return Pair(ms(sale), ms(pone))
    }

    // ---- Ubicacion guardada (la ultima conocida del movil) ----
    private const val P = "c10prefs"
    fun lat(ctx: Context): Double = leer(ctx, "sol_lat")
    fun lon(ctx: Context): Double = leer(ctx, "sol_lon")
    private fun leer(ctx: Context, k: String): Double {
        val sp = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        return if (sp.contains(k)) java.lang.Double.longBitsToDouble(sp.getLong(k, 0L)) else Double.NaN
    }
    private fun guardar(ctx: Context, l: Location) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putLong("sol_lat", java.lang.Double.doubleToRawLongBits(l.latitude))
            .putLong("sol_lon", java.lang.Double.doubleToRawLongBits(l.longitude)).apply()
    }

    fun tienePermiso(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Actualiza la ubicacion guardada con la mejor ultima conocida; si no hay, pide una nueva. */
    @SuppressLint("MissingPermission")
    fun actualizar(ctx: Context, alTerminar: () -> Unit = {}) {
        try {
            if (!tienePermiso(ctx)) { alTerminar(); return }
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var mejor: Location? = null
            for (pv in lm.getProviders(true)) {
                val l = try { lm.getLastKnownLocation(pv) } catch (e: Exception) { null } ?: continue
                if (mejor == null || l.time > mejor.time) mejor = l
            }
            if (mejor != null) { guardar(ctx, mejor); alTerminar(); return }
            if (Build.VERSION.SDK_INT >= 30) {
                val pv = lm.getProviders(true).firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
                if (pv != null) {
                    lm.getCurrentLocation(pv, null, ContextCompat.getMainExecutor(ctx)) { l ->
                        if (l != null) guardar(ctx, l)
                        alTerminar()
                    }
                    return
                }
            }
        } catch (e: Exception) { }
        alTerminar()
    }
}
