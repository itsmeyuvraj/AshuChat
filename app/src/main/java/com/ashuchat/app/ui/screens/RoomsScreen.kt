package com.ashuchat.app.ui.screens

import androidx.compose.runtime.Composable
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.LocalAppTheme
import com.ashuchat.app.ui.viewmodel.ChatViewModel

@Composable
fun RoomsScreen(
    viewModel: ChatViewModel,
    onRoomClick: (roomId: String) -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToRelay: () -> Unit,
    onNavigateToChatSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val theme = LocalAppTheme.current
    if (theme == AppTheme.PAPER) {
        RoomsScreenPaper(
            viewModel = viewModel,
            onRoomClick = onRoomClick,
            onNavigateToPairing = onNavigateToPairing,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToRelay = onNavigateToRelay,
            onNavigateToChatSettings = onNavigateToChatSettings,
            onNavigateToAbout = onNavigateToAbout
        )
    } else {
        RoomsScreenClassic(
            viewModel = viewModel,
            onRoomClick = onRoomClick,
            onNavigateToPairing = onNavigateToPairing,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToRelay = onNavigateToRelay,
            onNavigateToChatSettings = onNavigateToChatSettings,
            onNavigateToAbout = onNavigateToAbout
        )
    }
}
