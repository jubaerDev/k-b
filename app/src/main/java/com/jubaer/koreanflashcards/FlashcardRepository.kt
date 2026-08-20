package com.jubaer.koreanflashcards

/**
 * এখন থেকে সব READ (Flashcard due-card, Vocabulary browse, stats) হয় local
 * Room database থেকে — তাই তাৎক্ষণিক (internet lag নেই)। WRITE (progress
 * update, chapter upload) সরাসরি Supabase এ যায় (source of truth), তারপর
 * local Room ও আপডেট হয়ে যায় যাতে UI সাথে সাথে ঠিক দেখায়।
 *
 * syncFromServer() — app চালু হওয়ার সময় (বা manual "Sync" চাপলে) পুরো
 * Supabase data নতুন করে download করে Room এ replace করে।
 */

enum class Rating { HARD, GOOD, EASY }

enum class SessionMode { PRACTICE, REVIEW }

data class ChapterSummary(val chapter: Int, val total: Int, val memorized: Int)

class FlashcardRepository(private val api: SupabaseApi, private val db: AppDatabase) {

    companion object {
        val LEITNER_INTERVALS = mapOf(1 to 0, 2 to 1, 3 to 3, 4 to 7, 5 to 14, 6 to 30)
        const val MAX_BOX = 6
    }

    private suspend fun <T> fetchAllPaged(pageSize: Int = 1000, fetchPage: suspend (String) -> List<T>): List<T> {
        val all = mutableListOf<T>()
        var start = 0
        while (true) {
            val range = "$start-${start + pageSize - 1}"
            val batch = fetchPage(range)
            all.addAll(batch)
            if (batch.size < pageSize) break
            start += pageSize
        }
        return all
    }

    // ---------- Sync: Supabase → Room (local cache) ----------

    suspend fun syncFromServer() {
        val vocab = fetchAllPaged { range -> api.getVocabWords(range = range) }
        val progress = fetchAllPaged { range -> api.getProgress(range = range) }

        db.vocabDao().clearAll()
        db.vocabDao().insertAll(
            vocab.map { VocabWordEntity(it.korean_word, it.bangla_meaning, it.chapter_number) }
        )

        db.progressDao().clearAll()
        db.progressDao().insertAll(
            progress.map {
                ProgressEntity(it.korean_word, it.box_level, it.next_review_date, it.times_reviewed, it.times_correct)
            }
        )
    }

    // ---------- READ: এখন সব Room থেকে (দ্রুত) ----------

    suspend fun getAllChapters(): List<Int> = db.vocabDao().getChapters()

    suspend fun getStats(): Pair<Int, Int> {
        val progress = db.progressDao().getAll()
        val total = progress.size
        val mastered = progress.count { it.box_level >= MAX_BOX }
        return Pair(total, mastered)
    }

    /** প্রতিটা chapter এর জন্য মোট word ও কতগুলো "Mastered" (memorized) সেটার তালিকা। */
    suspend fun getChapterSummaries(): List<ChapterSummary> {
        val vocab = db.vocabDao().getAll()
        val progressMap = db.progressDao().getAll().associateBy { it.korean_word }
        return vocab.groupBy { it.chapter_number }
            .map { (chapter, words) ->
                val total = words.size
                val memorized = words.count { w -> (progressMap[w.korean_word]?.box_level ?: 1) >= MAX_BOX }
                ChapterSummary(chapter, total, memorized)
            }
            .sortedBy { it.chapter }
    }

    suspend fun getDueCards(chapterFilter: Int?): List<FlashcardItem> {
        val vocabAll = db.vocabDao().getAll()
        val vocab = if (chapterFilter != null) vocabAll.filter { it.chapter_number == chapterFilter } else vocabAll

        val progressMap = db.progressDao().getAll().associateBy { it.korean_word }
        val today = DateUtils.today()

        val due = vocab.mapNotNull { w ->
            val p = progressMap[w.korean_word]
            when {
                p == null -> FlashcardItem(w.korean_word, w.bangla_meaning, w.chapter_number, 1, 0, 0)
                p.next_review_date <= today -> FlashcardItem(
                    w.korean_word, w.bangla_meaning, w.chapter_number,
                    p.box_level, p.times_reviewed, p.times_correct
                )
                else -> null
            }
        }
        return due.shuffled()
    }

