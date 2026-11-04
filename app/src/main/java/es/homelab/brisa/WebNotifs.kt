package es.homelab.brisa

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate

/**
 * Notificaciones de las webs (#23): Gecko las entrega aquí y se muestran como notificaciones de Android.
 * Tocarla abre Brisa y avisa a la web (como si se pulsara en el navegador); quitarla, también.
 * Funcionan mientras Brisa está abierta o en segundo plano. Las que llegan con la app cerrada
 * (Web Push) necesitarían un servicio de envío y no están soportadas.
 */
class WebNotifs(private val ctx: Context) : WebNotificationDelegate {
    override fun onShowNotification(n: WebNotification) {
        live[n.tag] = n
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Las que la web marca como silenciosas van a un canal que no suena ni vibra
        val channel = if (n.silent) CHANNEL_SILENT else CHANNEL
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Notificaciones de las webs", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_SILENT, "Notificaciones de las webs (silenciosas)", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(ctx, n.tag.hashCode(),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_TAG, n.tag),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val gone = PendingIntent.getBroadcast(ctx, n.tag.hashCode(),
            Intent(ctx, Dismissed::class.java).putExtra(EXTRA_TAG, n.tag), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, channel) else android.app.Notification.Builder(ctx)
        val site = History.hostOf(n.source ?: "")
        b.setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(n.title ?: site)
            .setContentText(n.text ?: "")
            .setSubText(site)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setDeleteIntent(gone)
            .setStyle(android.app.Notification.BigTextStyle().bigText(n.text ?: ""))
        runCatching { nm.notify(n.tag.hashCode(), b.build()) }
    }

    override fun onCloseNotification(n: WebNotification) {
        live.remove(n.tag)
        ctx.getSystemService(NotificationManager::class.java).cancel(n.tag.hashCode())
    }

    /** Al quitar la notificación deslizándola, se avisa a la web. */
    class Dismissed : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { i.getStringExtra(EXTRA_TAG)?.let { live.remove(it)?.dismiss() } }
    }

    companion object {
        const val CHANNEL = "webs"
        const val CHANNEL_SILENT = "webs_silencio"
        const val EXTRA_TAG = "brisa.notificacion"
        private val live = java.util.concurrent.ConcurrentHashMap<String, WebNotification>()
        /** Se tocó la notificación: la web decide qué abrir (normalmente su propia página). */
        fun clicked(tag: String) { live.remove(tag)?.click() }
    }
}
