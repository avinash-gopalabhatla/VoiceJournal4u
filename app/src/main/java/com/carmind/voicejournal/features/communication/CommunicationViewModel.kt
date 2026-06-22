package com.carmind.voicejournal.features.communication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carmind.voicejournal.core.journal.CoachSession
import com.carmind.voicejournal.core.journal.JournalRepository
import com.carmind.voicejournal.core.llm.LlmRouter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject

data class CoachScenario(
    val title: String,
    val task: String,
    val initialPrompt: String
)

val coachScenarios = listOf(
    CoachScenario(
        title = "Explain Tech Simply",
        task = "Describe a complex database system simply to a child.",
        initialPrompt = "Explain how a database index works in 2-3 sentences to a 10-year old."
    ),
    CoachScenario(
        title = "Client Delay News",
        task = "Inform a client politely that their release date is delayed by a week due to testing issues.",
        initialPrompt = "Write a short email/message explaining a one-week delay to a high-priority client without sounding unprofessional."
    ),
    CoachScenario(
        title = "Extension Request",
        task = "Negotiate a major task deadline extension with your manager because of incorrect API docs.",
        initialPrompt = "Explain to your manager why you need 3 extra days on the payment integration task."
    ),
    CoachScenario(
        title = "Elevator Pitch",
        task = "Pitch a voice-activated mobile journal feature to an investor.",
        initialPrompt = "Draft a 1-sentence hook and a 3-sentence high-impact pitch for this app's core feature."
    )
)

data class VocabImprovement(val original: String, val improved: String, val reason: String)
data class GrammarFix(val original: String, val improved: String, val explanation: String)

data class CommunicationAnalysis(
    val clarityScore: Int,
    val vocabularyImprovements: List<VocabImprovement>,
    val grammarFixes: List<GrammarFix>,
    val generalFeedback: String
)

data class CommunicationUiState(
    val currentInput: String = "",
    val selectedScenarioIndex: Int? = null,
    val analysis: CommunicationAnalysis? = null,
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val sessions: List<CoachSession> = emptyList()
)

@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val llmRouter: LlmRouter,
    private val repository: JournalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CommunicationUiState())
    val state: StateFlow<CommunicationUiState> = _state.asStateFlow()
    private val gson = Gson()

    init {
        viewModelScope.launch {
            repository.observeCoachSessions().collect { list ->
                _state.update { it.copy(sessions = list) }
            }
        }
    }

    fun onInputChange(input: String) {
        _state.update { it.copy(currentInput = input) }
    }

    fun selectScenario(index: Int?) {
        _state.update { 
            it.copy(
                selectedScenarioIndex = index,
                currentInput = if (index != null) coachScenarios[index].initialPrompt else "",
                analysis = null,
                error = null
            )
        }
    }

    fun analyzeCommunication() {
        val input = _state.value.currentInput
        if (input.isBlank()) return

        val selectedScenario = _state.value.selectedScenarioIndex?.let { coachScenarios[it] }

        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val pastSessions = _state.value.sessions.take(3)
                val historyContext = if (pastSessions.isNotEmpty()) {
                    "USER HISTORY (Clarity score progression): " + 
                    pastSessions.reversed().joinToString(" -> ") { "${it.clarityScore}%" } +
                    "\nObserve the history and encourage improvements on past recurring friction."
                } else {
                    ""
                }
                
                val scenarioContext = selectedScenario?.let {
                    "SCENARIO TASK: ${it.task}\nSCENARIO INITIAL PROMPT: ${it.initialPrompt}\n"
                } ?: ""

                val prompt = """
                    You are an elite communication coach. Analyze the user's verbal or typed message and return a strictly structured JSON response.
                    
                    $scenarioContext
                    $historyContext
                    
                    USER RESPONSE TO ANALYZE:
                    "$input"
                    
                    INSTRUCTIONS:
                    1. Evaluate a "clarity_score" (1-100) based on message structure, impact, filler word count, and grammatical flow.
                    2. Extract specific "vocabulary_improvements" as a list of word/phrase upgrades.
                    3. Identify "grammar_fixes" to correct spelling, punctuation, or phrase formatting.
                    4. Write "general_feedback" summarizing their communication performance, noting progress compared to history, and offering coaching advice.
                    
                    OUTPUT JSON FORMAT (Strictly follow this structure, return ONLY JSON):
                    {
                      "clarity_score": 75,
                      "vocabulary_improvements": [
                        { "original": "hard stuff", "improved": "complex details", "reason": "More professional vocabulary choice." }
                      ],
                      "grammar_fixes": [
                        { "original": "it don't work", "improved": "it doesn't work", "explanation": "Subject-verb agreement." }
                      ],
                      "general_feedback": "Your delivery has improved in clarity compared to last time. However, try to..."
                    }
                """.trimIndent()

                val response = llmRouter.complete(prompt, "You are an expert communication coach.")
                
                val cleanResponse = try {
                    val start = response.indexOf("{")
                    val end = response.lastIndexOf("}")
                    if (start != -1 && end != -1) {
                        response.substring(start, end + 1).trim()
                    } else {
                        response.trim()
                    }
                } catch (e: Exception) {
                    response.trim()
                }

                val json = JSONObject(cleanResponse)
                val score = json.optInt("clarity_score", 70)
                val feedback = json.optString("general_feedback", response)
                
                val vocabList = mutableListOf<VocabImprovement>()
                json.optJSONArray("vocabulary_improvements")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        vocabList.add(VocabImprovement(
                            original = obj.optString("original"),
                            improved = obj.optString("improved"),
                            reason = obj.optString("reason")
                        ))
                    }
                }

                val grammarList = mutableListOf<GrammarFix>()
                json.optJSONArray("grammar_fixes")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        grammarList.add(GrammarFix(
                            original = obj.optString("original"),
                            improved = obj.optString("improved"),
                            explanation = obj.optString("explanation")
                        ))
                    }
                }
                
                val analysis = CommunicationAnalysis(
                    clarityScore = score,
                    vocabularyImprovements = vocabList,
                    grammarFixes = grammarList,
                    generalFeedback = feedback
                )

                // Save session in Room DB
                val session = CoachSession(
                    promptText = selectedScenario?.initialPrompt ?: "Freeform Entry",
                    responseText = input,
                    clarityScore = score,
                    vocabularyTable = gson.toJson(vocabList),
                    grammarFixes = gson.toJson(grammarList),
                    feedback = feedback
                )
                repository.saveCoachSession(session)
                
                _state.update { it.copy(analysis = analysis, isAnalyzing = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isAnalyzing = false, error = e.message) }
            }
        }
    }

    fun clear() {
        _state.update { 
            CommunicationUiState(
                sessions = it.sessions
            )
        }
    }
}
