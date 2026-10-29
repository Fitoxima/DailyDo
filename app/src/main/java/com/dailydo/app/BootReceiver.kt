package com.dailydo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed received, rescheduling reminders...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val tasks = db.todoDao().getAllDirect()
                    val currentTime = System.currentTimeMillis()
                    for (task in tasks) {
                        if (!task.isDone) {
                            val reminderTime = task.reminderTimeMillis
                            if (reminderTime != null && reminderTime > currentTime) {
                                TestReminderHelper.scheduleReminder(context, task)
                            }
                        }
                    }
                    Log.d("BootReceiver", "Successfully rescheduled alarms after reboot.")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
