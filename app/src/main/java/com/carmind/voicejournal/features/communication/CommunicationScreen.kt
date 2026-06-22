package com.carmind.voicejournal.features.communication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmind.voicejournal.shared.theme.AppColors
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.carmind.voicejournal.features.recording.RecordingViewModel
import androidx.compose.ui.text.TextStyle
import com.carmind.voicejournal.features.recording.RecordingStatus
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CommunicationScreen(
    viewModel: CommunicationViewModel = hiltViewModel(),
    recordingVm: RecordingViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val recState by recordingVm.state.collectAsState()
    val mono = FontFamily.Monospace

    val audioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    // Sync recording transcript to viewmodel
    LaunchedEffect(recState.finalTranscript) {
        if (recState.status == RecordingStatus.REVIEWING || recState.status == RecordingStatus.SAVED) {
            viewModel.onInputChange(recState.finalTranscript)
        }
    }

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
                        text = "IMPROVE COMMUNICATION",
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = AppColors.TextMuted,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.currentInput.isNotEmpty() || state.analysis != null || state.selectedScenarioIndex != null) {
                        TextButton(onClick = { 
                            viewModel.clear()
                            viewModel.selectScenario(null)
                            recordingVm.cancelRecording()
                        }) {
                            Text("RESET", color = AppColors.Error, fontSize = 10.sp, fontFamily = mono)
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

            Text(
                "Communication Coach",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                "Refine your message for better clarity and impact",
                fontSize = 13.sp,
                color = AppColors.TextMuted,
                fontFamily = mono
            )

            Spacer(Modifier.height(20.dp))

            // Scenario Chips Selector
            Text("SELECT A SCENARIO", fontSize = 10.sp, fontFamily = mono, color = AppColors.TextMuted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.selectedScenarioIndex == null,
                        onClick = { viewModel.selectScenario(null) },
                        label = { Text("Freeform Practice", fontSize = 10.sp, fontFamily = mono) },
                    )
                }
                itemsIndexed(coachScenarios) { idx, scenario ->
                    FilterChip(
                        selected = state.selectedScenarioIndex == idx,
                        onClick = { viewModel.selectScenario(idx) },
                        label = { Text(scenario.title, fontSize = 10.sp, fontFamily = mono) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.ProcessBlue.copy(alpha = 0.15f),
                            selectedLabelColor = AppColors.ProcessBlue,
                        ),
                    )
                }
            }

            // Coach Task Prompt description if selected
            state.selectedScenarioIndex?.let { idx ->
                val scenario = coachScenarios[idx]
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = AppColors.SurfaceHigh,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AppColors.Border)
                ) {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        Text("EXERCISE:", fontSize = 9.sp, fontFamily = mono, color = AppColors.ProcessBlue, fontWeight = FontWeight.Bold)
                        Text(scenario.task, fontSize = 12.sp, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("PROMPT:", fontSize = 9.sp, fontFamily = mono, color = AppColors.TextMuted, fontWeight = FontWeight.Bold)
                        Text(scenario.initialPrompt, fontSize = 12.sp, color = AppColors.TextPrimary, fontFamily = mono)
                    }
                }
            }

            // Coach Progress History Tracker
            if (state.sessions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = AppColors.Surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AppColors.Border)
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TrendingUp, null, tint = AppColors.ProcessBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        val progressStr = state.sessions.take(5).reversed().joinToString(" → ") { "${it.clarityScore}%" }
                        Text(
                            "Clarity Score Progression: $progressStr",
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary,
                            fontFamily = mono
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Input Section
            OutlinedTextField(
                value = state.currentInput,
                onValueChange = { viewModel.onInputChange(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Type your message or tap the microphone to dictate...", color = AppColors.TextMuted, fontSize = 14.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = AppColors.TextSecondary),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.ProcessBlue.copy(alpha = 0.5f),
                    unfocusedBorderColor = AppColors.Border,
                    focusedContainerColor = AppColors.Surface,
                    unfocusedContainerColor = AppColors.Surface,
                )
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Button
                FilledIconButton(
                    onClick = {
                        if (recState.status == RecordingStatus.RECORDING) {
                            recordingVm.stopRecording()
                        } else {
                            if (audioPermissionState.status.isGranted) {
                                recordingVm.startRecording()
                            } else {
                                audioPermissionState.launchPermissionRequest()
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (recState.status == RecordingStatus.RECORDING) AppColors.Error else AppColors.SurfaceHigh
                    )
                ) {
                    Icon(
                        if (recState.status == RecordingStatus.RECORDING) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Record",
                        tint = if (recState.status == RecordingStatus.RECORDING) Color.White else AppColors.ProcessBlue
                    )
                }

                Spacer(Modifier.width(24.dp))

                // Analyze Button
                Button(
                    onClick = { viewModel.analyzeCommunication() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.ProcessBlue),
                    enabled = state.currentInput.isNotBlank() && !state.isAnalyzing
                ) {
                    if (state.isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Analyze Delivery", fontWeight = FontWeight.Bold, fontFamily = mono)
                    }
                }
            }

            if (recState.status == RecordingStatus.RECORDING) {
                Text(
                    "Listening...",
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    color = AppColors.Error,
                    fontSize = 12.sp,
                    fontFamily = mono
                )
            }

            Spacer(Modifier.height(32.dp))

            // Results Section
            if (state.analysis != null) {
                FeedbackCard(state.analysis!!)
            } else if (!state.isAnalyzing && state.currentInput.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp).border(1.dp, AppColors.Border, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Coach analysis will appear here.", color = AppColors.TextMuted, fontSize = 12.sp, fontFamily = mono)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun FeedbackCard(analysis: CommunicationAnalysis) {
    val mono = FontFamily.Monospace
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Clarity Card
        Surface(
            color = AppColors.Surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppColors.Border)
        ) {
            Row(
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                    CircularProgressIndicator(
                        progress = { analysis.clarityScore / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = when {
                            analysis.clarityScore >= 80 -> Color(0xFF4ADBA2)
                            analysis.clarityScore >= 60 -> AppColors.ProcessBlue
                            else -> AppColors.Error
                        },
                        trackColor = AppColors.Border,
                        strokeWidth = 5.dp
                    )
                    Text("${analysis.clarityScore}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, fontFamily = mono)
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("CLARITY SCORE", fontSize = 10.sp, fontFamily = mono, color = AppColors.ProcessBlue, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            analysis.clarityScore >= 80 -> "Excellent structure, vocabulary, and impact."
                            analysis.clarityScore >= 60 -> "Good communication, with slight room for improvement."
                            else -> "Needs revision. Try using better verbs and simple phrasing."
                        },
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }

        // Vocabulary Upgrades
        if (analysis.vocabularyImprovements.isNotEmpty()) {
            Surface(
                color = AppColors.Surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("VOCABULARY UPGRADES", fontSize = 10.sp, fontFamily = mono, color = AppColors.ProcessBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    analysis.vocabularyImprovements.forEach { vocab ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                            Text("❌", fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(vocab.original, fontSize = 13.sp, color = AppColors.Error, fontFamily = mono, textDecoration = TextDecoration.LineThrough)
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("👉 ", fontSize = 10.sp)
                                    Text(vocab.improved, fontSize = 13.sp, color = Color(0xFF4ADBA2), fontWeight = FontWeight.Bold, fontFamily = mono)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(vocab.reason, fontSize = 11.sp, color = AppColors.TextSecondary)
                            }
                        }
                        Divider(color = AppColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // Grammar Corrections
        if (analysis.grammarFixes.isNotEmpty()) {
            Surface(
                color = AppColors.Surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("GRAMMAR CORRECTIONS", fontSize = 10.sp, fontFamily = mono, color = AppColors.ProcessBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    analysis.grammarFixes.forEach { grammar ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                            Text("✍️", fontSize = 12.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(grammar.original, fontSize = 12.sp, color = AppColors.TextSecondary, fontFamily = mono, textDecoration = TextDecoration.LineThrough)
                                    Spacer(Modifier.width(8.dp))
                                    Text("→", fontSize = 12.sp, color = AppColors.TextMuted)
                                    Spacer(Modifier.width(8.dp))
                                    Text(grammar.improved, fontSize = 13.sp, color = Color(0xFF4ADBA2), fontWeight = FontWeight.Bold, fontFamily = mono)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(grammar.explanation, fontSize = 11.sp, color = AppColors.TextSecondary)
                            }
                        }
                        Divider(color = AppColors.Border.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }

        // Coach Feedback
        Surface(
            color = AppColors.Surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AppColors.Border)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("COACH FEEDBACK", fontSize = 10.sp, fontFamily = mono, color = AppColors.ProcessBlue, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Markdown(
                    content = analysis.generalFeedback,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = object : MarkdownColors {
                        override val text: Color = AppColors.TextSecondary
                        override val codeText: Color = Color.White
                        override val inlineCodeText: Color = Color.White
                        override val linkText: Color = AppColors.ProcessBlue
                        override val codeBackground: Color = AppColors.SurfaceHigh
                        override val inlineCodeBackground: Color = AppColors.SurfaceHigh
                        override val dividerColor: Color = AppColors.Border
                        override val tableBackground: Color = AppColors.Surface
                        override val tableText: Color = AppColors.TextSecondary
                    },
                    typography = object : MarkdownTypography {
                        override val text: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                        override val code: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono)
                        override val inlineCode: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono)
                        override val h1: TextStyle = MaterialTheme.typography.headlineMedium.copy(color = AppColors.TextPrimary)
                        override val h2: TextStyle = MaterialTheme.typography.headlineSmall.copy(color = AppColors.TextPrimary)
                        override val h3: TextStyle = MaterialTheme.typography.titleLarge.copy(color = AppColors.TextPrimary)
                        override val h4: TextStyle = MaterialTheme.typography.titleMedium.copy(color = AppColors.TextPrimary)
                        override val h5: TextStyle = MaterialTheme.typography.titleSmall.copy(color = AppColors.TextPrimary)
                        override val h6: TextStyle = MaterialTheme.typography.labelLarge.copy(color = AppColors.TextPrimary)
                        override val quote: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                        override val paragraph: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                        override val ordered: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                        override val bullet: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                        override val list: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                        override val link: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                    }
                )
            }
        }
    }
}
