package com.jubaer.koreanflashcards

import org.json.JSONArray
import org.json.JSONObject

data class DialogueTurn(
    val speaker: String,
    val korean: String,
    val bangla: String
)

data class ExamQuestion(
    val number: Int = 0,
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val underlined: List<String> = emptyList(),
    val bangla: String = ""
)

data class DialogueParseResult(
    val turns: List<DialogueTurn>,
    val glossary: Map<String, String>,
    val questions: List<ExamQuestion> = emptyList()
)

// ========================================================================
// চ্যাপ্টার আকারে সেভ করার জন্য ডোমেইন মডেল।
// প্রতিটা chapter এ ৩টা অংশ: কথপোকথন ১, কথপোকথন ২, তথ্য/সংস্কৃতি — যেগুলো
// একবার AI দিয়ে প্রসেস করে local Room DB তে সেভ হয়ে যায়, তাই পরে internet
// ছাড়াই (offline) পড়া যায়।
// ========================================================================

enum class ChapterSection(val label: String, val emoji: String) {
    DIALOGUE_1("কথপোকথন ১", "🗣️"),
    DIALOGUE_2("কথপোকথন ২", "🗣️"),
    READING("읽기 / রিডিং", "📖"),
    LISTENING("듣기 / লিসেনিং", "🎧"),
    CULTURE("তথ্য / সংস্কৃতি", "🏮")
}

data class ChapterSectionContent(
    val turns: List<DialogueTurn> = emptyList(),
    val glossary: Map<String, String> = emptyMap(),
    val questions: List<ExamQuestion> = emptyList()
) {
    val isEmpty: Boolean get() = turns.isEmpty() && questions.isEmpty()
}

data class DialogueChapter(
    val id: Long = 0,
    val chapterName: String,
    val dialogue1: ChapterSectionContent = ChapterSectionContent(),
    val dialogue2: ChapterSectionContent = ChapterSectionContent(),
    val reading: ChapterSectionContent = ChapterSectionContent(),
    val listening: ChapterSectionContent = ChapterSectionContent(),
    val culture: ChapterSectionContent = ChapterSectionContent(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun content(section: ChapterSection): ChapterSectionContent = when (section) {
        ChapterSection.DIALOGUE_1 -> dialogue1
        ChapterSection.DIALOGUE_2 -> dialogue2
        ChapterSection.READING -> reading
        ChapterSection.LISTENING -> listening
        ChapterSection.CULTURE -> culture
    }

    fun withContent(section: ChapterSection, content: ChapterSectionContent): DialogueChapter = when (section) {
        ChapterSection.DIALOGUE_1 -> copy(dialogue1 = content)
        ChapterSection.DIALOGUE_2 -> copy(dialogue2 = content)
        ChapterSection.READING -> copy(reading = content)
        ChapterSection.LISTENING -> copy(listening = content)
        ChapterSection.CULTURE -> copy(culture = content)
    }

    val filledSectionCount: Int
        get() = listOf(dialogue1, dialogue2, reading, listening, culture).count { !it.isEmpty }
}

/**
 * turns / glossary কে JSON String এ (এবং ফিরিয়ে) কনভার্ট করার হেল্পার — Room এ
 * TEXT কলাম হিসেবে সেভ করার জন্য। org.json ব্যবহার করা হয়েছে (DialogueAiHelper.kt
 * তেও একই লাইব্রেরি ব্যবহার হয়), তাই নতুন কোনো dependency লাগেনি।
 */
object ChapterJson {
    fun turnsToJson(turns: List<DialogueTurn>): String {
        val arr = JSONArray()
        turns.forEach { t ->
            val o = JSONObject()
            o.put("speaker", t.speaker)
            o.put("korean", t.korean)
            o.put("bangla", t.bangla)
            arr.put(o)
        }
        return arr.toString()
    }

    fun turnsFromJson(json: String): List<DialogueTurn> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DialogueTurn(
                    speaker = o.optString("speaker", ""),
                    korean = o.optString("korean", ""),
                    bangla = o.optString("bangla", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun questionsToJson(questions: List<ExamQuestion>): String {
        val arr = JSONArray()
        questions.forEach { q ->
            val o = JSONObject()
            o.put("number", q.number)
            o.put("question", q.question)
            o.put("correctAnswer", q.correctAnswer)
            o.put("bangla", q.bangla)
            o.put("options", JSONArray().apply { q.options.forEach { put(it) } })
            o.put("underlined", JSONArray().apply { q.underlined.forEach { put(it) } })
            arr.put(o)
        }
        return arr.toString()
    }

    fun questionsFromJson(json: String): List<ExamQuestion> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val opts = o.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                val under = o.optJSONArray("underlined")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                ExamQuestion(
                    number = o.optInt("number", i + 1),
                    question = o.optString("question", ""),
                    options = opts,
                    correctAnswer = o.optString("correctAnswer", ""),
                    underlined = under,
                    bangla = o.optString("bangla", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun glossaryToJson(glossary: Map<String, String>): String {
        val o = JSONObject()
        glossary.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun glossaryFromJson(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val o = JSONObject(json)
            val map = mutableMapOf<String, String>()
            o.keys().forEach { k -> map[k] = o.optString(k, "") }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

fun DialogueChapterEntity.toDomain(): DialogueChapter = DialogueChapter(
    id = id,
    chapterName = chapterName,
    dialogue1 = ChapterSectionContent(
        ChapterJson.turnsFromJson(dialogue1Turns),
        ChapterJson.glossaryFromJson(dialogue1Glossary),
        emptyList()
    ),
    dialogue2 = ChapterSectionContent(
        ChapterJson.turnsFromJson(dialogue2Turns),
        ChapterJson.glossaryFromJson(dialogue2Glossary),
        emptyList()
    ),
    reading = ChapterSectionContent(
        ChapterJson.turnsFromJson(readingTurns),
        ChapterJson.glossaryFromJson(readingGlossary),
        ChapterJson.questionsFromJson(readingQuestions)
    ),
    listening = ChapterSectionContent(
        ChapterJson.turnsFromJson(listeningTurns),
        ChapterJson.glossaryFromJson(listeningGlossary),
        ChapterJson.questionsFromJson(listeningQuestions)
    ),
    culture = ChapterSectionContent(
        ChapterJson.turnsFromJson(cultureTurns),
        ChapterJson.glossaryFromJson(cultureGlossary),
        emptyList()
    ),
    createdAt = createdAt
)

fun DialogueChapter.toEntity(): DialogueChapterEntity = DialogueChapterEntity(
    id = id,
    chapterName = chapterName,
    dialogue1Turns = ChapterJson.turnsToJson(dialogue1.turns),
    dialogue1Glossary = ChapterJson.glossaryToJson(dialogue1.glossary),
    dialogue2Turns = ChapterJson.turnsToJson(dialogue2.turns),
    dialogue2Glossary = ChapterJson.glossaryToJson(dialogue2.glossary),
    readingTurns = ChapterJson.turnsToJson(reading.turns),
    readingGlossary = ChapterJson.glossaryToJson(reading.glossary),
    readingQuestions = ChapterJson.questionsToJson(reading.questions),
    listeningTurns = ChapterJson.turnsToJson(listening.turns),
    listeningGlossary = ChapterJson.glossaryToJson(listening.glossary),
    listeningQuestions = ChapterJson.questionsToJson(listening.questions),
    cultureTurns = ChapterJson.turnsToJson(culture.turns),
    cultureGlossary = ChapterJson.glossaryToJson(culture.glossary),
    createdAt = createdAt
)
