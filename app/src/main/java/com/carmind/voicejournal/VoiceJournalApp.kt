package com.carmind.voicejournal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class VoiceJournalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the LLM folder so the user can place the .bin file there
        val llmDir = File(filesDir, "llm")
        if (!llmDir.exists()) {
            llmDir.mkdirs()
        }
    }
}
