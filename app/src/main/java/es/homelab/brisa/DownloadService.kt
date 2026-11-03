package es.homelab.brisa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Servicio en primer plano mientras hay descargas: Android no cierra Brisa a mitad y la
 * notificación muestra el progreso. Se para solo cuando no queda ninguna en curso.
 * En descargas de pestañas privadas no se muestra el nombre del archivo.
 */
class DownloadService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val p = Downloads.progress()
            if (p == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return }
            getSystemService(NotificationManager::class.java).notify(ID, build(p))
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build(Downloads.progress() ?: -1f)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        main.removeCallbacks(tick); main.postDelayed(tick, 1000)
        return START_NOT_STICKY
    }

    override fun onDestroy() { main.removeCallbacks(tick); super.onDestroy() }

    // Android 15 limita las descargas en segundo plano a unas horas: si se agota, se para con elegancia
    override fun onTimeout(startId: Int, fgsType: Int) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }

    private fun build(p: Float): Notification {
        val active = Downloads.items.filter { it.status == "Descargando" }
        val title = when {
            active.size > 1 -> "Descargando ${active.size} archivos"
            active.firstOrNull()?.private == true -> "Descargando un archivo"
            else -> active.firstOrNull()?.name ?: "Descargando"
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (p >= 0f) "${(p * 100).toInt()} %" else "Calculando…")
            .setProgress(100, if (p >= 0f) (p * 100).toInt() else 0, p < 0f)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .setVisibility(if (active.any { it.private }) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        private const val ID = 7301
        private const val CHANNEL = "descargas"
        fun start(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW))
            runCatching {
                val i = Intent(ctx, DownloadService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }
    }
}
