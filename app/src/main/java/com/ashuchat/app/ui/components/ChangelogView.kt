package com.ashuchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashuchat.app.ui.theme.NeoBackground
import com.ashuchat.app.ui.theme.NeoBlack
import com.ashuchat.app.ui.theme.NeoCardLavender
import com.ashuchat.app.ui.theme.NeoCardMint
import com.ashuchat.app.ui.theme.NeoCardPeach
import com.ashuchat.app.ui.theme.NeoCardPink
import com.ashuchat.app.ui.theme.NeoCardSky
import com.ashuchat.app.ui.theme.NeoCardYellow
import com.ashuchat.app.ui.theme.SurfaceBorder
import com.ashuchat.app.ui.theme.SurfaceCard
import com.ashuchat.app.ui.theme.TextPrimary
import com.ashuchat.app.ui.theme.TextSecondary

enum class ChangelogCategory(
    val label: String,
    val iconEmoji: String,
    val classicBg: Color,
    val classicText: Color,
    val paperBg: Color
) {
    FEATURE(
        label = "FEATURE",
        iconEmoji = "✨",
        classicBg = Color(0xFF064E3B),
        classicText = Color(0xFF34D399),
        paperBg = NeoCardMint
    ),
    IMPROVEMENT(
        label = "IMPROVED",
        iconEmoji = "⚡",
        classicBg = Color(0xFF1E293B),
        classicText = Color(0xFF93C5FD),
        paperBg = NeoCardSky
    ),
    FIX(
        label = "FIX",
        iconEmoji = "🛠️",
        classicBg = Color(0xFF4C0519),
        classicText = Color(0xFFFB7185),
        paperBg = NeoCardPink
    ),
    SECURITY(
        label = "SECURITY",
        iconEmoji = "🔒",
        classicBg = Color(0xFF312E81),
        classicText = Color(0xFFA5B4FC),
        paperBg = NeoCardLavender
    ),
    UI(
        label = "DESIGN",
        iconEmoji = "🎨",
        classicBg = Color(0xFF4A1D96),
        classicText = Color(0xFFC084FC),
        paperBg = NeoCardYellow
    ),
    UPDATE(
        label = "UPDATE",
        iconEmoji = "📌",
        classicBg = Color(0xFF1F2937),
        classicText = Color(0xFFCBD5E1),
        paperBg = NeoCardPeach
    )
}

data class ChangelogItem(
    val category: ChangelogCategory,
    val title: String
)

object ChangelogParser {

    private val GITHUB_LINK_REGEX = Regex("""(?i)\s+(by\s+@[\w-]+|\(#\d+\)|in\s+https?://\S+|https?://\S+)""")
    private val MARKDOWN_HEADER_REGEX = Regex("""^#{1,6}\s+""")
    private val BULLET_PREFIX_REGEX = Regex("""^(\s*[-*•+]\s+|\s*\d+[.)]\s+)""")

    fun parse(rawNotes: String): List<ChangelogItem> {
        if (rawNotes.isBlank()) return emptyList()

        val lines = rawNotes.lines()
        val items = mutableListOf<ChangelogItem>()

        for (line in lines) {
            var trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val lower = trimmed.lowercase()
            if (lower.startsWith("## what's changed") ||
                lower.startsWith("### changes") ||
                lower.startsWith("### what's new") ||
                lower.startsWith("whats new") ||
                lower.contains("full changelog") ||
                lower.contains("compare/v")
            ) {
                continue
            }

            trimmed = trimmed.replace(MARKDOWN_HEADER_REGEX, "").trim()
            trimmed = trimmed.replace(BULLET_PREFIX_REGEX, "").trim()
            if (trimmed.isBlank()) continue

            trimmed = trimmed.replace(GITHUB_LINK_REGEX, "").trim()
            trimmed = trimmed.replace("**", "").replace("__", "").replace("`", "").trim()

            if (trimmed.length < 3) continue

            val (category, cleanedTitle) = extractCategoryAndCleanText(trimmed)
            if (cleanedTitle.isNotBlank()) {
                items.add(ChangelogItem(category = category, title = cleanedTitle))
            }
        }

        if (items.isEmpty() && rawNotes.isNotBlank()) {
            val fallbackClean = rawNotes
                .replace(GITHUB_LINK_REGEX, "")
                .replace(MARKDOWN_HEADER_REGEX, "")
                .replace("**", "")
                .trim()
            if (fallbackClean.isNotBlank()) {
                val sentences = fallbackClean.split(". ")
                for (s in sentences) {
                    val st = s.trim().removeSuffix(".")
                    if (st.isNotBlank()) {
                        val (cat, title) = extractCategoryAndCleanText(st)
                        items.add(ChangelogItem(category = cat, title = title))
                    }
                }
            }
        }

        return items
    }

