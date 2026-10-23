package com.ashuchat.app.ui.screens

import androidx.compose.runtime.Composable
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.LocalAppTheme

@Composable
fun QrScannerScreen(
    onRoomScanned: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    if (LocalAppTheme.current == AppTheme.PAPER) {
        QrScannerScreenPaper(
            onRoomScanned = onRoomScanned,
            onNavigateBack = onNavigateBack
        )
    } else {
        QrScannerScreenClassic(
            onRoomScanned = onRoomScanned,
            onNavigateBack = onNavigateBack
        )
    }
}
