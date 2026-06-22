package com.carmind.voicejournal.features.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmind.voicejournal.shared.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val mono = FontFamily.Monospace

    val whisperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importWhisperModel(it) }
    }

    val llmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importLlmModel(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", fontSize = 14.sp, fontFamily = mono, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = AppColors.TextPrimary,
                    navigationIconContentColor = AppColors.TextPrimary
                )
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (state.statusMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (state.isError) AppColors.Error.copy(alpha = 0.1f) else AppColors.ProcessBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (state.isError) AppColors.Error else AppColors.ProcessBlue)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppColors.ProcessBlue)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = state.statusMessage!!,
                            fontSize = 12.sp,
                            color = if (state.isError) AppColors.Error else AppColors.TextPrimary,
                            fontFamily = mono
                        )
                    }
                }
            }

            Text("Model Management", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            
            ModelSection(
                title = "Whisper STT Model",
                description = "Choose a .bin file for offline speech recognition (tiny, base, etc.)",
                currentPath = state.whisperPath,
                currentName = state.whisperName,
                onSelect = { whisperLauncher.launch("*/*") },
                onClear = { viewModel.clearWhisperModel() }
            )

            ModelSection(
                title = "MediaPipe LLM Model",
                description = "Choose a .bin file for on-device AI analysis (Gemma 2b, etc.)",
                currentPath = state.llmPath,
                currentName = state.llmName,
                onSelect = { llmLauncher.launch("*/*") },
                onClear = { viewModel.clearLlmModel() }
            )
        }
    }
}

@Composable
fun ModelSection(
    title: String,
    description: String,
    currentPath: String?,
    currentName: String?,
    onSelect: () -> Unit,
    onClear: () -> Unit
) {
    val mono = FontFamily.Monospace
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Text(description, fontSize = 12.sp, color = AppColors.TextMuted)
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = AppColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!currentPath.isNullOrBlank()) {
                    Text("ACTIVE MODEL:", fontSize = 10.sp, fontFamily = mono, color = AppColors.ProcessBlue, fontWeight = FontWeight.Bold)
                    Text(currentName ?: "Custom Model", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(currentPath, fontSize = 9.sp, fontFamily = mono, color = AppColors.TextMuted, maxLines = 1)
                    Spacer(Modifier.height(12.dp))
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceHigh),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp), tint = AppColors.TextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Select Model", fontSize = 12.sp, color = AppColors.TextPrimary)
                    }
                    
                    if (!currentPath.isNullOrBlank()) {
                        TextButton(onClick = onClear) {
                            Text("Reset to Default", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}
