package com.jubaer.koreanflashcards

data class VocabWord(
    val korean_word: String,
    val bangla_meaning: String,
    val chapter_number: Int
)

data class ProgressRow(
    val korean_word: String,
    val box_level: Int,
    val next_review_date: String,
    val times_reviewed: Int,
    val times_correct: Int
)

data class ProgressUpsert(
    val korean_word: String,
    val chapter_number: Int,
    val box_level: Int,
    val next_review_date: String,
    val last_reviewed: String,
    val times_reviewed: Int,
    val times_correct: Int
)

// UI তে ব্যবহারের জন্য (vocab_words ও flashcard_progress এর তথ্য একসাথে করা)
data class FlashcardItem(
    val korean: String,
    val bangla: String,
    val chapter: Int,
    val boxLevel: Int,
    val timesReviewed: Int,
    val timesCorrect: Int
)

// ---------- Upload Chapter এর জন্য ----------

data class RawWordRow(
    val chapter_number: Int,
    val korean_word: String,
    val bangla_meaning: String?,
    val id: Long
)

data class RawWordInsert(
    val chapter_number: Int,
    val korean_word: String,
    val bangla_meaning: String?
)

data class VocabWordInsert(
    val korean_word: String,
    val bangla_meaning: String?,
    val chapter_number: Int,
    val date_added: String
)

data class ChapterLogInsert(
    val chapter_number: Int,
    val total_words_in_file: Int,
    val unique_new_words: Int,
    val upload_date: String
)

data class IdOnly(val id: Long)

// ---------- Question Bank এর জন্য ----------

data class CategoryOnly(val category: String)

data class QuestionBankRow(
    val id: Long,
    val question_text: String,
    val option1: String,
    val option2: String,
    val option3: String,
    val option4: String,
    val correct_answer: Int,
    val explanation: String?
)

// ---------- Book View এর জন্য ----------

data class ChapterOnly(val chapter_number: Int)

data class BookParagraphRow(
    val id: Long,
    val chapter_title: String?,
    val heading: String?,
    val paragraph_label: String?,
    val korean_original: String,
    val annotated_text: String
)

// ---------- Category Vocabulary এর জন্য ----------

data class EnrichmentRow(
    val korean_word: String,
    val category: String?,
    val synonyms: String?,
    val antonyms: String?,
    val bangla_synonyms: String?,
    val bangla_antonyms: String?
)

data class CategoryWordItem(
    val korean: String,
    val bangla: String,
    val synonyms: String?,
    val antonyms: String?,
    val banglaSynonyms: String?,
    val banglaAntonyms: String?
)
