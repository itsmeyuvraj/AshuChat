package com.ashuchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoSurfaceElevated
import com.ashuchat.app.ui.theme.NeoTextPrimary
import com.ashuchat.app.ui.theme.NeoTextSecondary

@Composable
fun SettingsMenuButton(
    onNavigateToProfile: () -> Unit,
    onNavigateToRelay: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    iconTint: Color = NeoTextSecondary,
    trigger: (@Composable (onClick: () -> Unit) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (trigger != null) {
            trigger { expanded = true }
        } else {
            IconButton(
                onClick = { expanded = true },
                modifier = iconModifier
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = iconTint
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(NeoSurfaceElevated)
                .border(2.dp, NeoCardBorder, RoundedCornerShape(16.dp))
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Profile",
                        color = NeoTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = NeoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToProfile()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Relay",
                        color = NeoTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Relay",
                        tint = NeoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToRelay()
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Chat Settings",
                        color = NeoTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Chat",
                        tint = NeoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToChat()
                }
            )

            HorizontalDivider(
                color = NeoCardBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "About",
                        color = NeoTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About",
                        tint = NeoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToAbout()
                }
            )
        }
    }
}