    /** "Review" মোড — due-date না দেখে ওই chapter এর সব word নিয়ে session বানায়। */
    suspend fun getAllCardsForChapter(chapterFilter: Int?): List<FlashcardItem> {
        val vocabAll = db.vocabDao().getAll()
        val vocab = if (chapterFilter != null) vocabAll.filter { it.chapter_number == chapterFilter } else vocabAll
        val progressMap = db.progressDao().getAll().associateBy { it.korean_word }

        val all = vocab.map { w ->
            val p = progressMap[w.korean_word]
            FlashcardItem(
                w.korean_word, w.bangla_meaning, w.chapter_number,
                p?.box_level ?: 1, p?.times_reviewed ?: 0, p?.times_correct ?: 0
            )
        }
        return all.shuffled()
    }

    suspend fun getAllVocabWords(): List<VocabWordEntity> = db.vocabDao().getAll()

    // ---------- WRITE: সরাসরি Supabase এ, তারপর Room ও আপডেট ----------

    /**
     * Anki-স্টাইল ৩-ধাপ rating:
     * HARD → box আবার ১ এ ফিরে যায় (কাল আবার দেখাবে)
     * GOOD → এক ধাপ এগোয় (স্বাভাবিক spaced repetition)
     * EASY → দুই ধাপ এগোয় (আরও কম দেখাবে, কারণ ভালো জানা)
     */
    suspend fun updateProgress(item: FlashcardItem, rating: Rating) {
        val newBox = when (rating) {
            Rating.HARD -> 1
            Rating.GOOD -> minOf(item.boxLevel + 1, MAX_BOX)
            Rating.EASY -> minOf(item.boxLevel + 2, MAX_BOX)
        }
        val correct = rating != Rating.HARD
        val nextReview = DateUtils.plusDays(LEITNER_INTERVALS[newBox] ?: 0)
        val newTimesReviewed = item.timesReviewed + 1
        val newTimesCorrect = item.timesCorrect + if (correct) 1 else 0

        val body = listOf(
            ProgressUpsert(
                korean_word = item.korean,
                chapter_number = item.chapter,
                box_level = newBox,
                next_review_date = nextReview,
                last_reviewed = DateUtils.nowIso(),
                times_reviewed = newTimesReviewed,
                times_correct = newTimesCorrect
            )
        )
        api.upsertProgress(body)

        // Local Room ও সাথে সাথে আপডেট (পরের বার fetch এ instant দেখাবে)
        db.progressDao().insertAll(
            listOf(ProgressEntity(item.korean, newBox, nextReview, newTimesReviewed, newTimesCorrect))
        )
    }

    // ---------- Upload Chapter / Rebuild (সরাসরি Supabase, এরপর syncFromServer() কল করতে হবে) ----------

    suspend fun chapterExists(chapterNumber: Int): Boolean {
        val rows = api.checkChapterRawExists(chapterFilter = "eq.$chapterNumber")
        return rows.isNotEmpty()
    }

    suspend fun saveRawChapter(chapterNumber: Int, pairs: List<Pair<String, String>>, overwrite: Boolean) {
        if (overwrite) {
            api.deleteRawWordsForChapter(chapterFilter = "eq.$chapterNumber")
        }
        val cleaned = pairs.mapNotNull { (k, b) ->
            val kt = k.trim()
            if (kt.isEmpty() || kt.equals("nan", ignoreCase = true)) null
            else RawWordInsert(chapterNumber, kt, b.trim())
        }
        if (cleaned.isNotEmpty()) {
            cleaned.chunked(500).forEach { chunk -> api.insertRawWords(chunk) }
        }
    }

