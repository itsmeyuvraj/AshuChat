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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ashuchat.app.ui.screens.ChatScreen
import com.ashuchat.app.ui.screens.PairingScreen
import com.ashuchat.app.ui.screens.QrCodeScreen
import com.ashuchat.app.ui.screens.QrScannerScreen
import com.ashuchat.app.ui.screens.RoomsScreen
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

    // Main destination is WhatsApp-style Rooms list screen
    NavHost(
        navController = navController,
        startDestination = "rooms"
    ) {
        composable("rooms") {
            RoomsScreen(
                viewModel = chatViewModel,
                onRoomClick = { roomId ->
                    chatViewModel.openRoom(roomId)
                    navController.navigate("chat")
                },
                onNavigateToPairing = {
                    navController.navigate("pairing")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("pairing") {
            PairingScreen(
                viewModel = chatViewModel,
                onNavigateToChat = {
                    navController.navigate("chat") {
                        popUpTo("rooms") { inclusive = false }
                    }
                },
                onNavigateToShowQr = { roomId ->
                    navController.navigate("qr_code/$roomId")
                },
                onNavigateToScanner = {
                    navController.navigate("qr_scanner")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("chat") {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate("rooms") {
                            popUpTo("chat") { inclusive = true }
                        }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToShowQr = { roomId ->
                    navController.navigate("qr_code/$roomId")
                }
            )
        }

        composable(
            route = "qr_code/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: prefs.roomId
            QrCodeScreen(
                roomId = roomId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = {
                    chatViewModel.openRoom(roomId)
                    navController.navigate("chat") {
                        popUpTo("rooms") { inclusive = false }
                    }
                }
            )
        }

        composable("qr_code") {
            val currentRoom = chatViewModel.activeRoomId.collectAsState().value.ifBlank { prefs.roomId }
            QrCodeScreen(
                roomId = currentRoom,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = {
                    chatViewModel.openRoom(currentRoom)
                    navController.navigate("chat") {
                        popUpTo("rooms") { inclusive = false }
                    }
                }
            )
        }

        composable("qr_scanner") {
            QrScannerScreen(
                onRoomScanned = { scannedRoomId ->
                    val clean = scannedRoomId.trim().uppercase()
                    chatViewModel.createOrJoinRoom(clean)
                    chatViewModel.openRoom(clean)
                    navController.navigate("chat") {
                        popUpTo("rooms") { inclusive = false }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
