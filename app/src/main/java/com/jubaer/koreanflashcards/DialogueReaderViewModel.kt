package com.jubaer.koreanflashcards

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class DialogueScreenMode { LIBRARY, EDITOR, VIEWER }

data class SectionEditState(
    val inputText: String = "",
    val content: ChapterSectionContent = ChapterSectionContent(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val isDone: Boolean get() = content.turns.isNotEmpty()
}

data class DialogueUiState(
    val mode: DialogueScreenMode = DialogueScreenMode.LIBRARY,

    // লাইব্রেরি (সেভ করা সব চ্যাপ্টার)
    val library: List<DialogueChapter> = emptyList(),
    val libraryLoading: Boolean = false,
    val libraryError: String? = null,

    // এডিটর (নতুন চ্যাপ্টার তৈরি / পুরনোটা এডিট)
    val editingChapterId: Long? = null,
    val chapterName: String = "",
    val activeSection: ChapterSection = ChapterSection.DIALOGUE_1,
    val sections: Map<ChapterSection, SectionEditState> = ChapterSection.values()
        .associateWith { SectionEditState() },
    val saving: Boolean = false,
    val saveError: String? = null,

    // ভিউয়ার (সেভ করা চ্যাপ্টার অফলাইনে পড়া — কোনো AI call লাগে না)
    val viewingChapter: DialogueChapter? = null,
    val viewingSection: ChapterSection = ChapterSection.DIALOGUE_1,
    val selectedWord: String? = null
)

class DialogueReaderViewModel(private val repo: FlashcardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DialogueUiState())
    val uiState: StateFlow<DialogueUiState> = _uiState

    init {
        loadLibrary()
    }

    // ---------- লাইব্রেরি ----------

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(libraryLoading = true, libraryError = null)
            try {
                val chapters = repo.getDialogueChapters()
                _uiState.value = _uiState.value.copy(library = chapters, libraryLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    libraryLoading = false,
                    libraryError = e.message ?: "চ্যাপ্টার লোড করা যায়নি"
                )
            }
        }
    }

    fun deleteChapter(id: Long) {
        viewModelScope.launch {
            try {
                repo.deleteDialogueChapter(id)
                loadLibrary()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(libraryError = e.message ?: "ডিলিট করা যায়নি")
            }
        }
    }

    // ---------- এডিটর ----------

    fun startNewChapter() {
        _uiState.value = _uiState.value.copy(
            mode = DialogueScreenMode.EDITOR,
            editingChapterId = null,
            chapterName = "",
            activeSection = ChapterSection.DIALOGUE_1,
            sections = ChapterSection.values().associateWith { SectionEditState() },
            saveError = null
        )
    }

    fun editChapter(chapter: DialogueChapter) {
        val sections = mapOf(
            ChapterSection.DIALOGUE_1 to SectionEditState(content = chapter.dialogue1),
            ChapterSection.DIALOGUE_2 to SectionEditState(content = chapter.dialogue2),
            ChapterSection.READING to SectionEditState(content = chapter.reading),
            ChapterSection.LISTENING to SectionEditState(content = chapter.listening),
            ChapterSection.CULTURE to SectionEditState(content = chapter.culture)
        )
        _uiState.value = _uiState.value.copy(
            mode = DialogueScreenMode.EDITOR,
            editingChapterId = chapter.id,
            chapterName = chapter.chapterName,
            activeSection = ChapterSection.DIALOGUE_1,
            sections = sections,
            saveError = null
        )
    }

    fun cancelEditor() {
        _uiState.value = _uiState.value.copy(mode = DialogueScreenMode.LIBRARY, saveError = null)
    }

    fun setChapterName(name: String) {
        _uiState.value = _uiState.value.copy(chapterName = name)
    }

    fun setActiveSection(section: ChapterSection) {
        _uiState.value = _uiState.value.copy(activeSection = section)
    }

    fun updateSectionInputText(section: ChapterSection, text: String) {
        val current = _uiState.value.sections.toMutableMap()
        current[section] = (current[section] ?: SectionEditState()).copy(inputText = text)
        _uiState.value = _uiState.value.copy(sections = current)
    }

    /** নির্বাচিত section এর input text AI দিয়ে প্রসেস করে (dialogue হলে turn-ভাগ, culture হলে paragraph-ভাগ)। */
    fun processActiveSectionText() {
        val section = _uiState.value.activeSection
        val text = _uiState.value.sections[section]?.inputText ?: return
        if (text.isBlank()) return
        processSection(section) { DialogueAiHelper.parseSectionFromText(section, text) }
    }

    /** নির্বাচিত section এর screenshot AI দিয়ে প্রসেস করে। */
    fun processActiveSectionImage(bitmap: Bitmap) {
        val section = _uiState.value.activeSection
        processSection(section) { DialogueAiHelper.parseSectionFromImage(section, bitmap) }
    }

    private fun processSection(section: ChapterSection, call: suspend () -> DialogueParseResult) {
        viewModelScope.launch {
            setSectionState(section) { it.copy(loading = true, error = null) }
            try {
                val result = call()
                setSectionState(section) {
                    it.copy(
                        loading = false,
                        content = ChapterSectionContent(result.turns, result.glossary, result.questions)
                    )
                }
            } catch (e: Exception) {
                setSectionState(section) { it.copy(loading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    /** কোনো section এ ভুল হলে আবার নতুন করে input দেওয়ার জন্য reset করা। */
    fun clearSection(section: ChapterSection) {
        setSectionState(section) { SectionEditState() }
    }

    private fun setSectionState(section: ChapterSection, update: (SectionEditState) -> SectionEditState) {
        val current = _uiState.value.sections.toMutableMap()
        current[section] = update(current[section] ?: SectionEditState())
        _uiState.value = _uiState.value.copy(sections = current)
    }

    fun saveChapter() {
        val state = _uiState.value
        val name = state.chapterName.trim()
        if (name.isEmpty()) {
            _uiState.value = state.copy(saveError = "চ্যাপ্টারের একটা নাম দাও")
            return
        }
        val hasAnyContent = state.sections.values.any { it.isDone }
        if (!hasAnyContent) {
            _uiState.value = state.copy(saveError = "অন্তত একটা অংশ প্রসেস করে যোগ করো")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, saveError = null)
            try {
                val chapter = DialogueChapter(
                    id = state.editingChapterId ?: 0,
                    chapterName = name,
                    dialogue1 = state.sections[ChapterSection.DIALOGUE_1]?.content ?: ChapterSectionContent(),
                    dialogue2 = state.sections[ChapterSection.DIALOGUE_2]?.content ?: ChapterSectionContent(),
                    reading = state.sections[ChapterSection.READING]?.content ?: ChapterSectionContent(),
                    listening = state.sections[ChapterSection.LISTENING]?.content ?: ChapterSectionContent(),
                    culture = state.sections[ChapterSection.CULTURE]?.content ?: ChapterSectionContent()
                )
                repo.saveDialogueChapter(chapter)
                _uiState.value = _uiState.value.copy(saving = false, mode = DialogueScreenMode.LIBRARY)
                loadLibrary()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(saving = false, saveError = e.message ?: "সেভ করা যায়নি")
            }
        }
    }

    // ---------- ভিউয়ার (অফলাইন পড়া) ----------

    fun openChapterForReading(chapter: DialogueChapter) {
        _uiState.value = _uiState.value.copy(
            mode = DialogueScreenMode.VIEWER,
            viewingChapter = chapter,
            viewingSection = ChapterSection.DIALOGUE_1,
            selectedWord = null
        )
    }

    fun setViewingSection(section: ChapterSection) {
        _uiState.value = _uiState.value.copy(viewingSection = section, selectedWord = null)
    }

    fun selectWord(word: String?) {
        _uiState.value = _uiState.value.copy(selectedWord = word)
    }

    fun backToLibrary() {
        _uiState.value = _uiState.value.copy(
            mode = DialogueScreenMode.LIBRARY,
            viewingChapter = null,
            selectedWord = null,
            saveError = null
        )
    }
}

class DialogueReaderViewModelFactory(private val repo: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DialogueReaderViewModel(repo) as T
    }
}