    suspend fun getWordsForChapterFinal(chapterNumber: Int): List<VocabWord> {
        return api.getVocabWordsByChapter(chapterFilter = "eq.$chapterNumber")
    }

    suspend fun rebuildDatabase() {
        val raw = fetchAllPaged { range -> api.getRawChapterWords(range = range) }
        val byChapter = raw.groupBy { it.chapter_number }.toSortedMap()

        val seenWords = mutableSetOf<String>()
        val vocabPayload = mutableListOf<VocabWordInsert>()
        val logPayload = mutableListOf<ChapterLogInsert>()
        val now = DateUtils.nowIso()

        for ((chapterNumber, rowsUnsorted) in byChapter) {
            val rows = rowsUnsorted.sortedBy { it.id }
            val totalInFile = rows.size

            val localSeen = mutableSetOf<String>()
            val newThisChapter = mutableListOf<RawWordRow>()
            for (r in rows) {
                val k = r.korean_word.trim()
                if (k.isEmpty() || localSeen.contains(k)) continue
                localSeen.add(k)
                if (seenWords.contains(k)) continue
                seenWords.add(k)
                newThisChapter.add(r)
            }

            for (r in newThisChapter) {
                vocabPayload.add(VocabWordInsert(r.korean_word.trim(), r.bangla_meaning, chapterNumber, now))
            }
            logPayload.add(ChapterLogInsert(chapterNumber, totalInFile, newThisChapter.size, now))
        }

        api.deleteAllVocabWords()
        api.deleteAllChaptersLog()

        vocabPayload.chunked(500).forEach { chunk -> api.insertVocabWords(chunk) }
        if (logPayload.isNotEmpty()) {
            logPayload.chunked(500).forEach { chunk -> api.insertChaptersLog(chunk) }
        }
    }

    // ---------- Question Bank ----------

    suspend fun getQuestionCategories(): List<String> {
        val rows = api.getQuestionCategories()
        return rows.map { it.category }.distinct().sorted()
    }

    suspend fun getQuestionsByCategory(category: String): List<QuestionBankRow> {
        return api.getQuestionsByCategory(categoryFilter = "eq.$category")
    }

    // ---------- Book View ----------

    suspend fun getBookChapters(): List<Int> {
        val rows = api.getBookChapters()
        return rows.map { it.chapter_number }.distinct().sorted()
    }

    suspend fun getBookParagraphs(chapter: Int): List<BookParagraphRow> {
        return api.getBookParagraphsByChapter(chapterFilter = "eq.$chapter")
    }

    // ---------- Category Vocabulary ----------

    suspend fun getVocabCategories(): List<String> {
        val rows = api.getEnrichmentCategories()
        return rows.map { it.category }.distinct().sorted()
    }

    suspend fun getWordsByVocabCategory(category: String): List<CategoryWordItem> {
        val enriched = api.getEnrichmentByCategory(categoryFilter = "eq.$category")
        val vocabMap = db.vocabDao().getAll().associateBy { it.korean_word }
        return enriched
            .map { e ->
                CategoryWordItem(
                    korean = e.korean_word,
                    bangla = vocabMap[e.korean_word]?.bangla_meaning ?: "",
                    synonyms = e.synonyms,
                    antonyms = e.antonyms,
                    banglaSynonyms = e.bangla_synonyms,
                    banglaAntonyms = e.bangla_antonyms
                )
            }
            .sortedBy { it.korean }
    }

