package com.carmind.voicejournal.features.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carmind.voicejournal.core.setup.DownloadState
import com.carmind.voicejournal.shared.theme.AppColors

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onComplete: () -> Unit,
    onSkip: (String) -> Unit
) {
    val state by viewModel.downloadState.collectAsState()
    val mono = FontFamily.Monospace

    LaunchedEffect(state) {
        if (state is DownloadState.Completed) {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "AI INITIALIZATION",
                fontSize = 12.sp,
                color = AppColors.ProcessBlue,
                letterSpacing = 2.sp,
                fontFamily = mono
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                "This app requires local AI models for offline transcription and analysis (~1.2GB).",
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                fontFamily = mono
            )

            Spacer(Modifier.height(32.dp))

            when (val s = state) {
                is DownloadState.Idle -> {
                    Button(
                        onClick = { viewModel.startDownload() },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.ProcessBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DOWNLOAD MODELS", color = Color.White, fontFamily = mono)
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    TextButton(onClick = { onSkip("Please configure the necessary STT and LLM models from the settings option.") }) {
                        Text("SKIP FOR NOW", color = AppColors.TextMuted, fontFamily = mono)
                    }
                }
                is DownloadState.Downloading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (s.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = AppColors.ProcessBlue,
                                trackColor = AppColors.Border
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = AppColors.ProcessBlue,
                                trackColor = AppColors.Border
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            s.currentFile,
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary,
                            fontFamily = mono,
                            textAlign = TextAlign.Center
                        )
                        if (s.progress > 0f) {
                            Text(
                                "${(s.progress * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = AppColors.TextMuted,
                                fontFamily = mono
                            )
                        }
                    }
                }
                is DownloadState.Error -> {
                    Text(
                        "Error: ${s.message}",
                        color = AppColors.Error,
                        fontSize = 12.sp,
                        fontFamily = mono,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.startDownload() }) {
                        Text("RETRY", fontFamily = mono)
                    }
                    TextButton(onClick = { onSkip("Please configure the necessary STT and LLM models from the settings option.") }) {
                        Text("SKIP", color = AppColors.TextMuted, fontFamily = mono)
                    }
                }
                DownloadState.Completed -> {
                    Text("Ready!", color = Color(0xFF4ADBA2), fontFamily = mono)
                }
            }
        }
    }
}
