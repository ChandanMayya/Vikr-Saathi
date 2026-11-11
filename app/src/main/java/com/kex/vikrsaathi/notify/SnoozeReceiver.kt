package com.kex.vikrsaathi.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.kex.vikrsaathi.data.repository.NotificationPreferences
import com.kex.vikrsaathi.domain.notify.NotifyKind

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = NotifyKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: return
        NotificationPreferences(context).snooze(kind, System.currentTimeMillis() + 3 * 60 * 60 * 1000L)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_ID, kind.ordinal))
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "notification_id"
    }
}

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationScheduler.reconcile(context)
    }
}
