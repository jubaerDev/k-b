package com.jubaer.koreanflashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class QuestionBankUiState(
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val questions: List<QuestionBankRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class QuestionBankViewModel(private val repo: FlashcardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionBankUiState())
    val uiState: StateFlow<QuestionBankUiState> = _uiState

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val categories = repo.getQuestionCategories()
                val first = categories.firstOrNull()
                val questions = if (first != null) repo.getQuestionsByCategory(first) else emptyList()
                _uiState.value = QuestionBankUiState(categories, first, questions, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun selectCategory(category: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedCategory = category, loading = true)
            try {
                val questions = repo.getQuestionsByCategory(category)
                _uiState.value = _uiState.value.copy(questions = questions, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }
}

class QuestionBankViewModelFactory(private val repo: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return QuestionBankViewModel(repo) as T
    }
}
