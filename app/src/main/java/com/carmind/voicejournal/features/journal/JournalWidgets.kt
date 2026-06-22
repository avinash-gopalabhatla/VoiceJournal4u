// features/journal/JournalWidgets.kt
package com.carmind.voicejournal.features.journal

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carmind.voicejournal.core.journal.JournalEntry
import com.carmind.voicejournal.features.recording.RecordingStatus
import com.carmind.voicejournal.features.recording.RecordingUiState
import com.carmind.voicejournal.shared.theme.AppColors
import com.carmind.voicejournal.shared.theme.color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EntryCard(
    entry: JournalEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val mono = FontFamily.Monospace
    val color = entry.category.color()
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    Box(modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(AppColors.SurfaceHigh)
        .border(BorderStroke(1.dp, AppColors.Border), RoundedCornerShape(10.dp))
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onLongClick() }
            )
        }
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(color))
        Column(modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(entry.title, modifier = Modifier.weight(1f),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary, fontFamily = mono, lineHeight = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(entry.mood.emoji, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .border(BorderStroke(1.dp, color.copy(alpha = 0.3f)), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(entry.category.label, fontSize = 9.sp, color = color,
                        fontWeight = FontWeight.SemiBold, fontFamily = mono)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(entry.summary, fontSize = 11.sp, color = AppColors.TextSecondary,
                lineHeight = 16.sp, maxLines = 2)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.tags.take(3).forEach { tag ->
                        Text("#$tag", fontSize = 10.sp, color = AppColors.TextMuted, fontFamily = mono)
                    }
                }
                Text(fmt.format(Date(entry.timestamp)), fontSize = 10.sp,
                    color = AppColors.TextMuted, fontFamily = mono)
            }
        }
    }
}

@Composable
fun MicButton(
    state: RecordingUiState,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRecording = state.status == RecordingStatus.RECORDING
    val isProcessing = state.status == RecordingStatus.PROCESSING
    val isReviewing = state.status == RecordingStatus.REVIEWING

    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "micScale"
    )

    val bgColors = when {
        isRecording -> listOf(Color(0xFFFF6B6B), Color(0xFFCC3333))
        isProcessing -> listOf(Color(0xFF4A9EFF), Color(0xFF2255CC))
        isReviewing -> listOf(AppColors.ProcessBlue, Color(0xFF2255CC))
        else -> listOf(Color(0xFF1E2040), Color(0xFF12122A))
    }

    Box(
        modifier = modifier
            .scale(scale)
            .size(72.dp)
            .background(
                brush = Brush.radialGradient(bgColors),
                shape = CircleShape,
            )
            .border(BorderStroke(2.dp,
                if (isRecording) Color(0xFFFF6B6B).copy(alpha = 0.5f) else AppColors.Border),
                CircleShape)
            .pointerInput(isReviewing) {
                if (isReviewing) {
                    detectTapGestures { onConfirm() }
                } else {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Press -> onPressStart()
                                PointerEventType.Release -> onPressEnd()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = when {
                    isRecording -> "⏹"
                    isReviewing -> "✓"
                    else -> "🎙"
                },
                fontSize = 26.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun RecordingOverlay(
    state: RecordingUiState,
    onTranscriptChange: (String) -> Unit
) {
    val mono = FontFamily.Monospace
    val isProcessing = state.status == RecordingStatus.PROCESSING
    val isReviewing = state.status == RecordingStatus.REVIEWING
    val transcript = if (isReviewing) state.finalTranscript else state.finalTranscript.ifBlank { state.partialTranscript }

    Box(modifier = Modifier
        .fillMaxWidth()
        .background(AppColors.Surface, RoundedCornerShape(12.dp))
        .border(BorderStroke(1.dp, AppColors.Border), RoundedCornerShape(12.dp))
        .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isProcessing && !isReviewing) Waveform()
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        isProcessing && state.finalTranscript.isBlank() -> "⟳  Whisper AI: Transcribing…"
                        isProcessing && state.finalTranscript.isNotBlank() -> "✨  Gemma AI: Analyzing entry…"
                        isReviewing -> "✎  Review Entry"
                        else -> "●  Recording"
                    },
                    fontSize = 11.sp, fontFamily = mono, letterSpacing = 1.sp,
                    color = if (isProcessing || isReviewing) AppColors.ProcessBlue else AppColors.RecordRed,
                )
                Spacer(Modifier.weight(1f))
                if (state.recordingSeconds > 0) {
                    Text("${state.recordingSeconds}s", fontSize = 11.sp,
                        color = AppColors.TextMuted, fontFamily = mono)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (isReviewing) {
                OutlinedTextField(
                    value = transcript,
                    onValueChange = onTranscriptChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 12.sp, color = AppColors.TextSecondary, fontFamily = mono, lineHeight = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    placeholder = { Text("Tap to edit transcript…", fontSize = 12.sp, color = AppColors.TextMuted, fontFamily = mono) }
                )
            } else if (transcript.isNotBlank()) {
                Text(transcript, fontSize = 12.sp, color = AppColors.TextSecondary,
                    fontFamily = mono, lineHeight = 18.sp, maxLines = 4)
            }
        }
    }
}

@Composable
fun Waveform() {
    val bars = 8
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(bars) { i ->
            val infiniteTransition = rememberInfiniteTransition(label = "wave$i")
            val height by infiniteTransition.animateFloat(
                initialValue = 3f,
                targetValue = 14f + (i % 3) * 4f,
                animationSpec = infiniteRepeatable(
                    tween(300 + i * 60, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(modifier = Modifier
                .width(3.dp)
                .height(height.dp)
                .background(AppColors.RecordRed, RoundedCornerShape(2.dp)))
        }
    }
}
