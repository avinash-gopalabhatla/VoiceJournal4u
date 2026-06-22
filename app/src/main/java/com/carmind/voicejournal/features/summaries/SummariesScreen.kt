package com.carmind.voicejournal.features.summaries

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
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
import com.carmind.voicejournal.core.journal.SummaryPeriod
import com.carmind.voicejournal.core.journal.TimeSummary
import com.carmind.voicejournal.shared.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummariesScreen(
    viewModel: SummariesViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val mono = FontFamily.Monospace
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TIME CAPSULE", fontSize = 14.sp, fontFamily = mono, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = { viewModel.refreshSummaries() },
                            enabled = !state.isRefreshing
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (state.isRefreshing) AppColors.TextMuted else AppColors.TextPrimary
                            )
                        }
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AppColors.Background,
                contentColor = AppColors.ProcessBlue,
                divider = {}
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Summaries", modifier = Modifier.padding(16.dp), fontFamily = mono, fontSize = 12.sp)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Dump Memory", modifier = Modifier.padding(16.dp), fontFamily = mono, fontSize = 12.sp)
                }
            }

            if (selectedTab == 0) {
                if (state.isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.ProcessBlue,
                        trackColor = AppColors.Background
                    )
                }
                SummaryList(state.summaries, state.usageDays, state.lastRefreshTime)
            } else {
                DumpMemoryList(state.dumpItems)
            }
        }
    }
}

@Composable
fun SummaryList(summaries: List<TimeSummary>, usageDays: Int, lastRefresh: Long) {
    val mono = FontFamily.Monospace
    val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
    ) {
        item {
            Column {
                UsageStats(usageDays)
                if (lastRefresh > 0) {
                    Text(
                        "Last updated: ${timeFmt.format(Date(lastRefresh))}",
                        fontSize = 10.sp,
                        color = AppColors.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        fontFamily = mono
                    )
                }
            }
        }

        if (summaries.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Summaries will appear after a week of usage.", color = AppColors.TextMuted, fontFamily = mono, fontSize = 12.sp)
                }
            }
        }

        items(summaries, key = { it.id }) { summary ->
            SummaryCard(summary)
        }
    }
}

@Composable
fun UsageStats(days: Int) {
    Surface(
        color = AppColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = AppColors.ProcessBlue)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Consistency", fontSize = 12.sp, color = AppColors.TextMuted, fontFamily = FontFamily.Monospace)
                Text("$days Days of Journaling", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            }
        }
    }
}

@Composable
fun SummaryCard(summary: TimeSummary) {
    val mono = FontFamily.Monospace
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    
    Surface(
        color = AppColors.SurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.background(AppColors.ProcessBlue.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(summary.period.name, fontSize = 10.sp, color = AppColors.ProcessBlue, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "${fmt.format(Date(summary.startDate))} - ${fmt.format(Date(summary.endDate))}",
                    fontSize = 12.sp, color = AppColors.TextMuted, fontFamily = mono
                )
            }
            
            Spacer(Modifier.height(16.dp))
            Text(summary.content, fontSize = 15.sp, color = AppColors.TextSecondary, lineHeight = 24.sp)
            
            if (summary.keyAchievements.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("KEY ACHIEVEMENTS", fontSize = 11.sp, fontFamily = mono, color = AppColors.TextMuted, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                summary.keyAchievements.forEach { achievement ->
                    Text("🏆 $achievement", fontSize = 13.sp, color = AppColors.TextPrimary, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
fun DumpMemoryList(items: List<String>) {
    val mono = FontFamily.Monospace
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Memory, null, Modifier.size(48.dp), tint = AppColors.TextMuted)
                Spacer(Modifier.height(16.dp))
                Text("No data in dump memory yet.", color = AppColors.TextMuted, fontFamily = mono, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
        ) {
            items(items) { item ->
                Surface(
                    color = AppColors.Surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AppColors.Border)
                ) {
                    Text(
                        item,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        fontFamily = mono
                    )
                }
            }
        }
    }
}
