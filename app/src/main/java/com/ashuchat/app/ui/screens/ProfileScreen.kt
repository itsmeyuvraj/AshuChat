package com.ashuchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.components.NeoBrutalCard
import com.ashuchat.app.ui.components.NeoTopAppBar
import com.ashuchat.app.ui.theme.NeoAccentYellow
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.NeoSurfaceElevated
import com.ashuchat.app.ui.theme.NeoTextMuted
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.ui.theme.NeoTextSecondary
import com.ashuchat.app.ui.viewmodel.ChatViewModel

@Composable
fun ProfileScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val prefs = viewModel.prefs
    var myName by remember { mutableStateOf(prefs.myName) }

    Scaffold(
        containerColor = NeoBackground,
        topBar = {
            NeoTopAppBar(
                title = "Profile",
                subtitle = "Identity & Display Handle",
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(NeoBackground)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar Preview Card
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(NeoCardYellow)
                    .border(2.5.dp, NeoCardBorder, RoundedCornerShape(26.dp))
            ) {
                Text(
                    text = myName.take(1).uppercase().ifEmpty { "Y" },
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = NeoAccentYellow
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Name Field Card (Dark Tinted NeoBrutalCard)
            NeoBrutalCard(
                backgroundColor = NeoCardMint,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🏷️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Display Name",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This is the name your partner or peer sees in all chat rooms.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeoTextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = myName,
                        onValueChange = {
                            myName = it
                            viewModel.updateMyName(it)
                        },
                        textStyle = TextStyle(
                            color = NeoTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        placeholder = {
                            Text(
                                "Enter your name",
                                color = NeoTextMuted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeoAccentYellow,
                            unfocusedBorderColor = NeoCardBorder,
                            focusedContainerColor = NeoSurfaceElevated,
                            unfocusedContainerColor = NeoSurfaceElevated,
                            focusedTextColor = NeoTextPrimary,
                            unfocusedTextColor = NeoTextPrimary,
                            focusedPlaceholderColor = NeoTextMuted,
                            unfocusedPlaceholderColor = NeoTextMuted,
                            cursorColor = NeoAccentYellow
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, NeoCardBorder, RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}
