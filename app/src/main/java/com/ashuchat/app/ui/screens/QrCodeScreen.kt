package com.ashuchat.app.ui.screens

import androidx.compose.runtime.Composable
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.LocalAppTheme

@Composable
fun QrCodeScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val theme = LocalAppTheme.current
    if (theme == AppTheme.PAPER) {
        QrCodeScreenPaper(
            roomId = roomId,
            onNavigateBack = onNavigateBack,
            onNavigateToChat = onNavigateToChat
        )
    } else {
        QrCodeScreenClassic(
            roomId = roomId,
            onNavigateBack = onNavigateBack,
            onNavigateToChat = onNavigateToChat
        )
    }
}
