package com.jubaer.koreanflashcards

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject

/**
 * একটা Korean-Bangla dialogue/paragraph (screenshot বা paste করা text) থেকে:
 * ১. প্রতিটা turn/bubble বা বাক্য আলাদা করে (speaker + Korean + Bangla)
 * ২. পুরো লেখায় থাকা প্রতিটা Korean word এর জন্য একটা glossary (word → বাংলা অর্থ)
 * একটাই AI call এ বানায় — এতে পরে প্রতিটা word ট্যাপ করলে আবার API call করতে হয় না,
 * সাথে সাথে (local glossary lookup) অর্থ দেখানো যায়। একবার সেভ হয়ে গেলে এই
 * turns+glossary ই offline এ পড়ার জন্য যথেষ্ট, তাই আর কোনো AI call লাগে না।
 */
object DialogueAiHelper {

    private val CANDIDATE_MODELS = listOf("gemini-2.5-flash", "gemini-3.1-flash-lite", "gemini-flash-latest")

    private const val DIALOGUE_PROMPT = """
তুমি একজন Korean-Bangla ভাষা বিশেষজ্ঞ। তোমাকে একটা Korean-Bangla dialogue (কথোপকথন) দেওয়া হবে —
ছবি (screenshot) আকারে অথবা টেক্সট আকারে। এতে প্রতিটা turn এ Korean বাক্য ও তার Bangla অনুবাদ
থাকতে পারে (Bangla না থাকলে নিজে থেকে স্বাভাবিক বাংলায় অনুবাদ করে দেবে)।

তোমার কাজ:
1. পুরো dialogue টা আলাদা আলাদা turn/bubble এ ভাগ করা, প্রতিটার speaker "A" বা "B" হিসেবে
   চিহ্নিত করা (কথোপকথনে যে দুইজন আছে, তাদের ক্রমানুসারে A ও B ধরে নেবে)।
2. প্রতিটা turn এর Korean text ও তার Bangla অর্থ আলাদা করে বের করা।
3. পুরো dialogue এ থাকা প্রতিটা আলাদা Korean word/টোকেন (grammar particle সহ, স্পেস দিয়ে
   ভাগ করা eojeol হিসেবে) এর একটা glossary বানানো — প্রতিটার সংক্ষিপ্ত বাংলা অর্থ।

শুধু নিচের JSON ফরম্যাটে দাও, অন্য কোনো টেক্সট/ব্যাখ্যা/code fence দিও না:
{"turns": [{"speaker": "A", "korean": "...", "bangla": "..."}], "glossary": {"শব্দ": "অর্থ"}}
"""

    // "তথ্য / সংস্কৃতি" অংশের জন্য — এটা দুইজনের কথোপকথন না, বরং একটা তথ্যভিত্তিক/
    // সাংস্কৃতিক প্যারাগ্রাফ (narration)। তাই speaker আলাদা না করে পুরো লেখাটাকে
    // অর্থবহ বাক্য/অংশে ভাগ করে দেবে।
    private const val PARAGRAPH_PROMPT = """
তুমি একজন Korean-Bangla ভাষা বিশেষজ্ঞ। তোমাকে একটা Korean তথ্যভিত্তিক/সাংস্কৃতিক প্যারাগ্রাফ
(narration/রচনা, dialogue না) দেওয়া হবে — ছবি (screenshot) আকারে অথবা টেক্সট আকারে। এতে Bangla
অনুবাদ থাকতে পারে (না থাকলে নিজে থেকে স্বাভাবিক বাংলায় অনুবাদ করে দেবে)।

তোমার কাজ:
1. পুরো প্যারাগ্রাফটাকে অর্থবহ বাক্য/অংশে ভাগ করা — প্রতিটা অংশের Korean টেক্সট ও তার
   Bangla অর্থ।
2. "speaker" ফিল্ডে সবসময় "" (খালি স্ট্রিং) দেবে, কারণ এটা কথোপকথন না।
3. পুরো লেখায় থাকা প্রতিটা আলাদা Korean word/টোকেন এর একটা glossary বানানো — প্রতিটার
   সংক্ষিপ্ত বাংলা অর্থ।

শুধু নিচের JSON ফরম্যাটে দাও, অন্য কোনো টেক্সট/ব্যাখ্যা/code fence দিও না:
{"turns": [{"speaker": "", "korean": "...", "bangla": "..."}], "glossary": {"শব্দ": "অর্থ"}}
"""

