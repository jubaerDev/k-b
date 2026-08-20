package com.jubaer.koreanflashcards

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject

/**
 * Dialogue/Reading/Listening screenshot parser.
 * Reading/Listening-এর screenshot থেকে প্রশ্ন, options এবং underline করা অংশ
 * আলাদা করে বের করে। Underline detection এখানে Gemini Vision-এর মাধ্যমে হয়;
 * তাই screenshot-এ যেটা সত্যিই underline করা আছে সেটার Korean phrase-গুলো
 * `underlined` array-তে আসে এবং পরে UI-তে আবার underline করা হয়।
 */
object DialogueAiHelper {

    private val CANDIDATE_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-3.1-flash-lite",
        "gemini-flash-latest"
    )

    private const val DIALOGUE_PROMPT = """
তুমি একজন Korean-Bangla ভাষা বিশেষজ্ঞ। ছবিতে Korean dialogue বা কথোপকথন থাকলে সেটি পড়ো।
প্রতিটি turn আলাদা করো এবং Korean বাক্য ও স্বাভাবিক Bangla অর্থ দাও। পুরো লেখার Korean
word/eojeol-এর সংক্ষিপ্ত বাংলা glossary বানাও।
শুধু JSON দাও:
{"turns":[{"speaker":"A","korean":"...","bangla":"..."}],"glossary":{"শব্দ":"অর্থ"},"questions":[]}
"""

    private const val PARAGRAPH_PROMPT = """
তুমি একজন Korean-Bangla ভাষা বিশেষজ্ঞ। ছবির Korean তথ্যভিত্তিক/সাংস্কৃতিক paragraph পড়ো।
বাক্যভাগ করে Korean ও Bangla অর্থ দাও এবং glossary বানাও।
শুধু JSON দাও:
{"turns":[{"speaker":"","korean":"...","bangla":"..."}],"glossary":{"শব্দ":"অর্থ"},"questions":[]}
"""

    private const val READING_PROMPT = """
তুমি Korean-Bangla EPS-TOPIK পরীক্ষার প্রশ্ন বিশ্লেষক। দেওয়া screenshot ভালোভাবে দেখো।
Reading অংশের সব প্রশ্ন, প্রশ্নের Korean text, প্রতিটি option এবং screenshot-এ সত্যিই যেসব
Korean শব্দ/phrase-এর নিচে underline আছে সেগুলো EXACT text হিসেবে শনাক্ত করো।
Underline নিজে থেকে বানাবে না; শুধু ছবিতে দৃশ্যমান underline-কে `underlined` array-তে দেবে।
প্রতিটি প্রশ্নের Bangla অর্থ দিতে পারো। উত্তর key ছবিতে না থাকলে correctAnswer খালি রাখবে।
যদি passage/reading text থাকে, সেটি turns-এ রাখবে।
শুধু JSON দাও:
{"turns":[{"speaker":"","korean":"...","bangla":"..."}],"glossary":{"শব্দ":"অর্থ"},"questions":[{"number":1,"question":"...","options":["...","..."],"correctAnswer":"","underlined":["..."],"bangla":"..."}]}
"""

    private const val LISTENING_PROMPT = """
তুমি Korean-Bangla EPS-TOPIK Listening পরীক্ষার প্রশ্ন বিশ্লেষক। দেওয়া screenshot ভালোভাবে দেখো।
Listening অংশের প্রশ্ন, option এবং নিচে থাকা 듣기지문/listening script থাকলে সেগুলো হুবহু Korean-এ
ধরো। Screenshot-এ সত্যিই যেসব Korean শব্দ/phrase-এর নিচে underline আছে সেগুলো EXACT text হিসেবে
`underlined` array-তে দাও। Underline নিজে থেকে বানাবে না। প্রতিটি question-এর Bangla অর্থ দিতে পারো।
উত্তর key ছবিতে না থাকলে correctAnswer খালি রাখবে।
শুধু JSON দাও:
{"turns":[{"speaker":"A","korean":"...","bangla":"..."}],"glossary":{"শব্দ":"অর্থ"},"questions":[{"number":1,"question":"...","options":["...","..."],"correctAnswer":"","underlined":["..."],"bangla":"..."}]}
"""

    private fun parseJsonResponse(text: String): DialogueParseResult {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
        }
        if (cleaned.endsWith("```")) cleaned = cleaned.removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1)

        val obj = JSONObject(cleaned)
        val turns = mutableListOf<DialogueTurn>()
        obj.optJSONArray("turns")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                turns += DialogueTurn(
                    speaker = t.optString("speaker", if (i % 2 == 0) "A" else "B"),
                    korean = t.optString("korean", ""),
                    bangla = t.optString("bangla", "")
                )
            }
        }

        val glossary = mutableMapOf<String, String>()
        obj.optJSONObject("glossary")?.let { g ->
            g.keys().forEach { k -> glossary[k] = g.optString(k, "") }
        }

        val questions = mutableListOf<ExamQuestion>()
        obj.optJSONArray("questions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val q = arr.getJSONObject(i)
                val options = q.optJSONArray("options")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                } ?: emptyList()
                val underlined = q.optJSONArray("underlined")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                questions += ExamQuestion(
                    number = q.optInt("number", i + 1),
                    question = q.optString("question", ""),
                    options = options,
                    correctAnswer = q.optString("correctAnswer", ""),
                    underlined = underlined,
                    bangla = q.optString("bangla", "")
                )
            }
        }
        return DialogueParseResult(turns, glossary, questions)
    }

    private suspend fun runForText(prompt: String, rawText: String): DialogueParseResult {
        var lastError: Exception? = null
        for (modelName in CANDIDATE_MODELS) {
            try {
                val model = GenerativeModel(modelName = modelName, apiKey = GeminiConfig.API_KEY)
                val response = model.generateContent("$prompt\n\nText:\n$rawText")
                return parseJsonResponse(response.text ?: "")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Gemini request failed")
    }

    private suspend fun runForImage(prompt: String, bitmap: Bitmap): DialogueParseResult {
        var lastError: Exception? = null
        for (modelName in CANDIDATE_MODELS) {
            try {
                val model = GenerativeModel(modelName = modelName, apiKey = GeminiConfig.API_KEY)
                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }
                val response = model.generateContent(inputContent)
                return parseJsonResponse(response.text ?: "")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Gemini image request failed")
    }

    suspend fun parseDialogueFromText(rawText: String) = runForText(DIALOGUE_PROMPT, rawText)
    suspend fun parseDialogueFromImage(bitmap: Bitmap) = runForImage(DIALOGUE_PROMPT, bitmap)
    suspend fun parseParagraphFromText(rawText: String) = runForText(PARAGRAPH_PROMPT, rawText)
    suspend fun parseParagraphFromImage(bitmap: Bitmap) = runForImage(PARAGRAPH_PROMPT, bitmap)
    suspend fun parseReadingFromText(rawText: String) = runForText(READING_PROMPT, rawText)
    suspend fun parseReadingFromImage(bitmap: Bitmap) = runForImage(READING_PROMPT, bitmap)
    suspend fun parseListeningFromText(rawText: String) = runForText(LISTENING_PROMPT, rawText)
    suspend fun parseListeningFromImage(bitmap: Bitmap) = runForImage(LISTENING_PROMPT, bitmap)

    suspend fun parseSectionFromText(section: ChapterSection, rawText: String): DialogueParseResult = when (section) {
        ChapterSection.CULTURE -> parseParagraphFromText(rawText)
        ChapterSection.READING -> parseReadingFromText(rawText)
        ChapterSection.LISTENING -> parseListeningFromText(rawText)
        else -> parseDialogueFromText(rawText)
    }

    suspend fun parseSectionFromImage(section: ChapterSection, bitmap: Bitmap): DialogueParseResult = when (section) {
        ChapterSection.CULTURE -> parseParagraphFromImage(bitmap)
        ChapterSection.READING -> parseReadingFromImage(bitmap)
        ChapterSection.LISTENING -> parseListeningFromImage(bitmap)
        else -> parseDialogueFromImage(bitmap)
    }
}
