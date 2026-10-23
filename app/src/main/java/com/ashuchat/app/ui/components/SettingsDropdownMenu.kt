package com.ashuchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
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
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.BrandLight
import com.ashuchat.app.ui.theme.LocalAppTheme
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBlack
import com.ashuchat.app.ui.theme.SurfaceBorder
import com.ashuchat.app.ui.theme.SurfaceElevated
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.ui.theme.TextSecondary

@Composable
fun SettingsMenuButton(
    onNavigateToProfile: () -> Unit,
    onNavigateToRelay: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    iconTint: Color = TextSecondary,
    onToggleTheme: (() -> Unit)? = null,
    trigger: (@Composable (onClick: () -> Unit) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val currentTheme = LocalAppTheme.current
    val isPaper = currentTheme == AppTheme.PAPER

    val menuBg = if (isPaper) NeoBackground else SurfaceElevated
    val menuBorder = if (isPaper) 2.dp else 1.dp
    val menuBorderColor = if (isPaper) NeoBlack else SurfaceBorder
    val textColor = if (isPaper) NeoBlack else TextPrimary
    val iconColor = if (isPaper) NeoBlack else BrandLight
    val textWeight = if (isPaper) FontWeight.Bold else FontWeight.Medium
    val dividerColor = if (isPaper) NeoBlack.copy(alpha = 0.2f) else SurfaceBorder

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
                .background(menuBg)
                .border(menuBorder, menuBorderColor, RoundedCornerShape(16.dp))
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Profile",
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = textWeight
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = iconColor,
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
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = textWeight
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Relay",
                        tint = iconColor,
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
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = textWeight
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Chat",
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToChat()
                }
            )

            if (onToggleTheme != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isPaper) "Classic Dark Theme" else "Paper UI Theme",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = textWeight
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPaper) Icons.Default.DarkMode else Icons.Default.Palette,
                            contentDescription = "Toggle Theme",
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        expanded = false
                        onToggleTheme()
                    }
                )
            }

            HorizontalDivider(
                color = dividerColor,
                thickness = if (isPaper) 1.dp else 0.5.dp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "About",
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = textWeight
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About",
                        tint = iconColor,
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
