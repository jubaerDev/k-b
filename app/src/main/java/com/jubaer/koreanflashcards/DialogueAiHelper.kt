package com.jubaer.koreanflashcards

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject

/**
 * একটা Korean-Bangla dialogue (screenshot বা paste করা text) থেকে:
 * ১. প্রতিটা turn/bubble আলাদা করে (speaker + Korean + Bangla)
 * ২. পুরো dialogue এ থাকা প্রতিটা Korean word এর জন্য একটা glossary (word → বাংলা অর্থ)
 * একটাই AI call এ বানায় — এতে পরে প্রতিটা word ট্যাপ করলে আবার API call করতে হয় না,
 * সাথে সাথে (local glossary lookup) অর্থ দেখানো যায়।
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

    suspend fun parseDialogueFromText(rawText: String): DialogueParseResult {
        var lastError: Exception? = null
        for (modelName in CANDIDATE_MODELS) {
            try {
                val model = GenerativeModel(modelName = modelName, apiKey = GeminiConfig.API_KEY)
                val response = model.generateContent("$DIALOGUE_PROMPT\n\nDialogue:\n$rawText")
                return parseJsonResponse(response.text ?: "")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Unknown error")
    }

    suspend fun parseDialogueFromImage(bitmap: Bitmap): DialogueParseResult {
        var lastError: Exception? = null
        for (modelName in CANDIDATE_MODELS) {
            try {
                val model = GenerativeModel(modelName = modelName, apiKey = GeminiConfig.API_KEY)
                val inputContent = content {
                    image(bitmap)
                    text(DIALOGUE_PROMPT)
                }
                val response = model.generateContent(inputContent)
                return parseJsonResponse(response.text ?: "")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Unknown error")
    }
}
