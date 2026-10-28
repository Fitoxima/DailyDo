package com.dailydo.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val isDone: Boolean = false,
    val priority: String = "Medium",
    val category: String = "Personal",
    val dueDateMillis: Long? = null,
    val dueTimeMillis: Long? = null,
    val reminderTimeMillis: Long? = null
)
