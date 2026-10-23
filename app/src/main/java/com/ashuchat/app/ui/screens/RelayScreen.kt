package com.ashuchat.app.ui.screens

import androidx.compose.runtime.Composable
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.LocalAppTheme
import com.ashuchat.app.ui.viewmodel.ChatViewModel

@Composable
fun RelayScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val theme = LocalAppTheme.current
    if (theme == AppTheme.PAPER) {
        RelayScreenPaper(
            viewModel = viewModel,
            onNavigateBack = onNavigateBack
        )
    } else {
        RelayScreenClassic(
            viewModel = viewModel,
            onNavigateBack = onNavigateBack
        )
    }
}
