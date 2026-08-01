package com.jubaer.koreanflashcards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * onPracticeGroup: একটা category-group এর word list দিয়ে Flashcard tab এ গিয়ে
 * সরাসরি practice শুরু করার জন্য callback (MainActivity/AppRoot থেকে wiring করা)।
 */
@Composable
fun BrowseVocabularyScreen(viewModel: BrowseVocabularyViewModel, onPracticeGroup: (List<FlashcardItem>) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("📚 Browse Vocabulary", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (state.error != null) {
            Text("⚠️ ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.loadChapters() }) { Text("আবার চেষ্টা করো") }
            Spacer(Modifier.height(8.dp))
        }

        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (state.chapters.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text("Chapter ${state.selectedChapter ?: ""}")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.chapters.forEach { ch ->
                        DropdownMenuItem(text = { Text("Chapter $ch") }, onClick = {
                            viewModel.selectChapter(ch)
                            expanded = false
                        })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val totalWords = state.groupedWords.values.sumOf { it.size }
            Text(
                "$totalWords টা word, ${state.groupedWords.size} টা category-group এ ভাগ করা",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.groupedWords.forEach { (category, words) ->
                    item {
                        CategoryGroupSection(category, words, onPracticeGroup)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryGroupSection(category: String, words: List<FlashcardItem>, onPracticeGroup: (List<FlashcardItem>) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🗂️ $category", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${words.size} টা word", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }

            Spacer(Modifier.height(8.dp))

            words.forEach { w ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(w.korean, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(w.bangla, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onPracticeGroup(words) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶️ এই group Practice করো (${words.size} টা word)")
            }
        }
    }
}