    private fun parseJsonResponse(text: String): DialogueParseResult {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trim()
        }
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1)
        }

        val obj = JSONObject(cleaned)
        val turns = mutableListOf<DialogueTurn>()
        val turnsArray = obj.optJSONArray("turns")
        if (turnsArray != null) {
            for (i in 0 until turnsArray.length()) {
                val t = turnsArray.getJSONObject(i)
                turns.add(
                    DialogueTurn(
                        speaker = t.optString("speaker", if (i % 2 == 0) "A" else "B"),
                        korean = t.optString("korean", ""),
                        bangla = t.optString("bangla", "")
                    )
                )
            }
        }

        val glossary = mutableMapOf<String, String>()
        val glossaryObj = obj.optJSONObject("glossary")
        if (glossaryObj != null) {
            glossaryObj.keys().forEach { k -> glossary[k] = glossaryObj.optString(k, "") }
        }

        return DialogueParseResult(turns, glossary)
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
        throw lastError ?: RuntimeException("Unknown error")
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
        throw lastError ?: RuntimeException("Unknown error")
    }

    suspend fun parseDialogueFromText(rawText: String): DialogueParseResult = runForText(DIALOGUE_PROMPT, rawText)
    suspend fun parseDialogueFromImage(bitmap: Bitmap): DialogueParseResult = runForImage(DIALOGUE_PROMPT, bitmap)

    suspend fun parseParagraphFromText(rawText: String): DialogueParseResult = runForText(PARAGRAPH_PROMPT, rawText)
    suspend fun parseParagraphFromImage(bitmap: Bitmap): DialogueParseResult = runForImage(PARAGRAPH_PROMPT, bitmap)

    /** চ্যাপ্টারের কোন section (কথপোকথন ১/২ নাকি তথ্য/সংস্কৃতি) সেটা অনুযায়ী সঠিক prompt বেছে নেয়। */
    suspend fun parseSectionFromText(section: ChapterSection, rawText: String): DialogueParseResult =
        if (section == ChapterSection.CULTURE) parseParagraphFromText(rawText) else parseDialogueFromText(rawText)

    suspend fun parseSectionFromImage(section: ChapterSection, bitmap: Bitmap): DialogueParseResult =
        if (section == ChapterSection.CULTURE) parseParagraphFromImage(bitmap) else parseDialogueFromImage(bitmap)

    /** Translate one missing Korean token to a short, natural Bangla meaning. */
    suspend fun translateSingleKoreanWord(koreanWord: String): String {
        if (GeminiConfig.API_KEY.isBlank()) throw IllegalStateException("GEMINI_API_KEY সেট করা নেই")
        val prompt = """তুমি Korean-Bangla অভিধান সহকারী। Korean শব্দ/eojeol: $koreanWord\nশুধু তার সংক্ষিপ্ত, সঠিক বাংলা অর্থ দাও। Korean শব্দটি আবার লিখবে না, কোনো ব্যাখ্যা, JSON বা markdown দেবে না। Context না থাকলে সবচেয়ে প্রচলিত অর্থ দাও।"""
        var lastError: Exception? = null
        for (modelName in CANDIDATE_MODELS) {
            try {
                val model = GenerativeModel(modelName = modelName, apiKey = GeminiConfig.API_KEY)
                val response = model.generateContent(prompt)
                val text = response.text?.trim().orEmpty()
                if (text.isNotBlank()) return text
                throw IllegalStateException("Gemini empty response")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Gemini meaning lookup failed")
    }

}
