package com.kex.vikrsaathi.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kex.vikrsaathi.MainActivity
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.data.repository.NotificationPreferences
import com.kex.vikrsaathi.domain.notify.NotifyKind
import java.util.concurrent.TimeUnit

object NotificationChannels {
    const val PAYMENTS = "vikrsaathi.payments"
    const val SUMMARIES = "vikrsaathi.summaries"
    const val INVENTORY = "vikrsaathi.inventory"
    const val BACKUP = "vikrsaathi.backup"

    fun channelFor(kind: NotifyKind): String = when (kind) {
        NotifyKind.PAYMENT_DUE, NotifyKind.CUSTOMER_DIGEST, NotifyKind.VENDOR_DIGEST -> PAYMENTS
        NotifyKind.DAILY_SALES, NotifyKind.WEEKLY_SALES -> SUMMARIES
        NotifyKind.LOW_STOCK, NotifyKind.ORDER_FOLLOWUP, NotifyKind.EXPIRY -> INVENTORY
        NotifyKind.BACKUP -> BACKUP
    }

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            PAYMENTS to "Payment reminders",
            SUMMARIES to "Business summaries",
            INVENTORY to "Inventory and orders",
            BACKUP to "Backup reminders"
        ).forEach { (id, name) ->
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
            channel.setShowBadge(false)
            channel.lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            manager.createNotificationChannel(channel)
        }
    }

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun channelEnabled(context: Context, channelId: String): Boolean {
        if (!notificationsEnabled(context)) return false
        if (Build.VERSION.SDK_INT < 26) return true
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}

object NotificationScheduler {
    const val WORK_NAME = "vikrsaathi.notifications"

    fun reconcile(context: Context) {
        val enabled = NotificationPreferences(context).enabled
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        NotificationChannels.ensure(context)
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
            )
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

object NotificationPoster {
    fun show(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        body: String,
        destination: String,
        kind: NotifyKind?,
        revealOnLockScreen: Boolean
    ) {
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NAV, destination)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(
                if (revealOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_PRIVATE
            )
            .setPublicVersion(
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.notify_lock_private))
                    .build()
            )
        if (kind != null) {
            val snooze = PendingIntent.getBroadcast(
                context,
                kind.ordinal,
                Intent(context, SnoozeReceiver::class.java)
                    .putExtra(SnoozeReceiver.EXTRA_KIND, kind.name)
                    .putExtra(SnoozeReceiver.EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, context.getString(R.string.notify_snooze), snooze)
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
