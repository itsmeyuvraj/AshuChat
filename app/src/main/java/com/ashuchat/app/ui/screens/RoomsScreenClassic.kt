package com.ashuchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import com.ashuchat.app.ui.components.UpdateDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.data.local.RoomEntity
import com.ashuchat.app.ui.theme.AccentIndigo
import com.ashuchat.app.ui.theme.AccentTeal
import com.ashuchat.app.ui.theme.BrandDark
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.BrandPrimary
import com.ashuchat.app.ui.theme.MidnightBg
import com.ashuchat.app.ui.theme.OnlineGreen
import com.ashuchat.app.ui.theme.SurfaceBorder
import com.ashuchat.app.ui.theme.SurfaceCard
import com.ashuchat.app.ui.theme.SurfaceElevated
import com.ashuchat.app.ui.theme.TextMuted
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.ui.theme.TextSecondary
import com.ashuchat.app.ui.viewmodel.ChatViewModel
import com.ashuchat.app.utils.TimeUtils
import com.ashuchat.app.webrtc.P2PConnectionState

import com.ashuchat.app.ui.components.SettingsMenuButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreenClassic(
    viewModel: ChatViewModel,
    onRoomClick: (roomId: String) -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToRelay: () -> Unit,
    onNavigateToChatSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val activeRoomId by viewModel.activeRoomId.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    val updateInfo by viewModel.updateInfo.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var roomToDelete by remember { mutableStateOf<RoomEntity?>(null) }
    var roomToRename by remember { mutableStateOf<RoomEntity?>(null) }

    val filteredRooms = remember(rooms, searchQuery) {
        if (searchQuery.isBlank()) {
            rooms
        } else {
            rooms.filter {
                it.roomId.contains(searchQuery.trim(), ignoreCase = true) ||
                it.partnerName.contains(searchQuery.trim(), ignoreCase = true) ||
                it.lastMessage.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = MidnightBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceElevated
                ),
                title = {
                    Column {
                        Text(
                            text = "AshuChat",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Private direct chats",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary
                        )
                    }
                    SettingsMenuButton(
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToRelay = onNavigateToRelay,
                        onNavigateToChat = onNavigateToChatSettings,
                        onNavigateToAbout = onNavigateToAbout
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToPairing,
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(BrandPrimary, BrandDark)),
                        CircleShape
                    )
                    .border(1.dp, SurfaceBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "New Room",
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Bar
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by code or name...", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandLight,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = BrandLight
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Update Available Banner
            AnimatedVisibility(
                visible = updateInfo != null && updateInfo!!.isUpdateAvailable,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                updateInfo?.let { info ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(listOf(BrandPrimary, BrandDark))
                            )
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = BrandLight,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Update Available: ${info.latestVersionName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Tap to update AshuChat",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { viewModel.startAppUpdate(info.downloadUrl) },
                                enabled = !isDownloadingUpdate,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Update", fontSize = 11.sp, color = TextPrimary)
                            }

                            IconButton(
                                onClick = { viewModel.dismissUpdateDialog() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (filteredRooms.isEmpty()) {
                EmptyRoomsView(
                    isSearch = searchQuery.isNotBlank(),
                    onActionClick = onNavigateToPairing
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredRooms,
                        key = { it.roomId }
                    ) { room ->
                        RoomItemRow(
                            room = room,
                            isActive = room.roomId == activeRoomId && connectionState == P2PConnectionState.CONNECTED_P2P,
                            onClick = {
                                onRoomClick(room.roomId)
                            },
                            onRenameClick = {
                                roomToRename = room
                            },
                            onDeleteClick = {
                                roomToDelete = room
                            }
                        )
                        HorizontalDivider(
                            color = SurfaceBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 76.dp)
                        )
                    }
                }
            }
        }
    }

    // Rename Peer Dialog
    roomToRename?.let { room ->
        var newPeerName by remember(room) {
            mutableStateOf(if (room.partnerName.isNotBlank() && room.partnerName != "Peer") room.partnerName else "")
        }

        AlertDialog(
            onDismissRequest = { roomToRename = null },
            title = {
                Text(
                    text = "Rename Peer",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a name or nickname for your peer in room \"${room.roomId}\":",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPeerName,
                        onValueChange = { newPeerName = it },
                        singleLine = true,
                        placeholder = {
                            Text("e.g. Ashu", color = TextMuted)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandLight,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = BrandLight
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newPeerName.trim().ifEmpty { "Peer" }
                        viewModel.updateRoomPartnerName(room.roomId, clean)
                        roomToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToRename = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // Delete Room Confirmation Dialog
    roomToDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            title = {
                Text(
                    text = "Delete Chat Room?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete room \"${room.roomId}\"? All messages in this room will be permanently removed.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoom(room.roomId)
                        roomToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPrimary
                    )
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // What's New Dialog on first launch after an app upgrade
    val whatsNewDialogInfo by viewModel.whatsNewDialogInfo.collectAsState()
    if (whatsNewDialogInfo != null) {
        UpdateDialog(
            updateInfo = whatsNewDialogInfo!!,
            downloadProgress = null,
            isDownloading = false,
            onConfirmUpdate = { viewModel.dismissWhatsNewDialog() },
            onDismiss = { viewModel.dismissWhatsNewDialog() }
        )
    }

    // In-App Update Dialog when downloading or confirmed
    if (updateInfo != null && updateInfo!!.isUpdateAvailable && isDownloadingUpdate) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            downloadProgress = downloadProgress,
            isDownloading = isDownloadingUpdate,
            onConfirmUpdate = {
                viewModel.startAppUpdate(updateInfo!!.downloadUrl)
            },
            onDismiss = {
                viewModel.dismissUpdateDialog()
            }
        )
    }
}

@Composable
fun RoomItemRow(
    room: RoomEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val displayTitle = if (room.partnerName.isNotBlank() && room.partnerName != "Peer") {
        room.partnerName
    } else {
        room.roomId
    }

    val initialLetter = displayTitle.take(1).uppercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with online badge
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(AccentTeal, AccentIndigo)
                        )
                    )
            ) {
                Text(
                    text = initialLetter,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                        .border(2.dp, MidnightBg, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Center Content: Room title, room code tag, and message snippet
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = displayTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Room code badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = room.roomId,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = BrandLight
                        )
                    }
                }

                // Timestamp
                val timeStr = TimeUtils.formatRoomTimestamp(room.lastTimestamp)
                if (timeStr.isNotBlank()) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Last message snippet
            Text(
                text = if (room.lastMessage.isBlank()) "Tap to chat" else room.lastMessage,
                fontSize = 13.sp,
                color = if (room.lastMessage.isBlank()) TextMuted else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Options dropdown menu button
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceElevated)
            ) {
                DropdownMenuItem(
                    text = { Text("Rename Peer", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onRenameClick()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = BrandLight
                        )
                    }
                )

                DropdownMenuItem(
                    text = { Text("Delete Room", color = Color(0xFFEF4444)) },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyRoomsView(
    isSearch: Boolean,
    onActionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(1.dp, SurfaceBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = BrandLight,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isSearch) "No Matching Rooms" else "No Chats Yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSearch) {
                    "No room found matching your search term."
                } else {
                    "Create a room code or enter your partner's code to start a private, end-to-end direct chat."
                },
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            if (!isSearch) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .background(
                            Brush.horizontalGradient(listOf(BrandPrimary, BrandDark)),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Join or Create Room",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
