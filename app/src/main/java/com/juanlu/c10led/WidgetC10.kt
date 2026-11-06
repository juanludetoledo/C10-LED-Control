package com.juanlu.c10led

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

/** Widget de pantalla de inicio: botones OFF y ON. */
class WidgetC10 : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_c10)
            v.setOnClickPendingIntent(R.id.w_off, pi(ctx, "OFF", 21))
            v.setOnClickPendingIntent(R.id.w_on, pi(ctx, "ON", 22))
            mgr.updateAppWidget(id, v)
        }
    }

    private fun pi(ctx: Context, cmd: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, WidgetOrdenReceiver::class.java).setAction("com.juanlu.c10led.WIDGET").putExtra("cmd", cmd),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class WidgetOrdenReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val app = ctx.applicationContext
        I18n.iniciar(app)
        val pend = goAsync()
        EnviarBle(app, listOf(cmd)) { ok ->
            if (!ok) Toast.makeText(app, t("car_wait"), Toast.LENGTH_SHORT).show()
            try { pend.finish() } catch (e: Exception) { }
        }.ejecutar()
    }
}
