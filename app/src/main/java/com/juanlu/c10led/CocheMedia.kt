package com.juanlu.c10led

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * Pantalla sencilla para Android Auto (modo "app multimedia"): lista con Encender, Apagar,
 * brillos predefinidos y shows. Solo para usar con el coche parado.
 * En Android Auto hay que activar el modo desarrollador y "Fuentes desconocidas" para que aparezca.
 */
class CocheMedia : MediaBrowserServiceCompat() {
    private lateinit var sesion: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        I18n.iniciar(applicationContext)
        sesion = MediaSessionCompat(this, "C10LED")
        sesion.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { ejecutar(mediaId) }
            override fun onPlay() { }
        })
        estado(PlaybackStateCompat.STATE_PAUSED)
        sesion.isActive = true
        sessionToken = sesion.sessionToken
    }

    override fun onDestroy() { sesion.release(); super.onDestroy() }

    private fun estado(s: Int) {
        sesion.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PAUSE)
                .setState(s, 0L, 1f).build()
        )
    }

    private fun ejecutar(id: String?) {
        val cmd = id?.removePrefix("cmd:") ?: return
        if (id == cmd) return
        estado(PlaybackStateCompat.STATE_PLAYING)
        EnviarBle(applicationContext, listOf(cmd)) { _ -> estado(PlaybackStateCompat.STATE_PAUSED) }.ejecutar()
    }

    // Al abrir la pantalla en Android Auto se cierra la app principal para dejar libre el Bluetooth de la centralita.
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        MainActivity.cerrarPorCoche()
        return BrowserRoot("raiz", null)
    }

    private fun item(id: String, titulo: String, carpeta: Boolean): MediaBrowserCompat.MediaItem {
        val d = MediaDescriptionCompat.Builder().setMediaId(id).setTitle(titulo).build()
        return MediaBrowserCompat.MediaItem(d, if (carpeta) MediaBrowserCompat.MediaItem.FLAG_BROWSABLE else MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val l = mutableListOf<MediaBrowserCompat.MediaItem>()
        when (parentId) {
            "raiz" -> {
                l += item("ctl", t("car_ctl"), true)
                l += item("br", t("car_br"), true)
                l += item("fx", t("car_fx"), true)
            }
            "ctl" -> {
                l += item("cmd:ON", t("car_on"), false)
                l += item("cmd:OFF", t("car_off"), false)
            }
            "br" -> {
                for (v in listOf(20, 40, 60, 66, 80, 100)) l += item("cmd:BR:${v * 10}", "${t("car_br")} $v", false)
            }
            "fx" -> {
                for (i in 1..9) l += item("cmd:SHOW:$i,30", t("th$i"), false)
                l += item("cmd:SHOW:STOP", t("car_stop"), false)
            }
        }
        result.sendResult(l)
    }
}
