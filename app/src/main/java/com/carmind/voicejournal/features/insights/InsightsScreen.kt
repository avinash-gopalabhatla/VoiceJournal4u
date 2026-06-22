package com.carmind.voicejournal.features.insights

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmind.voicejournal.core.journal.EntryCategory
import com.carmind.voicejournal.shared.theme.AppColors
import com.carmind.voicejournal.shared.theme.color

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val mono = FontFamily.Monospace

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
                        text = "INSIGHTS",
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = AppColors.TextMuted,
                        letterSpacing = 2.sp
                    )
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
                "Pattern Discovery",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                "Filter entries to generate deeper collective AI insights",
                fontSize = 13.sp,
                color = AppColors.TextMuted,
                fontFamily = mono
            )

            Spacer(Modifier.height(24.dp))

            // Category Filter
            Text("CATEGORY", fontSize = 11.sp, fontFamily = mono, color = AppColors.TextMuted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EntryCategory.entries) { cat ->
                    val isSelected = state.selectedCategory == cat
                    val color = cat.color()
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.toggleCategory(cat) },
                        label = { Text(cat.label, fontSize = 10.sp, fontFamily = mono) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Tag Filter
            if (state.allTags.isNotEmpty()) {
                Text("TAGS", fontSize = 11.sp, fontFamily = mono, color = AppColors.TextMuted, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.allTags.forEach { tag ->
                        val isSelected = tag in state.selectedTags
                        SuggestionChip(
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text("#$tag", fontSize = 10.sp, fontFamily = mono) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) AppColors.ProcessBlue.copy(alpha = 0.15f) else Color.Transparent,
                                labelColor = if (isSelected) AppColors.ProcessBlue else AppColors.TextSecondary
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) AppColors.ProcessBlue.copy(alpha = 0.5f) else AppColors.Border
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Generate Button
            Button(
                onClick = { viewModel.generateInsight() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceHigh),
                border = BorderStroke(1.dp, AppColors.Border),
                enabled = !state.isGenerating && state.filteredEntries.isNotEmpty()
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.ProcessBlue)
                } else {
                    Icon(Icons.Default.AutoAwesome, null, tint = AppColors.ProcessBlue)
                    Spacer(Modifier.width(12.dp))
                    Text("Generate Collective Insight", color = AppColors.TextPrimary, fontFamily = mono, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Analyzing ${state.filteredEntries.size} relevant entries",
                fontSize = 10.sp,
                fontFamily = mono,
                color = AppColors.TextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(32.dp))

            // Result Area
            state.generatedInsight?.let { insight ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface, RoundedCornerShape(16.dp))
                        .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = AppColors.ProcessBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AI DISCOVERY", fontSize = 11.sp, fontFamily = mono, color = AppColors.ProcessBlue, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = insight,
                            fontSize = 15.sp,
                            color = AppColors.TextSecondary,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(60.dp))
        }
    }
}
