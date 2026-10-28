package com.dailydo.app

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ceil

class TestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("EXTRA_TASK_ID", -1)
        Log.d("ReminderReceiver", "Alarm fired for task ID: $taskId")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val task = if (taskId != -1) db.todoDao().getById(taskId) else null

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "test_reminder_channel"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Test Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                    notificationManager.createNotificationChannel(channel)
                }

                val title = task?.title ?: "Test Reminder"
                val dueMillis = task?.let { it.dueTimeMillis ?: it.dueDateMillis }
                val text = if (dueMillis != null) {
                    formatTimeRemaining(dueMillis - System.currentTimeMillis())
                } else {
                    task?.description?.ifEmpty { "Task reminder due now" } ?: "5 seconds have passed!"
                }

                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(if (taskId != -1) taskId else 999, notification)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun formatTimeRemaining(diffMillis: Long): String {
        if (diffMillis <= 0L) return "Due now"
        val minutes = ceil(diffMillis / (1000.0 * 60.0)).toInt()
        val hours = ceil(diffMillis / (1000.0 * 60.0 * 60.0)).toInt()
        val days = ceil(diffMillis / (1000.0 * 60.0 * 60.0 * 24.0)).toInt()

        return when {
            diffMillis >= 24 * 60 * 60 * 1000L -> "Due in $days ${if (days == 1) "day" else "days"}"
            diffMillis >= 60 * 60 * 1000L -> "Due in $hours ${if (hours == 1) "hour" else "hours"}"
            else -> "Due in $minutes ${if (minutes == 1) "minute" else "minutes"}"
        }
    }
}
