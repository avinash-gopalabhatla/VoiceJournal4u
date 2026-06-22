// shared/theme/AppTheme.kt
package com.carmind.voicejournal.shared.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.carmind.voicejournal.core.journal.EntryCategory

// ── Colors ────────────────────────────────────────────────────────────────────

object AppColors {
    val Background = Color(0xFF080812)
    val Surface = Color(0xFF0E0E1C)
    val SurfaceHigh = Color(0xFF12121E)
    val Border = Color(0xFF1E1E30)
    val TextPrimary = Color(0xFFE8E8F0)
    val TextSecondary = Color(0xFF8888AA)
    val TextMuted = Color(0xFF44445A)
    val Error = Color(0xFFFF6B6B)
    val RecordRed = Color(0xFFFF6B6B)
    val ProcessBlue = Color(0xFF4A9EFF)
}

val categoryColors = mapOf(
    EntryCategory.WORK to Color(0xFF4A9EFF),
    EntryCategory.HEALTH to Color(0xFF4ADBA2),
    EntryCategory.PERSONAL to Color(0xFFB57BFF),
    EntryCategory.IDEAS to Color(0xFFFFD166),
    EntryCategory.TASKS to Color(0xFFFF6B6B),
    EntryCategory.FINANCE to Color(0xFF4ADBD1),
    EntryCategory.LEARNING to Color(0xFFFF9F43),
    EntryCategory.RELATIONSHIPS to Color(0xFFF78FB3),
)

fun EntryCategory.color() = categoryColors[this] ?: Color(0xFF4A9EFF)

// ── Theme ─────────────────────────────────────────────────────────────────────

private val darkColorScheme = darkColorScheme(
    background = AppColors.Background,
    surface = AppColors.Surface,
    primary = Color(0xFF4A9EFF),
    secondary = Color(0xFF4ADBA2),
    error = AppColors.Error,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary,
)

@Composable
fun VoiceJournalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(),
        content = content,
    )
}
