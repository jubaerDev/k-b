package com.jubaer.koreanflashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UiState {
    data class Setup(
        val chapterSummaries: List<ChapterSummary> = emptyList(),
        val totalTracked: Int = 0,
        val mastered: Int = 0,
        val loading: Boolean = false,
        val error: String? = null
    ) : UiState()

    data class Practicing(
        val queue: List<FlashcardItem>,
        val index: Int,
        val showAnswer: Boolean,
        val correctCount: Int,
        val wrongCount: Int
    ) : UiState()

    data class Finished(val correctCount: Int, val wrongCount: Int) : UiState()
}

class FlashcardViewModel(private val repo: FlashcardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Setup())
    val uiState: StateFlow<UiState> = _uiState

    init {
        loadSetupData()
    }

    fun loadSetupData() {
        viewModelScope.launch {
            _uiState.value = (_uiState.value as? UiState.Setup ?: UiState.Setup()).copy(loading = true, error = null)
            try {
                val summaries = repo.getChapterSummaries()
                val (total, mastered) = repo.getStats()
                _uiState.value = UiState.Setup(summaries, total, mastered, loading = false)
            } catch (e: Exception) {
                _uiState.value = UiState.Setup(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    /** chapter=null মানে সব chapter মিলিয়ে। mode=PRACTICE (শুধু due card) বা REVIEW (সব card)। */
    fun startSession(chapter: Int?, mode: SessionMode) {
        viewModelScope.launch {
            val current = _uiState.value as? UiState.Setup ?: UiState.Setup()
            _uiState.value = current.copy(loading = true)
            try {
                val cards = if (mode == SessionMode.PRACTICE) {
                    repo.getDueCards(chapter)
                } else {
                    repo.getAllCardsForChapter(chapter)
                }
                _uiState.value = if (cards.isEmpty()) {
                    UiState.Finished(0, 0)
                } else {
                    UiState.Practicing(cards, 0, false, 0, 0)
                }
            } catch (e: Exception) {
                _uiState.value = current.copy(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    /** সরাসরি একটা নির্দিষ্ট word list (যেমন category-group) দিয়ে practice session শুরু করা। */
    fun startSessionWithItems(items: List<FlashcardItem>) {
        _uiState.value = if (items.isEmpty()) {
            UiState.Finished(0, 0)
        } else {
            UiState.Practicing(items.shuffled(), 0, false, 0, 0)
        }
    }

    fun revealAnswer() {
        val current = _uiState.value as? UiState.Practicing ?: return
        _uiState.value = current.copy(showAnswer = true)
    }

    /** Swipe করে rating না দিয়েই পরের card এ চলে যাওয়া (progress/box বদলাবে না)। */
    fun skipCard() {
        val current = _uiState.value as? UiState.Practicing ?: return
        val newIndex = current.index + 1
        _uiState.value = if (newIndex >= current.queue.size) {
            UiState.Finished(current.correctCount, current.wrongCount)
        } else {
            current.copy(index = newIndex, showAnswer = false)
        }
    }

    /** বাম দিকে swipe করে আগের card এ ফিরে যাওয়া (প্রথম card এ থাকলে কিছু হবে না)। */
    fun previousCard() {
        val current = _uiState.value as? UiState.Practicing ?: return
        if (current.index <= 0) return
        _uiState.value = current.copy(index = current.index - 1, showAnswer = false)
    }

    fun answer(rating: Rating) {
        val current = _uiState.value as? UiState.Practicing ?: return
        val card = current.queue[current.index]
        val correct = rating != Rating.HARD
        viewModelScope.launch {
            try {
                repo.updateProgress(card, rating)
            } catch (_: Exception) {
                // network সমস্যা হলেও local ভাবে পরের card এ চলে যাবে, পরের session এ আবার sync হবে
            }
            val newIndex = current.index + 1
            val newCorrect = current.correctCount + if (correct) 1 else 0
            val newWrong = current.wrongCount + if (!correct) 1 else 0
            _uiState.value = if (newIndex >= current.queue.size) {
                UiState.Finished(newCorrect, newWrong)
            } else {
                current.copy(index = newIndex, showAnswer = false, correctCount = newCorrect, wrongCount = newWrong)
            }
        }
    }

    fun backToSetup() {
        loadSetupData()
    }
}

class FlashcardViewModelFactory(private val repo: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FlashcardViewModel(repo) as T
    }
}
