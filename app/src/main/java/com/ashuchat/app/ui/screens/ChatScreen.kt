package com.ashuchat.app.ui.screens

import androidx.compose.runtime.Composable
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.LocalAppTheme
import com.ashuchat.app.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToShowQr: ((String) -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    if (theme == AppTheme.PAPER) {
        ChatScreenPaper(
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onNavigateToShowQr = onNavigateToShowQr
        )
    } else {
        ChatScreenClassic(
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onNavigateToShowQr = onNavigateToShowQr
        )
    }
}
