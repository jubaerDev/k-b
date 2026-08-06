package com.jubaer.koreanflashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CustomSetUiState(
    val sets: List<CustomSet> = emptyList(),
    val selectedSet: CustomSet? = null,
    val selectedSetWords: List<FlashcardItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class CustomSetViewModel(private val repo: FlashcardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomSetUiState())
    val uiState: StateFlow<CustomSetUiState> = _uiState

    init {
        loadSets()
    }

    fun loadSets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val sets = repo.getCustomSets()
                _uiState.value = _uiState.value.copy(sets = sets, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun createSet(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                repo.createCustomSet(name.trim())
                loadSets()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Unknown error")
            }
        }
    }

    fun deleteSet(set: CustomSet) {
        viewModelScope.launch {
            try {
                repo.deleteCustomSet(set.id)
                if (_uiState.value.selectedSet?.id == set.id) {
                    _uiState.value = _uiState.value.copy(selectedSet = null, selectedSetWords = emptyList())
                }
                loadSets()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Unknown error")
            }
        }
    }

    fun openSet(set: CustomSet) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedSet = set, loading = true)
            try {
                val words = repo.getCustomSetWordItems(set.id)
                _uiState.value = _uiState.value.copy(selectedSetWords = words, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun closeSet() {
        _uiState.value = _uiState.value.copy(selectedSet = null, selectedSetWords = emptyList())
    }

    fun addWords(koreanWords: List<String>) {
        val set = _uiState.value.selectedSet ?: return
        viewModelScope.launch {
            try {
                repo.addWordsToCustomSet(set.id, koreanWords)
                openSet(set)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Unknown error")
            }
        }
    }

    fun removeWord(koreanWord: String) {
        val set = _uiState.value.selectedSet ?: return
        viewModelScope.launch {
            try {
                repo.removeWordFromCustomSet(set.id, koreanWord)
                openSet(set)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Unknown error")
            }
        }
    }

    /** সব vocab এর মধ্যে থেকে search করে word খুঁজে দেয় (add করার জন্য)। */
    suspend fun searchVocab(query: String): List<VocabWordEntity> {
        if (query.isBlank()) return emptyList()
        val all = repo.getAllVocabWords()
        return all.filter {
            it.korean_word.contains(query, ignoreCase = true) || it.bangla_meaning.contains(query, ignoreCase = true)
        }.take(30)
    }
}

class CustomSetViewModelFactory(private val repo: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CustomSetViewModel(repo) as T
    }
}
