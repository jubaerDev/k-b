package com.jubaer.koreanflashcards

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VocabWordEntity::class, ProgressEntity::class, DialogueChapterEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun progressDao(): ProgressDao
    abstract fun dialogueChapterDao(): DialogueChapterDao

    companion object {
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dialogue_chapters ADD COLUMN readingTurns TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dialogue_chapters ADD COLUMN readingGlossary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dialogue_chapters ADD COLUMN readingQuestions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dialogue_chapters ADD COLUMN listeningTurns TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dialogue_chapters ADD COLUMN listeningGlossary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dialogue_chapters ADD COLUMN listeningQuestions TEXT NOT NULL DEFAULT ''")
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
