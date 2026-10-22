package com.ashuchat.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.NeoBadgeRed
import com.ashuchat.app.ui.theme.NeoBlack
import com.ashuchat.app.ui.theme.NeoCardBorder
import com.ashuchat.app.ui.theme.NeoCardShadow
import com.ashuchat.app.ui.theme.NeoHeaderYellow
import com.ashuchat.app.ui.theme.NeoPillDark
import com.ashuchat.app.utils.HapticHelper

/**
 * Neo-Brutalist card with solid hard offset shadow and crisp black border.
 * Features physical press-down animation and haptic vibration when clicked.
 */
@Composable
fun NeoBrutalCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    borderColor: Color = NeoCardBorder,
    borderWidth: Dp = 2.5.dp,
    shadowColor: Color = NeoCardShadow,
    shadowOffset: Dp = 4.dp,
    shadowOffsetX: Dp = shadowOffset,
    shadowOffsetY: Dp = shadowOffset,
    cornerRadius: Dp = 20.dp,
    shape: RoundedCornerShape = RoundedCornerShape(cornerRadius),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffsetX by animateDpAsState(
        targetValue = if (isPressed && onClick != null) (shadowOffsetX - 1.dp) else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "neoCardPressOffsetX"
    )
    val currentOffsetY by animateDpAsState(
        targetValue = if (isPressed && onClick != null) (shadowOffsetY - 1.dp) else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "neoCardPressOffsetY"
    )

    Box(
        modifier = modifier.padding(end = shadowOffsetX, bottom = shadowOffsetY)
    ) {
        // Hard drop shadow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffsetX, y = shadowOffsetY)
                .background(shadowColor, shape)
        )

        // Foreground Card layer (shifts down/right on press)
        Box(
            modifier = Modifier
                .offset(x = currentOffsetX, y = currentOffsetY)
                .background(backgroundColor, shape)
                .border(borderWidth, borderColor, shape)
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                HapticHelper.playClick(context, view)
                                onClick()
                            }
                        )
                    } else Modifier
                ),
            content = content
        )
    }
}

/**
 * Neo-Brutalist icon button with physical press animation and haptic vibration.
 */
@Composable
fun NeoBrutalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    size: Dp = 44.dp,
    borderColor: Color = NeoBlack,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 3.5.dp,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed) (shadowOffset - 1.dp) else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btnOffset"
    )

    Box(
        modifier = modifier
            .padding(end = shadowOffset, bottom = shadowOffset)
            .size(size)
    ) {
        // Hard Shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(NeoBlack, shape)
        )

        // Button Surface (shifts down on press)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .offset(x = currentOffset, y = currentOffset)
                .background(backgroundColor, shape)
                .border(borderWidth, borderColor, shape)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        HapticHelper.playClick(context, view)
                        onClick()
                    }
                ),
            content = content
        )
    }
}

/**
 * Overload of NeoBrutalIconButton accepting an icon directly.
 */
@Composable
fun NeoBrutalIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = NeoBlack,
    borderColor: Color = NeoBlack,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 3.5.dp,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    onClick: () -> Unit
) {
    NeoBrutalIconButton(
        onClick = onClick,
        modifier = modifier,
        backgroundColor = backgroundColor,
        size = size,
        borderColor = borderColor,
        borderWidth = borderWidth,
        shadowOffset = shadowOffset,
        shape = shape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Standard Neo-Brutalist Button with text and icon, press animation, and haptic feedback.
 */
@Composable
fun NeoBrutalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    borderColor: Color = NeoBlack,
    borderWidth: Dp = 2.5.dp,
    shadowOffset: Dp = 4.dp,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) (shadowOffset - 1.dp) else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "neoButtonOffset"
    )

    Box(
        modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        // Shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(if (enabled) NeoBlack else NeoBlack.copy(alpha = 0.3f), shape)
        )

        // Surface
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = currentOffset, y = currentOffset)
                .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f), shape)
                .border(borderWidth, NeoBlack, shape)
                .clip(shape)
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                HapticHelper.playClick(context, view)
                                onClick()
                            }
                        )
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            content = content
        )
    }
}

/**
 * Overload of NeoBrutalButton accepting text and optional icon directly.
 */
@Composable
fun NeoBrutalButton(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    textColor: Color = NeoBlack,
    borderColor: Color = NeoBlack,
    borderWidth: Dp = 2.5.dp,
    shadowOffset: Dp = 4.dp,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    NeoBrutalButton(
        onClick = onClick,
        modifier = modifier,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        borderWidth = borderWidth,
        shadowOffset = shadowOffset,
        shape = shape,
        enabled = enabled
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            color = textColor
        )
    }
}

