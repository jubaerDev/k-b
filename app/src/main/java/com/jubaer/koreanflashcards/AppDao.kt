package com.jubaer.koreanflashcards

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VocabDao {
    @Query("SELECT * FROM vocab_words_cache")
    suspend fun getAll(): List<VocabWordEntity>

    @Query("SELECT DISTINCT chapter_number FROM vocab_words_cache ORDER BY chapter_number")
    suspend fun getChapters(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<VocabWordEntity>)

    @Query("SELECT * FROM vocab_words_cache WHERE korean_word = :word LIMIT 1")
    suspend fun getByKoreanWord(word: String): VocabWordEntity?

    @Query("DELETE FROM vocab_words_cache")
    suspend fun clearAll()
}

@Dao
interface DialogueWordMeaningDao {
    @Query("SELECT * FROM dialogue_word_meanings WHERE koreanWord = :word LIMIT 1")
    suspend fun get(word: String): DialogueWordMeaningEntity?

    @androidx.room.Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DialogueWordMeaningEntity)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM flashcard_progress_cache")
    suspend fun getAll(): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ProgressEntity>)

    @Query("DELETE FROM flashcard_progress_cache")
    suspend fun clearAll()
}

/** Dialogue Reader এর "চ্যাপ্টার আকারে সেভ করা" লাইব্রেরির জন্য — সম্পূর্ণ local, offline। */
@Dao
interface DialogueChapterDao {
    @Query("SELECT * FROM dialogue_chapters ORDER BY createdAt DESC")
    suspend fun getAll(): List<DialogueChapterEntity>

    @Query("SELECT * FROM dialogue_chapters WHERE id = :id")
    suspend fun getById(id: Long): DialogueChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: DialogueChapterEntity): Long

    @Query("DELETE FROM dialogue_chapters WHERE id = :id")
    suspend fun deleteById(id: Long)
}
