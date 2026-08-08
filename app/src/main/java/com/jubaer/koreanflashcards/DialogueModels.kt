package com.jubaer.koreanflashcards

data class DialogueTurn(
    val speaker: String,
    val korean: String,
    val bangla: String
)

data class DialogueParseResult(
    val turns: List<DialogueTurn>,
    val glossary: Map<String, String>
)