/**
 * Universal Neo-Brutalist Top App Bar with sunshine yellow background,
 * solid black border bottom, and optional back navigation button.
 */
@Composable
fun NeoTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NeoHeaderYellow)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 2.5.dp, color = NeoBlack)
                .background(NeoHeaderYellow)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (onNavigateBack != null) {
                    NeoBrutalIconButton(
                        onClick = onNavigateBack,
                        backgroundColor = Color.White,
                        size = 38.dp
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeoBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = NeoBlack
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = NeoBlack.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
        }
    }
}

/**
 * Neo Pill badge with customizable colors and border.
 */
@Composable
fun NeoPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoPillDark,
    textColor: Color = if (backgroundColor == NeoPillDark) Color.White else NeoBlack,
    borderWidth: Dp = if (backgroundColor == NeoPillDark) 0.dp else 1.5.dp,
    shadowOffsetX: Dp = 0.dp,
    shadowOffsetY: Dp = 0.dp
) {
    Box(
        modifier = modifier.then(
            if (shadowOffsetX > 0.dp || shadowOffsetY > 0.dp) {
                Modifier.padding(end = shadowOffsetX, bottom = shadowOffsetY)
            } else Modifier
        )
    ) {
        if (shadowOffsetX > 0.dp || shadowOffsetY > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = shadowOffsetX, y = shadowOffsetY)
                    .background(NeoBlack, RoundedCornerShape(8.dp))
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .then(
                    if (borderWidth > 0.dp) Modifier.border(borderWidth, NeoBlack, RoundedCornerShape(8.dp)) else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = textColor
            )
        }
    }
}

/**
 * Avatar with white background, black border and centered emoji or initial.
 */
@Composable
fun NeoEmojiAvatar(
    name: String = "",
    emoji: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp)
) {
    val emojiList = listOf("🐻", "🦊", "🐼", "🐨", "🦁", "🐯", "🐶", "🐱", "🐰", "🐸", "🐵", "🦄", "🐙", "🐧", "🦉")
    val avatarContent = if (emoji.isNotBlank()) {
        emoji
    } else if (name.isNotBlank()) {
        val hash = kotlin.math.abs(name.hashCode())
        emojiList[hash % emojiList.size]
    } else {
        "👻"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(Color.White, shape)
            .border(2.dp, NeoBlack, shape)
            .clip(shape)
    ) {
        Text(
            text = avatarContent,
            fontSize = (size.value * 0.5f).sp
        )
    }
}

/**
 * Neo Unread circular badge with red fill, black border and white number.
 */
@Composable
fun NeoUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(NeoBadgeRed)
            .border(2.dp, NeoBlack, CircleShape)
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * Neo-brutalist comic speech bubble icon with tail, white fill, black border and 3 dots.
 */
@Composable
fun NeoChatBubbleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val r = 9.dp.toPx()
        val bottomEdge = h - 6.dp.toPx()

        val bubblePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(r, 2.dp.toPx())
            lineTo(w - r, 2.dp.toPx())
            quadraticBezierTo(w, 2.dp.toPx(), w, 2.dp.toPx() + r)
            lineTo(w, bottomEdge - r)
            quadraticBezierTo(w, bottomEdge, w - r, bottomEdge)
            lineTo(14.dp.toPx(), bottomEdge)
            lineTo(7.dp.toPx(), h - 1.dp.toPx())
            lineTo(10.dp.toPx(), bottomEdge)
            lineTo(r, bottomEdge)
            quadraticBezierTo(2.dp.toPx(), bottomEdge, 2.dp.toPx(), bottomEdge - r)
            lineTo(2.dp.toPx(), 2.dp.toPx() + r)
            quadraticBezierTo(2.dp.toPx(), 2.dp.toPx(), r, 2.dp.toPx())
            close()
        }

        drawPath(path = bubblePath, color = Color.White)
        drawPath(
            path = bubblePath,
            color = NeoBlack,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.5.dp.toPx(),
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )

        val dotRadius = 1.8.dp.toPx()
        val dotY = bottomEdge / 2f + 1.dp.toPx()
        val cx = w / 2f + 1.dp.toPx()
        val spacing = 5.dp.toPx()

        drawCircle(color = NeoBlack, radius = dotRadius, center = androidx.compose.ui.geometry.Offset(cx - spacing, dotY))
        drawCircle(color = NeoBlack, radius = dotRadius, center = androidx.compose.ui.geometry.Offset(cx, dotY))
        drawCircle(color = NeoBlack, radius = dotRadius, center = androidx.compose.ui.geometry.Offset(cx + spacing, dotY))
    }
}
