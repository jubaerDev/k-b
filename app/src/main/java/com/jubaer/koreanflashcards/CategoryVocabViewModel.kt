package com.jubaer.koreanflashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CategoryVocabUiState(
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val words: List<CategoryWordItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class CategoryVocabViewModel(private val repo: FlashcardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryVocabUiState())
    val uiState: StateFlow<CategoryVocabUiState> = _uiState

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val categories = repo.getVocabCategories()
                val first = categories.firstOrNull()
                val words = if (first != null) repo.getWordsByVocabCategory(first) else emptyList()
                _uiState.value = CategoryVocabUiState(categories, first, words, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun selectCategory(category: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedCategory = category, loading = true)
            try {
                val words = repo.getWordsByVocabCategory(category)
                _uiState.value = _uiState.value.copy(words = words, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    /** নির্বাচিত word গুলো (korean_word list) দিয়ে FlashcardItem বানিয়ে callback এ পাঠায়। */
    fun buildPracticeItems(koreanWords: List<String>, onReady: (List<FlashcardItem>) -> Unit) {
        viewModelScope.launch {
            val items = repo.buildFlashcardItemsForWords(koreanWords)
            onReady(items)
        }
    }
}

class CategoryVocabViewModelFactory(private val repo: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CategoryVocabViewModel(repo) as T
    }
}
