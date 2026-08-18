package com.jubaer.koreanflashcards

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VocabWordEntity::class, ProgressEntity::class, DialogueChapterEntity::class, DialogueWordMeaningEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun progressDao(): ProgressDao
    abstract fun dialogueChapterDao(): DialogueChapterDao
    abstract fun dialogueWordMeaningDao(): DialogueWordMeaningDao

    companion object {
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS dialogue_word_meanings (koreanWord TEXT NOT NULL, banglaMeaning TEXT NOT NULL, source TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(koreanWord))")
            }
        }

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
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
