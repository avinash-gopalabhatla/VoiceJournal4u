package com.carmind.voicejournal.core.stt

sealed class SttEvent {
    data class Partial(val text: String) : SttEvent()
    data class Final(val text: String) : SttEvent()
    data class Error(val message: String) : SttEvent()
}
