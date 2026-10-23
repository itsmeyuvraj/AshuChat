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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.data.local.RoomEntity
import com.ashuchat.app.ui.components.NeoBrutalCard
import com.ashuchat.app.ui.components.NeoBrutalIconButton
import com.ashuchat.app.ui.components.NeoChatBubbleIcon
import com.ashuchat.app.ui.components.NeoEmojiAvatar
import com.ashuchat.app.ui.components.NeoPillBadge
import com.ashuchat.app.ui.components.NeoUnreadBadge
import com.ashuchat.app.ui.components.SettingsMenuButton
import com.ashuchat.app.ui.components.UpdateDialog
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBlack
import com.ashuchat.app.ui.theme.NeoBtnBlue
import com.ashuchat.app.ui.theme.NeoBtnMint
import com.ashuchat.app.ui.theme.NeoBtnPeach
import com.ashuchat.app.ui.theme.NeoCardLavender
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoCardPeach
import com.ashuchat.app.ui.theme.NeoCardPink
import com.ashuchat.app.ui.theme.NeoCardSky
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoHeaderYellow
import com.ashuchat.app.ui.theme.NeoTextDark
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.theme.OnlineGreen
import com.ashuchat.app.ui.viewmodel.ChatViewModel
import com.ashuchat.app.utils.TimeUtils
import com.ashuchat.app.webrtc.P2PConnectionState

// Card color rotating cycle matching the reference image aesthetic
private val NeoCardColorPalette = listOf(
    NeoCardYellow,   // Card 0: Sunshine Yellow
    NeoCardMint,     // Card 1: Seafoam Mint Green
    NeoCardLavender, // Card 2: Soft Lilac / Lavender
    NeoCardPeach,    // Card 3: Peach / Tangerine
    NeoCardPink,     // Card 4: Bubblegum Pink
    NeoCardSky       // Card 5: Sky Blue
)

/**
 * Deterministic fun emoji picker based on room ID / partner name
 */
