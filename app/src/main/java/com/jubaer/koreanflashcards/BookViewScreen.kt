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

@Composable
fun BookViewScreen(viewModel: BookViewViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("📚 বই আকারে দেখো", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (state.error != null) {
            Text("⚠️ ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.loadChapters() }) { Text("আবার চেষ্টা করো") }
        }

        if (state.chapters.isEmpty() && !state.loading && state.error == null) {
            Text("এখনো কোনো paragraph যোগ হয়নি।")
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
            Spacer(Modifier.height(12.dp))
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        val chapterTitle = state.paragraphs.firstOrNull()?.chapter_title
        if (!chapterTitle.isNullOrBlank()) {
            Text(chapterTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.paragraphs) { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!p.heading.isNullOrBlank()) {
                            Text(p.heading, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (!p.paragraph_label.isNullOrBlank()) {
                            Text(p.paragraph_label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(p.annotated_text, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
