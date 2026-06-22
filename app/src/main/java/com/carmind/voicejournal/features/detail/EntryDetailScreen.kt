package com.carmind.voicejournal.features.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.carmind.voicejournal.core.journal.EntryCategory
import com.carmind.voicejournal.core.journal.EntryMood
import com.carmind.voicejournal.core.journal.JournalEntry
import com.carmind.voicejournal.shared.theme.AppColors
import com.carmind.voicejournal.shared.theme.color
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import androidx.compose.ui.text.TextStyle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry: JournalEntry,
    onBack: () -> Unit,
    onSave: (JournalEntry) -> Unit
) {
    var showTranscript by remember { mutableStateOf(false) }
    var editedSummary by remember { mutableStateOf(entry.summary) }
    var editedTranscript by remember { mutableStateOf(entry.rawTranscript) }
    var selectedCategory by remember { mutableStateOf(entry.category) }
    var selectedMood by remember { mutableStateOf(entry.mood) }

    var catMenuExpanded by remember { mutableStateOf(false) }
    var moodMenuExpanded by remember { mutableStateOf(false) }
    
    val mono = FontFamily.Monospace
    val fmt = SimpleDateFormat("MMMM d, yyyy • HH:mm", Locale.getDefault())
    val catColor = selectedCategory.color()

    val hasChanges = editedSummary != entry.summary || 
                     editedTranscript != entry.rawTranscript ||
                     selectedCategory != entry.category ||
                     selectedMood != entry.mood

    Scaffold(
        topBar = {
            Column(modifier = Modifier
                .background(AppColors.Background)
                .statusBarsPadding()
                .padding(top = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.TextPrimary)
                    }
                    Text(
                        text = "ENTRY DETAILS",
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = AppColors.TextMuted,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (hasChanges) {
                        Button(
                            onClick = {
                                onSave(entry.copy(
                                    summary = editedSummary, 
                                    rawTranscript = editedTranscript,
                                    category = selectedCategory,
                                    mood = selectedMood
                                ))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ProcessBlue),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("SAVE CHANGES", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = mono)
                        }
                    }
                }
            }
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // Header Info & Dropdowns
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fmt.format(Date(entry.timestamp)).uppercase(),
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = catColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Category Dropdown
                Box {
                    Surface(
                        onClick = { catMenuExpanded = true },
                        color = selectedCategory.color().copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, selectedCategory.color().copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = selectedCategory.label.uppercase(),
                            fontSize = 9.sp,
                            fontFamily = mono,
                            fontWeight = FontWeight.Bold,
                            color = selectedCategory.color(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = catMenuExpanded,
                        onDismissRequest = { catMenuExpanded = false }
                    ) {
                        EntryCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label, color = AppColors.TextPrimary, fontFamily = mono, fontSize = 12.sp) },
                                onClick = {
                                    selectedCategory = cat
                                    catMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Mood Dropdown
                Box {
                    Surface(
                        onClick = { moodMenuExpanded = true },
                        color = AppColors.SurfaceHigh,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, AppColors.Border)
                    ) {
                        Text(
                            text = selectedMood.emoji,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = moodMenuExpanded,
                        onDismissRequest = { moodMenuExpanded = false }
                    ) {
                        EntryMood.entries.forEach { mood ->
                            DropdownMenuItem(
                                leadingIcon = { Text(mood.emoji) },
                                text = { Text(mood.label, color = AppColors.TextPrimary, fontFamily = mono, fontSize = 12.sp) },
                                onClick = {
                                    selectedMood = mood
                                    moodMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = entry.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                lineHeight = 32.sp
            )

            Spacer(Modifier.height(24.dp))

            // Audio Player Section
            if (entry.audioPath != null) {
                AudioPlayer(audioPath = entry.audioPath)
                Spacer(Modifier.height(24.dp))
            }

            // Toggle Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(AppColors.Surface, RoundedCornerShape(22.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(22.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (!showTranscript) AppColors.ProcessBlue else Color.Transparent,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { showTranscript = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SUMMARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = if (!showTranscript) Color.White else AppColors.TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (showTranscript) AppColors.ProcessBlue else Color.Transparent,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { showTranscript = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "TRANSCRIPT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = if (showTranscript) Color.White else AppColors.TextMuted
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Editable Content
            Text(
                text = if (showTranscript) "EDIT TRANSCRIPT" else "EDIT SUMMARY",
                fontSize = 10.sp,
                fontFamily = mono,
                color = AppColors.ProcessBlue.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!showTranscript) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Surface,
                    border = BorderStroke(1.dp, AppColors.Border)
                ) {
                    Text(
                        text = editedSummary,
                        modifier = Modifier.padding(16.dp),
                        color = AppColors.TextSecondary,
                        fontSize = 16.sp,
                        lineHeight = 26.sp
                    )
                }
                
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = editedSummary,
                    onValueChange = { editedSummary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("EDIT SUMMARY (MARKDOWN)", fontSize = 10.sp, fontFamily = mono) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = AppColors.TextMuted, lineHeight = 20.sp, fontFamily = mono),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.ProcessBlue.copy(alpha = 0.5f),
                        unfocusedBorderColor = AppColors.Border,
                        focusedContainerColor = AppColors.Surface,
                        unfocusedContainerColor = AppColors.Surface,
                    )
                )
                
                if (entry.keyTopics.isNotEmpty()) {
                    Spacer(Modifier.height(32.dp))
                    Text("KEY TOPICS", fontSize = 12.sp, fontFamily = mono, color = AppColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    entry.keyTopics.forEach { topic ->
                        Text("• $topic", fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                if (entry.actionItems.isNotEmpty()) {
                    Spacer(Modifier.height(32.dp))
                    Text("ACTION ITEMS", fontSize = 12.sp, fontFamily = mono, color = AppColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    entry.actionItems.forEach { item ->
                        Text("□ $item", fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            } else {
                OutlinedTextField(
                    value = editedTranscript,
                    onValueChange = { editedTranscript = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = AppColors.TextSecondary, lineHeight = 24.sp, fontFamily = mono),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.ProcessBlue.copy(alpha = 0.5f),
                        unfocusedBorderColor = AppColors.Border,
                        focusedContainerColor = AppColors.Surface,
                        unfocusedContainerColor = AppColors.Surface,
                    )
                )
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun AudioPlayer(audioPath: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(audioPath))
            prepare()
        }
    }
    
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.play()
                }
                isPlaying = !isPlaying
            },
            modifier = Modifier.background(AppColors.ProcessBlue, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column {
            Text("Voice Recording", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
            Text("Tap to listen to the original entry", fontSize = 11.sp, color = AppColors.TextMuted)
        }
    }
}
