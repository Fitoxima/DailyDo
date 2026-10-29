package com.dailydo.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val taskId = intent.getIntExtra("EXTRA_TASK_ID", -1)
        Log.d("NotifActionReceiver", "Received action $action for task ID: $taskId")

        if (taskId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                when (action) {
                    "ACTION_MARK_DONE" -> {
                        TestReminderHelper.cancelLoopAlarm(context, taskId)
                        TestReminderHelper.cancelReminder(context, taskId)
                        val task = db.todoDao().getById(taskId)
                        if (task != null) {
                            db.todoDao().update(task.copy(isDone = true))
                        }
                        notificationManager.cancel(taskId)
                        Log.d("NotifActionReceiver", "Task $taskId marked as done via notification action.")
                    }
                    "ACTION_LOOP_REMINDER" -> {
                        val task = db.todoDao().getById(taskId)
                        if (task != null && !task.isDone && task.reminderTimeMillis != null) {
                            val reminderIntent = Intent(context, TestReminderReceiver::class.java).apply {
                                putExtra("EXTRA_TASK_ID", taskId)
                            }
                            context.sendBroadcast(reminderIntent)
                            Log.d("NotifActionReceiver", "Loop reminder re-triggered for task $taskId")
                        } else {
                            TestReminderHelper.cancelLoopAlarm(context, taskId)
                            Log.d("NotifActionReceiver", "Task $taskId is done or reminder cancelled, stopping loop.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotifActionReceiver", "Error handling notification action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