private fun getRoomAvatarEmoji(roomId: String, partnerName: String): String {
    val key = (roomId + partnerName).lowercase()
    return when {
        key.contains("586") || key.contains("bot") -> "🤖"
        key.contains("604") || key.contains("rocket") -> "🚀"
        key.contains("450") || key.contains("flash") -> "⚡"
        key.contains("doodle") || key.contains("art") || key.contains("squad") -> "🎨"
        else -> {
            val emojis = listOf(
                "🤖", "🚀", "⚡", "🎨", "🎮", "👾", "🦊", "🐱",
                "🍕", "🍩", "🔮", "🛸", "🔥", "🌈", "🎸", "🏄‍♂️"
            )
            val hash = Math.abs(key.hashCode())
            emojis[hash % emojis.size]
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreenPaper(
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
        containerColor = NeoBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeoHeaderYellow)
            ) {
                // System Status Bar spacing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                )

                // Main Sunshine Yellow Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.5.dp,
                            color = NeoBlack
                        )
                        .background(NeoHeaderYellow)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Brand: 🎨 AshuChat & Subtitle
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎨",
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AshuChat",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black,
                                color = NeoBlack
                            )
                        }

                        Text(
                            text = "PRIVATE DIRECT CHATS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = NeoBlack.copy(alpha = 0.9f)
                        )
                    }

                    // Right Actions: Search & Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Search Button
                        NeoBrutalIconButton(
                            onClick = { isSearchActive = !isSearchActive },
                            backgroundColor = NeoBtnBlue,
                            size = 42.dp
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF2B7CD3),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Settings Menu Button with custom Peach Neo-Brutalist trigger
                        SettingsMenuButton(
                            onNavigateToProfile = onNavigateToProfile,
                            onNavigateToRelay = onNavigateToRelay,
                            onNavigateToChat = onNavigateToChatSettings,
                            onNavigateToAbout = onNavigateToAbout,
                            trigger = { openMenu ->
                                NeoBrutalIconButton(
                                    onClick = openMenu,
                                    backgroundColor = NeoBtnPeach,
                                    size = 42.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = Color(0xFF4A90E2),
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(NeoBackground)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Search Input Bar (Neo-brutalist styled)
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        NeoBrutalCard(
                            backgroundColor = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    color = NeoBlack,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                placeholder = {
                                    Text(
                                        "Search by code or name...",
                                        color = NeoBlack.copy(alpha = 0.55f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedTextColor = NeoBlack,
                                    unfocusedTextColor = NeoBlack,
                                    cursorColor = NeoBlack
                                ),
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = NeoBlack
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // In-App Update Banner (Neo-brutalist styled)
                AnimatedVisibility(
                    visible = updateInfo != null && updateInfo!!.isUpdateAvailable,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    updateInfo?.let { info ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            NeoBrutalCard(
                                backgroundColor = NeoCardLavender,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White)
                                                .border(2.dp, NeoBlack, RoundedCornerShape(10.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = NeoBlack,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Update Available: ${info.latestVersionName}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeoBlack
                                            )
                                            Text(
                                                text = "Tap to update AshuChat",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = NeoTextMuted
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        NeoBrutalIconButton(
                                            onClick = { viewModel.startAppUpdate(info.downloadUrl) },
                                            backgroundColor = NeoBtnMint,
                                            size = 36.dp
                                        ) {
                                            Text(
                                                text = "Update",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = NeoBlack
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.dismissUpdateDialog() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = NeoBlack,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                // Rooms List or Empty State
                if (filteredRooms.isEmpty()) {
                    EmptyNeoRoomsView(
                        isSearch = searchQuery.isNotBlank(),
                        onActionClick = onNavigateToPairing
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 14.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = filteredRooms,
                            key = { _, room -> room.roomId }
                        ) { index, room ->
                            val cardColor = NeoCardColorPalette[index % NeoCardColorPalette.size]

                            NeoRoomCardItem(
                                room = room,
                                backgroundColor = cardColor,
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
                        }
                    }
                }
            }

            // Bottom-Right Yellow Neo FAB with Speech Bubble Icon
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
            ) {
                NeoBrutalIconButton(
                    onClick = onNavigateToPairing,
                    backgroundColor = NeoHeaderYellow,
                    size = 64.dp,
                    borderColor = NeoBlack,
                    borderWidth = 2.5.dp,
                    shadowOffset = 4.5.dp,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    NeoChatBubbleIcon(size = 34.dp)
                }
            }
        }
    }

    // Delete Room Confirmation Dialog (Neo-brutalist style)
    roomToDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            title = {
                Text(
                    text = "Delete Chat Room?",
                    color = NeoBlack,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete room \"${room.roomId}\"? All messages in this room will be permanently removed.",
                    color = NeoTextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoom(room.roomId)
                        roomToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(2.dp, NeoBlack, RoundedCornerShape(10.dp))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToDelete = null }) {
                    Text("Cancel", color = NeoBlack, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = NeoBackground,
            modifier = Modifier.border(2.5.dp, NeoBlack, RoundedCornerShape(20.dp))
        )
    }

    // Rename Peer Dialog (Neo-brutalist style)
    roomToRename?.let { room ->
        var newPeerName by remember(room) {
            mutableStateOf(if (room.partnerName.isNotBlank() && room.partnerName != "Peer") room.partnerName else "")
        }

        AlertDialog(
            onDismissRequest = { roomToRename = null },
            title = {
                Text(
                    text = "Rename Peer",
                    color = NeoBlack,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a name or nickname for your peer in room \"${room.roomId}\":",
                        color = NeoTextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPeerName,
                        onValueChange = { newPeerName = it },
                        singleLine = true,
                        placeholder = {
                            Text("e.g. Ashu", color = NeoBlack.copy(alpha = 0.5f))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeoBlack,
                            unfocusedBorderColor = NeoBlack,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = NeoBlack,
                            unfocusedTextColor = NeoBlack,
                            cursorColor = NeoBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, NeoBlack, RoundedCornerShape(10.dp))
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
                        containerColor = NeoBtnMint
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(2.dp, NeoBlack, RoundedCornerShape(10.dp))
                ) {
                    Text("Save", color = NeoBlack, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToRename = null }) {
                    Text("Cancel", color = NeoBlack, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = NeoBackground,
            modifier = Modifier.border(2.5.dp, NeoBlack, RoundedCornerShape(20.dp))
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

/**
 * Individual Neo-Brutalist Room Item Card
 */
@Composable
private fun NeoRoomCardItem(
    room: RoomEntity,
    backgroundColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val peerDisplayName = if (room.partnerName.isNotBlank() && room.partnerName != "Peer") {
        room.partnerName
    } else {
        "Peer"
    }

    val emoji = remember(room.roomId, room.partnerName) {
        getRoomAvatarEmoji(room.roomId, room.partnerName)
    }

    val lastMessageSnippet = if (room.lastMessage.isBlank()) {
        "Room created successfully! 🎉"
    } else {
        room.lastMessage
    }

    val timeStr = TimeUtils.formatRoomTimestamp(room.lastTimestamp)

    NeoBrutalCard(
        backgroundColor = backgroundColor,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle Avatar with Emoji & Online Badge
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                NeoEmojiAvatar(
                    emoji = emoji,
                    size = 52.dp,
                    shape = RoundedCornerShape(16.dp)
                )

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                            .border(2.dp, NeoBlack, CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center Content: Room title, room code badge, last message
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = peerDisplayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = NeoTextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Neo Dark Pill Badge for Room Code
                    NeoPillBadge(text = room.roomId)
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Last message snippet
                Text(
                    text = lastMessageSnippet,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = NeoBlack.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Column: Timestamp & Unread Badge / More Menu
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (timeStr.isNotBlank()) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoBlack.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (room.unreadCount > 0) {
                    NeoUnreadBadge(count = room.unreadCount)
                } else {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = NeoBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(NeoBackground)
                                .border(2.dp, NeoBlack, RoundedCornerShape(16.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Rename Peer",
                                        color = NeoBlack,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onRenameClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = NeoBlack
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete Room",
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold
                                    )
                                },
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
        }
    }
}

/**
 * Playful Neo-Brutalist Empty State View
 */
@Composable
private fun EmptyNeoRoomsView(
    isSearch: Boolean,
    onActionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        NeoBrutalCard(
            backgroundColor = NeoCardYellow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(2.5.dp, NeoBlack, RoundedCornerShape(20.dp))
                ) {
                    Text(
                        text = if (isSearch) "🔍" else "💬",
                        fontSize = 38.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (isSearch) "No Matching Rooms" else "No Chats Yet!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = NeoBlack
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSearch) {
                        "No room found matching your search term."
                    } else {
                        "Create a room code or enter your partner's code to start a private, end-to-end direct chat."
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = NeoBlack.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                if (!isSearch) {
                    Spacer(modifier = Modifier.height(20.dp))

                    NeoBrutalIconButton(
                        onClick = onActionClick,
                        backgroundColor = NeoBtnMint,
                        size = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = NeoBlack,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Join or Create Room",
                                color = NeoBlack,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
