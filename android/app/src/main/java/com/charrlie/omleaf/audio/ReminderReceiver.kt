package com.charrlie.omleaf.audio

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.charrlie.omleaf.MainActivity
import com.charrlie.omleaf.OmLeafApp
import com.charrlie.omleaf.R
import com.charrlie.omleaf.domain.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Shows the daily reminder, then books tomorrow's. Also re-books after a reboot. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = (context.applicationContext as OmLeafApp).repository.settings.first()
                if (!s.reminderOn) return@launch
                if (intent.action != Intent.ACTION_BOOT_COMPLETED) notify(context)
                schedule(context, s.reminderMinute)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle("Time to breathe")
            .setContentText("A few quiet minutes with OmLeaf.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    companion object {
        private const val CHANNEL = "reminder"
        private const val ID = 8

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** An inexact alarm: it can slip a little to save battery, and needs no special permission. */
        fun schedule(context: Context, minuteOfDay: Int) {
            val at = Reminder.nextTriggerMs(System.currentTimeMillis(), ZoneId.systemDefault(), minuteOfDay)
            context.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        }
    }
}
