package com.jubaer.koreanflashcards

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VocabWordEntity::class, ProgressEntity::class, DialogueChapterEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun progressDao(): ProgressDao
    abstract fun dialogueChapterDao(): DialogueChapterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "korean_app_local_db"
                )
                    // Note: version 1 → 2 (dialogue_chapters টেবিল নতুন যোগ হলো)।
                    // fallbackToDestructiveMigration মানে version বাড়লে পুরনো local DB
                    // মুছে নতুন করে বানাবে। vocab/progress ক্যাশ যেহেতু server থেকে আবার
                    // sync হয়ে যায়, সমস্যা নেই — কিন্তু ভবিষ্যতে dialogue_chapters এর
                    // schema বদলালে (version 3+) আগের সেভ করা chapter গুলোও মুছে যাবে,
                    // তাই পরে proper Migration লিখে দেওয়াই ভালো হবে।
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
