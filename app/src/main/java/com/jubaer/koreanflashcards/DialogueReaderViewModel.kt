package com.jubaer.koreanflashcards

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DialogueUiState(
    val inputText: String = "",
    val turns: List<DialogueTurn> = emptyList(),
    val glossary: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val selectedWord: String? = null
)

class DialogueReaderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DialogueUiState())
    val uiState: StateFlow<DialogueUiState> = _uiState

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun processText() {
        val text = _uiState.value.inputText
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val result = DialogueAiHelper.parseDialogueFromText(text)
                _uiState.value = _uiState.value.copy(turns = result.turns, glossary = result.glossary, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val result = DialogueAiHelper.parseDialogueFromImage(bitmap)
                _uiState.value = _uiState.value.copy(turns = result.turns, glossary = result.glossary, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun selectWord(word: String?) {
        _uiState.value = _uiState.value.copy(selectedWord = word)
    }

    fun reset() {
        _uiState.value = DialogueUiState()
    }
}

class DialogueReaderViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DialogueReaderViewModel() as T
    }
}
