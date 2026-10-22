package com.ashuchat.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ashuchat.app.service.ChatSyncService
import com.ashuchat.app.ui.components.BiometricLockOverlay
import com.ashuchat.app.ui.screens.AboutScreen
import com.ashuchat.app.ui.screens.ChatScreen
import com.ashuchat.app.ui.screens.ChatSettingsScreen
import com.ashuchat.app.ui.screens.PairingScreen
import com.ashuchat.app.ui.screens.ProfileScreen
import com.ashuchat.app.ui.screens.QrCodeScreen
import com.ashuchat.app.ui.screens.QrScannerScreen
import com.ashuchat.app.ui.screens.RelayScreen
import com.ashuchat.app.ui.screens.RoomsScreen
import com.ashuchat.app.ui.theme.AshuChatTheme
import com.ashuchat.app.ui.theme.MidnightBg
import com.ashuchat.app.ui.viewmodel.ChatViewModel
import com.ashuchat.app.utils.BiometricHelper

class MainActivity : FragmentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private var isAppUnlocked by mutableStateOf(true)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply FLAG_SECURE if enabled in privacy settings
        updateFlagSecure()

        // Check Biometric lock
        if (chatViewModel.prefs.isBiometricEnabled && BiometricHelper.isBiometricAvailable(this)) {
            isAppUnlocked = false
            triggerBiometricAuth()
        }

        // Request POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start P3 Background Sync Service (foreground service to survive Doze mode)
        ChatSyncService.startService(this)

        setContent {
            AshuChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MidnightBg
                ) {
                    if (isAppUnlocked) {
                        AppNavigation(chatViewModel = chatViewModel)
                    } else {
                        BiometricLockOverlay(
                            onUnlockClick = { triggerBiometricAuth() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateFlagSecure()
        if (chatViewModel.prefs.isBiometricEnabled && !isAppUnlocked && BiometricHelper.isBiometricAvailable(this)) {
            triggerBiometricAuth()
        }
    }

    private fun updateFlagSecure() {
        if (chatViewModel.prefs.isFlagSecureEnabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun triggerBiometricAuth() {
        BiometricHelper.authenticate(
            activity = this,
            title = "Unlock AshuChat",
            subtitle = "Verify identity to access private chats",
            onSuccess = { isAppUnlocked = true },
            onError = { /* Keep locked */ }
        )
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
                onNavigateToProfile = {
                    navController.navigate("settings/profile")
                },
                onNavigateToRelay = {
                    navController.navigate("settings/relay")
                },
                onNavigateToChatSettings = {
                    navController.navigate("settings/chat")
                },
                onNavigateToAbout = {
                    navController.navigate("settings/about")
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

        composable("settings/profile") {
            ProfileScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings/relay") {
            RelayScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings/chat") {
            ChatSettingsScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings/about") {
            AboutScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            ProfileScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
