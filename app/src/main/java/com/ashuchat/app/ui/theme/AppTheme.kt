package com.ashuchat.app.ui.theme

enum class AppTheme(val id: String, val displayName: String) {
    CLASSIC("classic", "Classic Dark"),
    PAPER("paper", "Paper UI");

    companion object {
        fun fromId(id: String?): AppTheme {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: CLASSIC
        }
    }
}
