package com.ashuchat.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ashuchat.app.ui.screens.ChatScreen
import com.ashuchat.app.ui.screens.PairingScreen
import com.ashuchat.app.ui.screens.QrScannerScreen
import com.ashuchat.app.ui.screens.SettingsScreen
import com.ashuchat.app.ui.theme.AshuChatTheme
import com.ashuchat.app.ui.theme.MidnightBg
import com.ashuchat.app.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AshuChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MidnightBg
                ) {
                    AppNavigation(chatViewModel = chatViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(chatViewModel: ChatViewModel) {
    val navController = rememberNavController()
    val prefs = chatViewModel.prefs

    // Start on Chat if already paired, else start on Pairing
    val startDestination = if (prefs.roomId.isNotBlank()) "chat" else "pairing"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("pairing") {
            PairingScreen(
                prefs = prefs,
                onNavigateToChat = {
                    navController.navigate("chat") {
                        popUpTo("pairing") { inclusive = true }
                    }
                },
                onNavigateToScanner = {
                    navController.navigate("qr_scanner")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("chat") {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.navigate("pairing")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("qr_scanner") {
            QrScannerScreen(
                onRoomScanned = { scannedRoomId ->
                    prefs.roomId = scannedRoomId.trim().uppercase()
                    navController.navigate("chat") {
                        popUpTo("pairing") { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                onClearChatHistory = {
                    chatViewModel.clearChat()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
