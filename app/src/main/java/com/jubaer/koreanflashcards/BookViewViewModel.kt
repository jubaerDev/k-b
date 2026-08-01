package com.jubaer.koreanflashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BookViewUiState(
    val chapters: List<Int> = emptyList(),
    val selectedChapter: Int? = null,
    val paragraphs: List<BookParagraphRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class BookViewViewModel(private val repo: FlashcardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BookViewUiState())
    val uiState: StateFlow<BookViewUiState> = _uiState

    init {
        loadChapters()
    }

    fun loadChapters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val chapters = repo.getBookChapters()
                val first = chapters.firstOrNull()
                val paragraphs = if (first != null) repo.getBookParagraphs(first) else emptyList()
                _uiState.value = BookViewUiState(chapters, first, paragraphs, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun selectChapter(chapter: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedChapter = chapter, loading = true)
            try {
                val paragraphs = repo.getBookParagraphs(chapter)
                _uiState.value = _uiState.value.copy(paragraphs = paragraphs, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }
}

class BookViewViewModelFactory(private val repo: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BookViewViewModel(repo) as T
    }
}
