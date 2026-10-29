package com.dailydo.app

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                val text = when {
                    dueMillis != null -> {
                        val reminderTime = task.reminderTimeMillis
                        if (reminderTime != null && reminderTime == dueMillis) {
                            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(dueMillis))
                            "Task is due at $timeStr"
                        } else {
                            formatTimeRemaining(dueMillis - System.currentTimeMillis())
                        }
                    }
                    else -> task?.description?.ifEmpty { "Task reminder due now" } ?: "5 seconds have passed!"
                }

                // Mark as done action intent
                val markDoneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = "ACTION_MARK_DONE"
                    putExtra("EXTRA_TASK_ID", taskId)
                }
                val markDonePendingIntent = PendingIntent.getBroadcast(
                    context,
                    taskId + 200000,
                    markDoneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Postpone action intent (opens MainActivity in edit mode)
                val postponeIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("EXTRA_TASK_ID", taskId)
                    putExtra("EXTRA_OPEN_EDIT", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val postponePendingIntent = PendingIntent.getActivity(
                    context,
                    taskId + 300000,
                    postponeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Main notification body click intent (opens Tasks screen)
                val clickIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("EXTRA_TASK_ID", taskId)
                    putExtra("EXTRA_OPEN_TASKS", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val clickPendingIntent = PendingIntent.getActivity(
                    context,
                    taskId + 400000,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setTimeoutAfter(15000L)
                    .setContentIntent(clickPendingIntent)
                    .addAction(R.drawable.checkbox_on_background, "Mark as done", markDonePendingIntent)
                    .addAction(R.drawable.ic_menu_recent_history, "Postpone", postponePendingIntent)
                    .build()

                notificationManager.notify(if (taskId != -1) taskId else 999, notification)

                if (taskId != -1) {
                    TestReminderHelper.scheduleLoopAlarm(context, taskId)
                }
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
