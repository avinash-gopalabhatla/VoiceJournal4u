# VoiceJournal 🎙️✨

VoiceJournal is a privacy-focused, on-device AI journaling application. It enables users to record voice entries which are then transcribed and analyzed locally using state-of-the-art machine learning models. No audio or text ever leaves your device.

## 🚀 Architecture Overview

VoiceJournal follows modern Android development practices with an **MVVM (Model-View-ViewModel)** architecture and an **Offline-First** approach.

### Tech Stack
- **UI**: Jetpack Compose (Material 3) with a custom dark-themed design.
- **Logic**: Kotlin Coroutines & Flow for reactive programming.
- **Dependency Injection**: Hilt (Dagger).
- **Local Database**: Room (SQLite) for storing journal entries.
- **On-Device STT**: `whisper.cpp` (C++) integrated via JNI for high-quality offline speech-to-text.
- **On-Device LLM**: MediaPipe GenAI (Gemma 2b) for summarizing entries, detecting mood, and generating tags.
- **Audio Engine**: Media3 / ExoPlayer for playback and standard Android APIs for recording.
- **Background Work**: WorkManager for handling intensive AI tasks.

## ✨ Key Features
- **Instant Voice Recording**: Simple press-and-hold interface to capture thoughts.
- **Local Transcription**: Converts voice to text offline using OpenAI's Whisper models.
- **AI Analysis**: Automatically generates:
    - 🏷️ **Titles**
    - 📝 **Summaries**
    - 🎭 **Mood Detection** (using Emojis)
    - 🗂️ **Categories** (Personal, Work, Ideas, etc.)
    - #️⃣ **Auto-generated Tags**
- **History Management**: Categorized view of all your past entries.
- **Privacy by Design**: All processing happens on-device; no cloud connection required.

## 📖 How to Use

1.  **Permissions**: On first launch, grant Microphone and Storage permissions.
2.  **Model Installation**:
    - The app expects models to be placed in the internal storage (`/data/user/0/com.carmind.voicejournal/files/llm/`).
    - Place your `whisper.bin` and Gemma `model.bin` files there.
3.  **Create an Entry**:
    - Tap and hold the **Mic Icon** to start recording.
    - Release the icon to finish recording.
4.  **Wait for AI**:
    - **Whisper** will transcribe the audio first.
    - **Gemma** will then analyze the text to create a summary and metadata.
5.  **Review & Save**:
    - Review the generated transcript and summary.
    - Tap the **Checkmark (✓)** to save the entry to your history.

## ⚠️ Limitations

-   **Hardware Requirements**: On-device AI is resource-intensive. High-performance devices (e.g., Snapdragon 8 Gen 2/3 or Elite) are recommended for smooth transcription and analysis.
-   **Model Size**: Whisper and Gemma models are large (several hundred MBs to ~2GB). Ensure you have sufficient storage space.
-   **Battery Consumption**: Intensive AI tasks can significantly drain the battery if used frequently throughout the day.
-   **Initial Accuracy**: Whisper accuracy depends on the model size used (base/small/medium). Smaller models are faster but may have more typos.

---

*Built with ❤️ for privacy and self-reflection.*
