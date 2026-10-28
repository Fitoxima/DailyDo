package com.dailydo.app

import androidx.compose.ui.graphics.Color

val PriorityLowColor    = Color(0xFF66BB6A)   // green
val PriorityMediumColor = Color(0xFFFFB300)   // yellow/amber
val PriorityHighColor   = Color(0xFFE53935)   // red

fun priorityColor(priority: String): Color = when (priority) {
    "Low"    -> PriorityLowColor
    "Medium" -> PriorityMediumColor
    "High"   -> PriorityHighColor
    else     -> Color(0xFF9E9E9E)
}