    private fun extractCategoryAndCleanText(text: String): Pair<ChangelogCategory, String> {
        val lower = text.lowercase()
        val category: ChangelogCategory
        var content = text

        when {
            lower.startsWith("feat:") || lower.startsWith("feat(") || lower.startsWith("feature:") || lower.startsWith("new:") -> {
                category = ChangelogCategory.FEATURE
                content = text.substringAfter(":", text)
            }
            lower.startsWith("fix:") || lower.startsWith("fix(") || lower.startsWith("bug:") || lower.startsWith("bugfix:") -> {
                category = ChangelogCategory.FIX
                content = text.substringAfter(":", text)
            }
            lower.startsWith("perf:") || lower.startsWith("perf(") || lower.startsWith("optimize:") || lower.startsWith("optimization:") -> {
                category = ChangelogCategory.IMPROVEMENT
                content = text.substringAfter(":", text)
            }
            lower.startsWith("sec:") || lower.startsWith("security:") -> {
                category = ChangelogCategory.SECURITY
                content = text.substringAfter(":", text)
            }
            lower.startsWith("ui:") || lower.startsWith("design:") || lower.startsWith("style:") -> {
                category = ChangelogCategory.UI
                content = text.substringAfter(":", text)
            }
            lower.startsWith("refactor:") || lower.startsWith("clean:") || lower.startsWith("chore:") || lower.startsWith("build:") -> {
                category = ChangelogCategory.IMPROVEMENT
                content = text.substringAfter(":", text)
            }
            lower.contains("fix") || lower.contains("resolved") || lower.contains("crash") || lower.contains("bug") -> {
                category = ChangelogCategory.FIX
            }
            lower.contains("optim") || lower.contains("speed") || lower.contains("faster") || lower.contains("cache") || lower.contains("robust") -> {
                category = ChangelogCategory.IMPROVEMENT
            }
            lower.contains("encrypt") || lower.contains("secur") || lower.contains("privacy") -> {
                category = ChangelogCategory.SECURITY
            }
            lower.contains("call") || lower.contains("added") || lower.contains("new") || lower.contains("support") -> {
                category = ChangelogCategory.FEATURE
            }
            else -> {
                category = ChangelogCategory.UPDATE
            }
        }

        content = content.trim().removePrefix(":").trim()
        content = content.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        return Pair(category, content)
    }
}

@Composable
fun ChangelogView(
    rawNotes: String,
    isPaper: Boolean,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 220.dp
) {
    val items = remember(rawNotes) { ChangelogParser.parse(rawNotes) }

    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isPaper) NeoBackground else SurfaceCard)
                .border(
                    if (isPaper) 1.5.dp else 1.dp,
                    if (isPaper) NeoBlack else SurfaceBorder,
                    RoundedCornerShape(14.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                text = rawNotes.ifBlank { "General bug fixes and performance improvements." },
                fontSize = 12.sp,
                fontWeight = if (isPaper) FontWeight.Medium else FontWeight.Normal,
                color = if (isPaper) NeoBlack else TextSecondary,
                lineHeight = 17.sp
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPaper) NeoBackground else SurfaceCard.copy(alpha = 0.7f))
            .border(
                if (isPaper) 2.dp else 1.dp,
                if (isPaper) NeoBlack else SurfaceBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEach { item ->
                ChangelogItemRow(item = item, isPaper = isPaper)
            }
        }
    }
}

@Composable
private fun ChangelogItemRow(
    item: ChangelogItem,
    isPaper: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (isPaper) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(item.category.paperBg)
                    .border(1.5.dp, NeoBlack, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.category.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = NeoBlack,
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(item.category.classicBg)
                    .border(1.dp, item.category.classicText.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = item.category.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = item.category.classicText,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = item.title,
            fontSize = 12.5.sp,
            fontWeight = if (isPaper) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isPaper) NeoBlack else TextPrimary,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
