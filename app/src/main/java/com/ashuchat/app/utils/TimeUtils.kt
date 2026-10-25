package com.ashuchat.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {

    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    private val monthDayFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d", Locale.getDefault())
    }

    private val fullDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    }

    fun formatRoomTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = Calendar.getInstance()
        val time = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isToday = now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR)
        if (isToday) {
            return timeFormat.get()?.format(Date(timestamp)) ?: ""
        }

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return "Yesterday"
        }

        val isSameYear = now.get(Calendar.YEAR) == time.get(Calendar.YEAR)
        return if (isSameYear) {
            monthDayFormat.get()?.format(Date(timestamp)) ?: ""
        } else {
            fullDateFormat.get()?.format(Date(timestamp)) ?: ""
        }
    }

    fun formatMessageTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return timeFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatCurrentHeaderTime(): String {
        return timeFormat.get()?.format(Date())?.uppercase() ?: ""
    }

    fun formatCallDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}