    /**
     * একটা chapter এর সব word কে (আগে থেকে categorize করা category অনুযায়ী) গ্রুপ করে
     * ফেরত দেয় — LinkedHashMap ব্যবহার করা হয়েছে যাতে category-র ক্রম consistent থাকে।
     * প্রতিটা group এর word গুলো FlashcardItem আকারে থাকে, তাই সরাসরি practice শুরু করা যায়।
     */
    suspend fun getChapterWordsGroupedByCategory(chapter: Int): Map<String, List<FlashcardItem>> {
        val words = db.vocabDao().getAll().filter { it.chapter_number == chapter }
        val enrichmentAll = fetchAllPaged { range -> api.getAllEnrichment(range = range) }
        val catMap = enrichmentAll.associate { it.korean_word to (it.category ?: "Other") }
        val progressMap = db.progressDao().getAll().associateBy { it.korean_word }

        val grouped = words.groupBy { catMap[it.korean_word] ?: "Uncategorized" }
        return grouped
            .toSortedMap()
            .mapValues { (_, wordList) ->
                wordList.map { w ->
                    val p = progressMap[w.korean_word]
                    FlashcardItem(
                        w.korean_word, w.bangla_meaning, w.chapter_number,
                        p?.box_level ?: 1, p?.times_reviewed ?: 0, p?.times_correct ?: 0
                    )
                }
            }
    }

    /**
     * নির্দিষ্ট কিছু korean_word (যেমন Category Vocabulary screen থেকে ব্যবহারকারী
     * বেছে নেওয়া word) দিয়ে সরাসরি FlashcardItem list বানায়, practice শুরু করার জন্য।
     */
    suspend fun buildFlashcardItemsForWords(koreanWords: List<String>): List<FlashcardItem> {
        val vocabMap = db.vocabDao().getAll().associateBy { it.korean_word }
        val progressMap = db.progressDao().getAll().associateBy { it.korean_word }
        return koreanWords.mapNotNull { k ->
            val v = vocabMap[k] ?: return@mapNotNull null
            val p = progressMap[k]
            FlashcardItem(
                v.korean_word, v.bangla_meaning, v.chapter_number,
                p?.box_level ?: 1, p?.times_reviewed ?: 0, p?.times_correct ?: 0
            )
        }
    }

    // ---------- Custom Sets ----------

    suspend fun getCustomSets(): List<CustomSet> = api.getCustomSets()

    suspend fun createCustomSet(name: String): CustomSet {
        val result = api.insertCustomSet(CustomSetInsert(name))
        return result.first()
    }

    suspend fun deleteCustomSet(setId: Long) {
        api.deleteCustomSet(idFilter = "eq.$setId")
    }

    suspend fun getCustomSetWordItems(setId: Long): List<FlashcardItem> {
        val rows = api.getCustomSetWords(setIdFilter = "eq.$setId")
        return buildFlashcardItemsForWords(rows.map { it.korean_word })
    }

    suspend fun addWordsToCustomSet(setId: Long, koreanWords: List<String>) {
        if (koreanWords.isEmpty()) return
        val existing = api.getCustomSetWords(setIdFilter = "eq.$setId").map { it.korean_word }.toSet()
        val newOnes = koreanWords.filter { it !in existing }
        if (newOnes.isEmpty()) return
        val payload = newOnes.map { CustomSetWordInsert(setId, it) }
        api.insertCustomSetWords(payload)
    }

    suspend fun removeWordFromCustomSet(setId: Long, koreanWord: String) {
        api.deleteCustomSetWord(setIdFilter = "eq.$setId", koreanWordFilter = "eq.$koreanWord")
    }

    // ---------- Dialogue Chapters (Dialogue Reader এর offline লাইব্রেরি) ----------
    // এগুলো সম্পূর্ণ local — কোনো server call নেই, তাই internet ছাড়াও কাজ করে।

    suspend fun getDialogueChapters(): List<DialogueChapter> =
        db.dialogueChapterDao().getAll().map { it.toDomain() }

    suspend fun getDialogueChapter(id: Long): DialogueChapter? =
        db.dialogueChapterDao().getById(id)?.toDomain()

    suspend fun saveDialogueChapter(chapter: DialogueChapter): Long =
        db.dialogueChapterDao().upsert(chapter.toEntity())

    suspend fun deleteDialogueChapter(id: Long) {
        db.dialogueChapterDao().deleteById(id)
    }
}

