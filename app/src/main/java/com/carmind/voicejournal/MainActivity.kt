package com.carmind.voicejournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carmind.voicejournal.features.journal.JournalScreen
import com.carmind.voicejournal.features.detail.EntryDetailScreen
import com.carmind.voicejournal.features.insights.InsightsScreen
import com.carmind.voicejournal.features.summaries.SummariesScreen
import com.carmind.voicejournal.features.settings.SettingsScreen
import com.carmind.voicejournal.features.communication.CommunicationScreen
import com.carmind.voicejournal.shared.theme.VoiceJournalTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import com.carmind.voicejournal.features.journal.JournalViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.carmind.voicejournal.core.setup.ModelManager
import com.carmind.voicejournal.features.setup.SetupScreen
import com.carmind.voicejournal.features.setup.SetupViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val startDest = if (modelManager.areModelsDownloaded()) "journal" else "setup"

        setContent {
            VoiceJournalTheme {
                val nav = rememberNavController()
                val journalVm: JournalViewModel = hiltViewModel()

                NavHost(nav, startDestination = startDest) {
                    composable("setup") {
                        val setupVm: SetupViewModel = hiltViewModel()
                        SetupScreen(
                            viewModel = setupVm,
                            onComplete = {
                                nav.navigate("journal") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            },
                            onSkip = { message ->
                                // Pass message to JournalScreen
                                nav.navigate("journal?message=$message") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        route = "journal?message={message}",
                        arguments = listOf(navArgument("message") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                    ) { backStackEntry ->
                        val message = backStackEntry.arguments?.getString("message")
                        JournalScreen(
                            journalVm = journalVm,
                            initialMessage = message,
                            onEntryClick = { entry ->
                                nav.navigate("entry/${entry.id}")
                            },
                            onInsightsClick = {
                                nav.navigate("insights")
                            },
                            onSettingsClick = {
                                nav.navigate("settings")
                            },
                            onSummariesClick = {
                                nav.navigate("summaries")
                            },
                            onCommunicationClick = {
                                nav.navigate("communication")
                            }
                        )
                    }
                    composable("communication") {
                        CommunicationScreen(
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("insights") {
                        InsightsScreen(
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("summaries") {
                        SummariesScreen(
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("entry/{id}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id") ?: ""
                        val state = journalVm.state.collectAsState().value
                        val entry = state.entries.find { it.id == id }
                        
                        if (entry != null) {
                            EntryDetailScreen(
                                entry = entry,
                                onBack = { nav.popBackStack() },
                                onSave = { updatedEntry ->
                                    journalVm.updateEntry(updatedEntry)
                                    nav.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
