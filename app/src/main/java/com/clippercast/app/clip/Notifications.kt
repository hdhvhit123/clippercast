package com.clippercast.app.clip

import com.clippercast.app.tr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.clippercast.app.MainActivity
import com.clippercast.app.R

object Notifications {
    const val CH_CLIP = "clip"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_CLIP, tr(R.string.ch_clip), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr(R.string.ch_clip_desc)
            }
        )
    }

    private fun canPost(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun articleIntent(context: Context, action: String, id: Long): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            this.action = action
            putExtra(MainActivity.EXTRA_ID, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, (action.hashCode() * 31 + id).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun clipped(context: Context, id: Long, title: String, partial: Boolean) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_CLIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (partial) tr(R.string.notif_saved_partial) else tr(R.string.notif_saved))
            .setContentText(title)
            .setContentIntent(articleIntent(context, MainActivity.ACTION_OPEN, id))
            .addAction(0, tr(R.string.read_now), articleIntent(context, MainActivity.ACTION_OPEN, id))
            .addAction(0, tr(R.string.listen_now), articleIntent(context, MainActivity.ACTION_LISTEN, id))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        NotificationManagerCompat.from(context).notify(id.toInt(), n)
    }

    fun failed(context: Context, id: Long, title: String, reason: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_CLIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr(R.string.notif_failed))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$reason"))
            .setContentIntent(articleIntent(context, MainActivity.ACTION_OPEN, id))
            .addAction(0, tr(R.string.open_to_sign_in_short), articleIntent(context, MainActivity.ACTION_BROWSER, id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id.toInt(), n)
    }
}
