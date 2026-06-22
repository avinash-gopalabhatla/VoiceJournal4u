// features/journal/JournalScreen.kt
package com.carmind.voicejournal.features.journal

import androidx.compose.animation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmind.voicejournal.core.journal.EntryCategory
import com.carmind.voicejournal.core.journal.JournalEntry
import com.carmind.voicejournal.features.recording.RecordingStatus
import com.carmind.voicejournal.features.recording.RecordingViewModel
import com.carmind.voicejournal.shared.theme.AppColors
import com.carmind.voicejournal.shared.theme.color
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    journalVm: JournalViewModel = hiltViewModel(),
    recordingVm: RecordingViewModel = hiltViewModel(),
    initialMessage: String? = null,
    onEntryClick: (JournalEntry) -> Unit,
    onInsightsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSummariesClick: () -> Unit,
    onCommunicationClick: () -> Unit
) {
    val state by journalVm.state.collectAsState()
    val recState by recordingVm.state.collectAsState()
    val mono = FontFamily.Monospace

    var setupMessage by remember { mutableStateOf(initialMessage) }
    var entryToDelete by remember { mutableStateOf<JournalEntry?>(null) }

    val audioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(AppColors.Background)
        .imePadding()
        .navigationBarsPadding()
    ) {
        val isOverlayActive = recState.status in listOf(RecordingStatus.RECORDING, RecordingStatus.PROCESSING, RecordingStatus.REVIEWING)

        Column(modifier = Modifier
            .fillMaxSize()
            .then(if (isOverlayActive) Modifier.blur(12.dp) else Modifier)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Column(modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 0.dp)) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("VOICE", fontSize = 10.sp, color = AppColors.TextMuted,
                            letterSpacing = 3.sp, fontFamily = mono)
                        Text("Journal", fontSize = 26.sp, color = AppColors.TextPrimary,
                            fontWeight = FontWeight.Medium, fontFamily = mono)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings",
                                tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onSummariesClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.History, contentDescription = "Summaries",
                                tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onCommunicationClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = "Communication Coach",
                                tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onInsightsClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Insights",
                                tint = AppColors.ProcessBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${state.entries.size} entries", fontSize = 11.sp,
                                color = AppColors.TextMuted, fontFamily = mono)
                            state.syncStatus?.let {
                                Text(it, fontSize = 10.sp, color = AppColors.ProcessBlue, fontFamily = mono)
                            }
                            IconButton(onClick = { journalVm.syncToCarMind() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync to CarMind",
                                    tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Setup Message if skipped
                setupMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        color = Color(0xFF1E1E30),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AppColors.ProcessBlue.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = AppColors.ProcessBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(msg, fontSize = 11.sp, color = AppColors.TextPrimary, fontFamily = mono, modifier = Modifier.weight(1f))
                            IconButton(onClick = { setupMessage = null }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Default.Close, null, tint = AppColors.TextMuted, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                // Search
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { journalVm.setSearch(it) },
                    placeholder = { Text("search transcripts, tags, topics…",
                        fontSize = 12.sp, color = AppColors.TextMuted, fontFamily = mono) },
                    leadingIcon = { Icon(Icons.Default.Search, null,
                        tint = AppColors.TextMuted, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty())
                            IconButton(onClick = { journalVm.setSearch("") }) {
                                Icon(Icons.Default.Close, null, tint = AppColors.TextMuted,
                                    modifier = Modifier.size(16.dp))
                            }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 12.sp, color = AppColors.TextSecondary, fontFamily = mono),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.ProcessBlue,
                        unfocusedBorderColor = AppColors.Border,
                        focusedContainerColor = AppColors.Surface,
                        unfocusedContainerColor = AppColors.Surface,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(Modifier.height(10.dp))

                // Category filter chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedCategory == null,
                            onClick = { journalVm.setCategory(null) },
                            label = { Text("All", fontSize = 10.sp, fontFamily = mono) },
                        )
                    }
                    items(EntryCategory.entries) { cat ->
                        val color = cat.color()
                        FilterChip(
                            selected = state.selectedCategory == cat,
                            onClick = { journalVm.setCategory(if (state.selectedCategory == cat) null else cat) },
                            label = { Text(cat.label, fontSize = 10.sp, fontFamily = mono) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.15f),
                                selectedLabelColor = color,
                            ),
                        )
                    }
                }
            }

            // ── Entry list ────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.entries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎙", fontSize = 40.sp)
                                Spacer(Modifier.height(12.dp))
                                Text("Hold the mic to speak your first entry",
                                    fontSize = 13.sp, color = AppColors.TextMuted, fontFamily = mono)
                            }
                        }
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onClick = { onEntryClick(entry) },
                            onLongClick = { entryToDelete = entry }
                        )
                    }
                }
            }
        }

        // ── Deletion Confirmation ─────────────────────────────────────────────
        entryToDelete?.let { entry ->
            AlertDialog(
                onDismissRequest = { entryToDelete = null },
                containerColor = AppColors.Surface,
                titleContentColor = AppColors.TextPrimary,
                textContentColor = AppColors.TextSecondary,
                title = { Text("Delete Entry?", fontFamily = mono, fontSize = 16.sp) },
                text = { Text("Are you sure you want to permanently delete \"${entry.title}\"?", fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        journalVm.delete(entry)
                        entryToDelete = null
                    }) {
                        Text("DELETE", color = AppColors.Error, fontWeight = FontWeight.Bold, fontFamily = mono)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { entryToDelete = null }) {
                        Text("CANCEL", color = AppColors.TextMuted, fontFamily = mono)
                    }
                }
            )
        }

        // ── Background Blur Scrim ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = isOverlayActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = false) {} // Scrim to block interaction
            )
        }

        // ── Recording overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = recState.status in listOf(RecordingStatus.RECORDING, RecordingStatus.PROCESSING, RecordingStatus.REVIEWING),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp, start = 20.dp, end = 20.dp)
        ) {
            RecordingOverlay(
                state = recState,
                onTranscriptChange = { recordingVm.updateTranscript(it) }
            )
        }

        // ── Mic button ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recState.status in listOf(RecordingStatus.READY, RecordingStatus.IDLE, RecordingStatus.SAVED)) {
                    IconButton(
                        onClick = { recordingVm.startManualEntry() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(AppColors.Surface, CircleShape)
                            .border(1.dp, AppColors.Border, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.EditNote, 
                            contentDescription = "Manual Entry", 
                            tint = AppColors.ProcessBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                }

                if (recState.status in listOf(RecordingStatus.REVIEWING, RecordingStatus.PROCESSING)) {
                    IconButton(
                        onClick = { recordingVm.cancelRecording() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF2A0A0A), CircleShape)
                            .border(1.dp, AppColors.Error.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close, 
                            contentDescription = "Discard", 
                            tint = AppColors.Error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                }

                MicButton(
                    state = recState,
                    onPressStart = {
                        if (audioPermissionState.status.isGranted) {
                            recordingVm.startRecording()
                        } else {
                            audioPermissionState.launchPermissionRequest()
                        }
                    },
                    onPressEnd = { recordingVm.stopRecording() },
                    onConfirm = { recordingVm.analyzeTranscript() },
                )

                if (recState.status in listOf(RecordingStatus.READY, RecordingStatus.IDLE, RecordingStatus.SAVED)) {
                    Spacer(Modifier.width(68.dp)) // Offset to keep mic centered when manual entry is shown
                }

                if (recState.status in listOf(RecordingStatus.REVIEWING, RecordingStatus.PROCESSING)) {
                    Spacer(Modifier.width(68.dp)) // Offset to keep the main button roughly centered
                }
            }
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        recState.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp, start = 16.dp, end = 16.dp),
                containerColor = Color(0xFF1A0A0A),
                contentColor = AppColors.Error,
            ) { Text(msg, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}
