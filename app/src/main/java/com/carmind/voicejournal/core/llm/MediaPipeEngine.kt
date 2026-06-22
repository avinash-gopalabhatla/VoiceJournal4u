// core/llm/MediaPipeEngine.kt
package com.carmind.voicejournal.core.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.carmind.voicejournal.core.journal.EntryAnalysis
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.carmind.voicejournal.core.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.carmind.voicejournal.core.journal.EntryCategory
import com.carmind.voicejournal.core.journal.EntryMood
import org.json.JSONObject
import org.json.JSONArray

@Singleton
class MediaPipeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) : LlmEngine {

    override val name = "MediaPipe On-Device"

    private var currentModelPath: String? = null

    private suspend fun getModelPath(): String {
        val userPath = settings.llmModelPath.first()
        return if (!userPath.isNullOrBlank()) {
            userPath
        } else {
            "${context.filesDir.absolutePath}/llm/model.bin"
        }
    }

    private var inference: LlmInference? = null
    private var useCpuOnly = false

    private fun getDeviceTotalMemory(): Long {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem
        } catch (e: Throwable) {
            Log.e("MediaPipeEngine", "Failed to query system RAM capacity", e)
            0L
        }
    }

    private suspend fun buildInference(): LlmInference {
        val path = getModelPath()
        
        return try {
            if (useCpuOnly) throw IllegalStateException("Forced CPU mode")
            
            Log.d("MediaPipeEngine", "Attempting GPU initialization...")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            currentModelPath = path
            LlmInference.createFromOptions(context, options)
        } catch (e: Throwable) {
            Log.w("MediaPipeEngine", "GPU Init failed (likely work-group size or hardware), falling back to CPU. Error: ${e.message}")
            useCpuOnly = true
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            currentModelPath = path
            LlmInference.createFromOptions(context, options)
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val path = getModelPath()
        val file = File(path)
        val exists = file.exists()
        
        val totalMem = getDeviceTotalMemory()
        // If device has less than 5.8 GB total RAM, disable local Gemma to avoid out-of-memory crashes.
        val hasEnoughRam = totalMem == 0L || totalMem >= 5_800_000_000L
        
        Log.d("MediaPipeEngine", "Checking model at: $path, exists: $exists, RAM: ${totalMem / (1024*1024)}MB, isEnough: $hasEnoughRam")
        exists && hasEnoughRam
    }

    override suspend fun analyzeEntry(transcript: String, context: String?): EntryAnalysis {
        return withContext(Dispatchers.Default) {
            val path = getModelPath()
            if (inference != null && currentModelPath != path) {
                inference?.close()
                inference = null
            }

            val engine = inference ?: buildInference().also { inference = it }
            
            val userContent = if (context != null) {
                """
                RECENT CONTEXT (for reference only):
                $context
                
                NEW VOICE ENTRY TO ANALYZE (Primary focus):
                "$transcript"
                
                CRITICAL: The 'summary' MUST be about the NEW VOICE ENTRY. 
                Only use the context to better understand names, places, or continuity. 
                Do NOT repeat previous summaries.
                """.trimIndent()
            } else {
                "Voice entry to analyze: \"$transcript\""
            }

            val prompt = buildPrompt(
                system = ANALYSIS_SYSTEM_PROMPT,
                user = userContent
            )
            val response = engine.generateResponse(prompt)
            parseAnalysisJson(response)
        }
    }

    override suspend fun complete(prompt: String, systemPrompt: String?): String {
        return withContext(Dispatchers.Default) {
            val path = getModelPath()
            if (inference != null && currentModelPath != path) {
                inference?.close()
                inference = null
            }

            val engine = inference ?: buildInference().also { inference = it }
            val full = buildPrompt(
                system = systemPrompt ?: "You are a helpful AI assistant.",
                user = prompt,
            )
            engine.generateResponse(full)
        }
    }

    private fun buildPrompt(system: String, user: String): String =
        "<start_of_turn>user\n$system\n\n$user<end_of_turn>\n<start_of_turn>model\n"

    private fun parseAnalysisJson(jsonStr: String): EntryAnalysis {
        val cleaned = try {
            val start = jsonStr.indexOf("{")
            val end = jsonStr.lastIndexOf("}")
            if (start != -1 && end != -1) {
                jsonStr.substring(start, end + 1).trim()
            } else {
                jsonStr.trim()
            }
        } catch (e: Exception) {
            jsonStr.trim()
        }

        try {
            val json = JSONObject(cleaned)
            return EntryAnalysis(
                title = json.optString("title", "Voice Entry"),
                summary = json.optString("summary", "No summary generated."),
                category = EntryCategory.from(json.optString("category", "PERSONAL")),
                mood = EntryMood.from(json.optString("mood", "NEUTRAL")),
                tags = json.optJSONArray("tags")?.let { arr -> List(arr.length()) { i -> arr.getString(i) } } ?: emptyList(),
                actionItems = json.optJSONArray("action_items")?.let { arr -> List(arr.length()) { i -> arr.getString(i) } } ?: emptyList(),
                keyTopics = json.optJSONArray("key_topics")?.let { arr -> List(arr.length()) { i -> arr.getString(i) } } ?: emptyList(),
                dumpSnippets = json.optJSONArray("dump_snippets")?.let { arr -> List(arr.length()) { i -> arr.getString(i) } } ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e("MediaPipeEngine", "JSON Parse failed, trying regex fallback. Raw: $jsonStr", e)
            
            // Regex Fallbacks to rescue fields
            val extractedSummary = Regex("\"summary\"\\s*:\\s*\"([^\"]*)\"").find(cleaned)?.groupValues?.get(1)
                ?: Regex("\"summary\"\\s*:\\s*\"(.*)\"").find(cleaned)?.groupValues?.get(1)
                ?: "Voice entry processed. View details to edit transcript."

            val extractedTitle = Regex("\"title\"\\s*:\\s*\"([^\"]*)\"").find(cleaned)?.groupValues?.get(1)
                ?: "Voice Entry"

            val extractedCategory = EntryCategory.from(
                Regex("\"category\"\\s*:\\s*\"([^\"]*)\"").find(cleaned)?.groupValues?.get(1) ?: "PERSONAL"
            )

            val extractedMood = EntryMood.from(
                Regex("\"mood\"\\s*:\\s*\"([^\"]*)\"").find(cleaned)?.groupValues?.get(1) ?: "NEUTRAL"
            )

            return EntryAnalysis(
                title = extractedTitle,
                summary = extractedSummary,
                category = extractedCategory,
                mood = extractedMood,
                tags = emptyList(),
                actionItems = emptyList(),
                keyTopics = emptyList(),
                dumpSnippets = emptyList()
            )
        }
    }

    fun release() {
        inference?.close()
        inference = null
    }

    companion object {
        private const val ANALYSIS_SYSTEM_PROMPT = """
            You are an expert personal journal analyzer. 
            Analyze the provided voice transcript and return a VALID JSON object.
            
            CONTEXT: If 'Recent context' is provided, use it to make the summary more relevant and connected.
            
            DUMP MEMORY: Identify tangential information, random thoughts, or 'notes to self' that don't fit the main narrative and put them in 'dump_snippets'.
            
            CRITICAL: Do NOT use generic placeholders like "tag1" or "tag2". Generate meaningful, specific hashtags based on the actual content (e.g., #Productivity, #Fitness, #ProjectX, #Family).
            Write a detailed, informative summary (2-3 sentences), not plain or generic descriptions.
            
            EXPECTED OUTPUT FORMAT (Must be strict JSON, do not wrap in any other text):
            { 
              "title": "Short descriptive title", 
              "summary": "Detailed, specific summary of the entry.", 
              "category": "WORK|HEALTH|PERSONAL|IDEAS|TASKS|FINANCE|LEARNING|RELATIONSHIPS", 
              "mood": "ENERGIZED|FOCUSED|NEUTRAL|STRESSED|REFLECTIVE|EXCITED|TIRED", 
              "tags": ["specificTag1", "specificTag2"], 
              "action_items": ["Specific task 1", "Specific task 2"], 
              "key_topics": ["Main topic 1", "Main topic 2"],
              "dump_snippets": ["tangential thought 1"]
            }

            FEW-SHOT EXAMPLE:
            User: "I spent three hours working on the design system for our new mobile app today. It was quite exhausting but we got the navigation flows approved. Need to clean up the Figma prototypes tomorrow. Also, I should buy milk on the way home."
            Model:
            {
              "title": "Mobile App Navigation Approved",
              "summary": "Completed the layout design for the mobile app's navigation flows. The session was demanding but successful as the team approved the new layout, moving the prototypes to the finalization stage.",
              "category": "WORK",
              "mood": "FOCUSED",
              "tags": ["Figma", "DesignSystem", "NavigationFlows"],
              "action_items": ["Clean up Figma prototypes tomorrow"],
              "key_topics": ["Design System Layouts", "Navigation Approvals"],
              "dump_snippets": ["buy milk on the way home"]
            }
        """
    }
}
