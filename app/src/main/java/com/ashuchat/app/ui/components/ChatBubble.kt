package com.ashuchat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ashuchat.app.data.model.ChatMessage
import com.ashuchat.app.ui.theme.AppTheme
import com.ashuchat.app.ui.theme.LocalAppTheme
import com.ashuchat.app.utils.AudioPlayerHelper

@Composable
fun ChatBubble(
    message: ChatMessage,
    onImageClick: (filePath: String, fileName: String?) -> Unit,
    onRetryClick: ((ChatMessage) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onReactionClick: ((messageId: String, emoji: String) -> Unit)? = null,
    audioPlayerHelper: AudioPlayerHelper? = null,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    if (theme == AppTheme.PAPER) {
        ChatBubblePaper(
            message = message,
            onImageClick = onImageClick,
            onRetryClick = onRetryClick,
            onReplyClick = onReplyClick,
            onReactionClick = onReactionClick,
            audioPlayerHelper = audioPlayerHelper,
            modifier = modifier
        )
    } else {
        ChatBubbleClassic(
            message = message,
            onImageClick = onImageClick,
            onRetryClick = onRetryClick,
            onReplyClick = onReplyClick,
            onReactionClick = onReactionClick,
            audioPlayerHelper = audioPlayerHelper,
            modifier = modifier
        )
    }
}
