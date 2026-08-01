package com.jubaer.koreanflashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UploadUiState(
    val fileName: String? = null,
    val parsedPairs: List<Pair<String, String>> = emptyList(),
    val chapterNumber: Int = 1,
    val alreadyExists: Boolean = false,
    val overwriteConfirmed: Boolean = false,
    val loading: Boolean = false,
    val checkingExists: Boolean = false,
    val error: String? = null,
    val resultWords: List<VocabWord>? = null
)

class UploadChapterViewModel(private val repo: FlashcardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState

    init {
        suggestNextChapter()
    }

    private fun suggestNextChapter() {
        viewModelScope.launch {
            try {
                val chapters = repo.getAllChapters()
                val next = (chapters.maxOrNull() ?: 0) + 1
                _uiState.value = _uiState.value.copy(chapterNumber = next)
                checkExists(next)
            } catch (_: Exception) {
                // ব্যর্থ হলেও default chapter 1 থেকেই যাবে
            }
        }
    }

    fun onFileParsed(fileName: String, pairs: List<Pair<String, String>>) {
        _uiState.value = _uiState.value.copy(
            fileName = fileName,
            parsedPairs = pairs,
            resultWords = null,
            error = null
        )
    }

    fun onChapterNumberChanged(newChapter: Int) {
        _uiState.value = _uiState.value.copy(chapterNumber = newChapter, overwriteConfirmed = false, resultWords = null)
        checkExists(newChapter)
    }

    private fun checkExists(chapter: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(checkingExists = true)
            try {
                val exists = repo.chapterExists(chapter)
                _uiState.value = _uiState.value.copy(alreadyExists = exists, checkingExists = false)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(checkingExists = false)
            }
        }
    }

    fun toggleOverwriteConfirmed(value: Boolean) {
        _uiState.value = _uiState.value.copy(overwriteConfirmed = value)
    }

    fun saveChapter() {
        val current = _uiState.value
        if (current.parsedPairs.isEmpty()) return
        if (current.alreadyExists && !current.overwriteConfirmed) return

        viewModelScope.launch {
            _uiState.value = current.copy(loading = true, error = null)
            try {
                repo.saveRawChapter(current.chapterNumber, current.parsedPairs, overwrite = current.alreadyExists)
                repo.rebuildDatabase()
                val finalWords = repo.getWordsForChapterFinal(current.chapterNumber)
                repo.syncFromServer() // Room local cache নতুন করে ঠিক করে নেওয়া
                _uiState.value = _uiState.value.copy(loading = false, resultWords = finalWords)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        _uiState.value = UploadUiState()
        suggestNextChapter()
    }
}

class UploadChapterViewModelFactory(private val repo: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return UploadChapterViewModel(repo) as T
    }
}
