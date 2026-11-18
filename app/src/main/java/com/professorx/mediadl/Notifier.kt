package com.professorx.mediadl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

object Notifier {
    private const val CH_PROGRESS = "progress"
    private const val CH_DONE = "done"
    const val ID_PROGRESS = 1
    private const val ID_DONE = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Download complete", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun progress(ctx: Context, text: String, pct: Int, indeterminate: Boolean): Notification =
        Notification.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MediaDown")
            .setContentText(text)
            .setProgress(100, pct.coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()

    fun updateProgress(ctx: Context, text: String, pct: Int, indeterminate: Boolean) {
        try { ctx.getSystemService(NotificationManager::class.java).notify(ID_PROGRESS, progress(ctx, text, pct, indeterminate)) } catch (_: Throwable) {}
    }

    fun cancelProgress(ctx: Context) {
        try { ctx.getSystemService(NotificationManager::class.java).cancel(ID_PROGRESS) } catch (_: Throwable) {}
    }

    fun done(ctx: Context, title: String, text: String, uri: Uri?, mime: String?) {
        try {
            val open = if (uri != null && mime != null) PendingIntent.getActivity(
                ctx, 1,
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ) else openApp(ctx)
            val n = Notification.Builder(ctx, CH_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(ID_DONE, n)
        } catch (_: Throwable) {}
    }
}
