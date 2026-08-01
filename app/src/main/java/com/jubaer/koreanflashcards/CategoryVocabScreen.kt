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
fun CategoryVocabScreen(viewModel: CategoryVocabViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("🗂️ Vocabulary Categories", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (state.error != null) {
            Text("⚠️ ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.loadCategories() }) { Text("আবার চেষ্টা করো") }
        }

        if (state.categories.isEmpty() && !state.loading && state.error == null) {
            Text("এখনো কোনো category তৈরি হয়নি। Web app থেকে categorize করো।")
        }

        if (state.categories.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(state.selectedCategory ?: "Category বেছে নাও")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.categories.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat) }, onClick = {
                            viewModel.selectCategory(cat)
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

        Text(
            "${state.words.size} টা word",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.words) { w ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(w.korean, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("অর্থ: ${w.bangla}", fontSize = 14.sp)
                        if (!w.synonyms.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Synonym: ${w.synonyms} (${w.banglaSynonyms ?: ""})",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!w.antonyms.isNullOrBlank()) {
                            Text(
                                "Antonym: ${w.antonyms} (${w.banglaAntonyms ?: ""})",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
