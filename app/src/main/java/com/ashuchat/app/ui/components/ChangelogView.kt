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
import androidx.compose.foundation.shape.CircleShape
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
import com.ashuchat.app.ui.theme.NeoSurface
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
    val title: String,
    val isSubBullet: Boolean = false
)

object ChangelogParser {

    private val GITHUB_LINK_REGEX = Regex("""(?i)\s+(by\s+@[\w-]+|\(#\d+\)|in\s+https?://\S+|https?://\S+)""")
    private val MARKDOWN_HEADER_REGEX = Regex("""^#{1,6}\s+""")
    private val BULLET_PREFIX_REGEX = Regex("""^(\s*[-*•+]\s+|\s*\d+[.)]\s+)""")
    private val CONVENTIONAL_COMMIT_PREFIX_REGEX = Regex("""(?i)^([a-z]+)(?:\([^)]+\))?:\s*""")

    fun parse(rawNotes: String): List<ChangelogItem> {
        if (rawNotes.isBlank()) return emptyList()

        // 1. Strip markdown header noise & commit hashes
        var text = rawNotes
            .replace(Regex("""(?i)\b(automated\s+ci/cd\s+build\s+for\s+commit|full\s+changelog:?)\b[^\n]*"""), "")
            .replace(Regex("""(?i)#{1,6}\s*(🚀\s*)?(what's\s*new|what's\s*changed|changelog)[^\n]*"""), "")
            .replace(Regex("""---[-]*"""), "")
            .replace(Regex("""`[0-9a-f]{7,40}`"""), "")
            .trim()

        // 2. Un-flatten single-line notes that have " - " or " • " separating bullets
        text = text.replace(Regex("""(?<=\S)\s{2,}[-*•]\s+"""), "\n- ")
            .replace(Regex("""(?<=\S)\s+[-*•]\s+"""), "\n- ")

        val lines = text.lines()
        val items = mutableListOf<ChangelogItem>()
        var currentSectionCategory: ChangelogCategory = ChangelogCategory.UPDATE
        var hasSeenHeader = false

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

            val isExplicitBullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
                    trimmed.startsWith("• ") || trimmed.startsWith("+ ") ||
                    Regex("""^\d+[.)]\s+""").containsMatchIn(trimmed)

            trimmed = trimmed.replace(MARKDOWN_HEADER_REGEX, "").trim()
            trimmed = trimmed.replace(BULLET_PREFIX_REGEX, "").trim()
            if (trimmed.isBlank()) continue

            trimmed = trimmed.replace(GITHUB_LINK_REGEX, "").trim()
            trimmed = trimmed.replace("**", "").replace("__", "").replace("`", "").trim()

            if (trimmed.length < 3) continue

            val match = CONVENTIONAL_COMMIT_PREFIX_REGEX.find(trimmed)
            if (match != null) {
                // Headline item with conventional commit prefix (e.g. style(theme):, feat(call):, fix:)
                val tag = match.groupValues[1].lowercase()
                val category = categorizeTag(tag, trimmed)
                currentSectionCategory = category
                hasSeenHeader = true

                var content = trimmed.substring(match.range.last + 1).trim()
                content = content.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                if (content.isNotBlank()) {
                    items.add(ChangelogItem(category = category, title = content, isSubBullet = false))
                }
            } else {
                if (isExplicitBullet && hasSeenHeader) {
                    // Sub-bullet detail under current section
                    val content = trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    items.add(ChangelogItem(category = currentSectionCategory, title = content, isSubBullet = true))
                } else {
                    val category = detectKeywordCategory(trimmed)
                    currentSectionCategory = category
                    hasSeenHeader = true
                    val content = trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    items.add(ChangelogItem(category = category, title = content, isSubBullet = false))
                }
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
                        val cat = detectKeywordCategory(st)
                        val title = st.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        items.add(ChangelogItem(category = cat, title = title, isSubBullet = false))
                    }
                }
            }
        }

        return items
    }

    private fun categorizeTag(tag: String, fullText: String): ChangelogCategory {
        return when (tag) {
            "feat", "feature", "add" -> ChangelogCategory.FEATURE
            "fix", "bug", "bugfix" -> ChangelogCategory.FIX
            "perf", "performance", "optimize", "optimization", "refactor", "clean", "chore", "build", "ci", "deps" -> ChangelogCategory.IMPROVEMENT
            "sec", "security" -> ChangelogCategory.SECURITY
            "ui", "style", "design", "theme" -> ChangelogCategory.UI
            else -> detectKeywordCategory(fullText)
        }
    }

    private fun detectKeywordCategory(text: String): ChangelogCategory {
        val lower = text.lowercase()
        return when {
            lower.contains("fix") || lower.contains("resolved") || lower.contains("crash") || lower.contains("bug") || lower.contains("glare") -> ChangelogCategory.FIX
            lower.contains("theme") || lower.contains("paper") || lower.contains("palette") || lower.contains("color") || lower.contains("background") || lower.contains("surface") || lower.contains("contrast") || lower.contains("ui") || lower.contains("design") -> ChangelogCategory.UI
            lower.contains("optim") || lower.contains("speed") || lower.contains("faster") || lower.contains("cache") || lower.contains("robust") || lower.contains("reconnect") || lower.contains("queue") || lower.contains("database") -> ChangelogCategory.IMPROVEMENT
            lower.contains("encrypt") || lower.contains("secur") || lower.contains("privacy") || lower.contains("sensor") || lower.contains("proximity") -> ChangelogCategory.SECURITY
            lower.contains("call") || lower.contains("voice") || lower.contains("video") || lower.contains("added") || lower.contains("new") || lower.contains("support") || lower.contains("message") || lower.contains("qr") -> ChangelogCategory.FEATURE
            else -> ChangelogCategory.UPDATE
        }
    }
}

@Composable
fun ChangelogView(
    rawNotes: String,
    isPaper: Boolean,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 240.dp
) {
    val items = remember(rawNotes) { ChangelogParser.parse(rawNotes) }

    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isPaper) NeoSurface else SurfaceCard)
                .border(
                    if (isPaper) 1.5.dp else 1.dp,
                    if (isPaper) NeoBlack else SurfaceBorder,
                    RoundedCornerShape(14.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                text = rawNotes.ifBlank { "General bug fixes and performance improvements." },
                fontSize = 13.sp,
                fontWeight = if (isPaper) FontWeight.Medium else FontWeight.Normal,
                color = if (isPaper) NeoBlack else TextSecondary,
                lineHeight = 18.sp
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPaper) NeoSurface else SurfaceCard.copy(alpha = 0.7f))
            .border(
                if (isPaper) 2.dp else 1.dp,
                if (isPaper) NeoBlack else SurfaceBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    if (item.isSubBullet) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Crisp circular bullet dot
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isPaper) NeoBlack.copy(alpha = 0.7f) else item.category.classicText.copy(alpha = 0.8f))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = item.title,
                fontSize = 12.5.sp,
                fontWeight = if (isPaper) FontWeight.Medium else FontWeight.Normal,
                color = if (isPaper) NeoBlack.copy(alpha = 0.85f) else TextSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isPaper) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(item.category.paperBg)
                        .border(1.5.dp, NeoBlack, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "${item.category.iconEmoji} ${item.category.label}",
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
                        text = "${item.category.iconEmoji} ${item.category.label}",
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
                fontSize = 13.sp,
                fontWeight = if (isPaper) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isPaper) NeoBlack else TextPrimary,
                lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
