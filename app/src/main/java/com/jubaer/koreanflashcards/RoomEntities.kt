package com.jubaer.koreanflashcards

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vocab_words_cache")
data class VocabWordEntity(
    @PrimaryKey val korean_word: String,
    val bangla_meaning: String,
    val chapter_number: Int
)

@Entity(tableName = "flashcard_progress_cache")
data class ProgressEntity(
    @PrimaryKey val korean_word: String,
    val box_level: Int,
    val next_review_date: String,
    val times_reviewed: Int,
    val times_correct: Int
)
