package com.ashuchat.app.ui.screens

import androidx.compose.runtime.Composable
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.LocalAppTheme
import com.ashuchat.app.ui.viewmodel.ChatViewModel

@Composable
fun PairingScreen(
    viewModel: ChatViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToShowQr: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    if (theme == AppTheme.PAPER) {
        PairingScreenPaper(
            viewModel = viewModel,
            onNavigateToChat = onNavigateToChat,
            onNavigateToShowQr = onNavigateToShowQr,
            onNavigateToScanner = onNavigateToScanner,
            onNavigateBack = onNavigateBack
        )
    } else {
        PairingScreenClassic(
            viewModel = viewModel,
            onNavigateToChat = onNavigateToChat,
            onNavigateToShowQr = onNavigateToShowQr,
            onNavigateToScanner = onNavigateToScanner,
            onNavigateBack = onNavigateBack
        )
    }
}
