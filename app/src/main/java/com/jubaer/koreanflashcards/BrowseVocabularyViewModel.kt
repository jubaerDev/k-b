package com.jubaer.koreanflashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BrowseUiState(
    val chapters: List<Int> = emptyList(),
    val selectedChapter: Int? = null,
    val groupedWords: Map<String, List<FlashcardItem>> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null
)

class BrowseVocabularyViewModel(private val repo: FlashcardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState

    init {
        loadChapters()
    }

    fun loadChapters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val allWords = repo.getAllVocabWords()
                val chapters = allWords.map { it.chapter_number }.distinct().sorted()
                val firstChapter = chapters.firstOrNull()
                val grouped = if (firstChapter != null) repo.getChapterWordsGroupedByCategory(firstChapter) else emptyMap()
                _uiState.value = BrowseUiState(
                    chapters = chapters,
                    selectedChapter = firstChapter,
                    groupedWords = grouped,
                    loading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun selectChapter(chapter: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedChapter = chapter, loading = true)
            try {
                val grouped = repo.getChapterWordsGroupedByCategory(chapter)
                _uiState.value = _uiState.value.copy(groupedWords = grouped, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }
}

class BrowseVocabularyViewModelFactory(private val repo: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BrowseVocabularyViewModel(repo) as T
    }
}
