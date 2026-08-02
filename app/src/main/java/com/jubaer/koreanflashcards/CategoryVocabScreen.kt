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
 * onPracticeGroup: নির্বাচিত word গুলো দিয়ে Flashcard tab এ গিয়ে সরাসরি
 * practice শুরু করার callback (MainActivity/AppRoot থেকে wiring করা)।
 */
@Composable
fun CategoryVocabScreen(viewModel: CategoryVocabViewModel, onPracticeGroup: (List<FlashcardItem>) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val selectedWords = remember { mutableStateListOf<String>() }

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

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.words) { w ->
                val isSelected = selectedWords.contains(w.korean)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(w.korean, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("অর্থ: ${w.bangla}", fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    if (isSelected) selectedWords.remove(w.korean) else selectedWords.add(w.korean)
                                },
                                colors = if (isSelected) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                Text(if (isSelected) "✅ যোগ হয়েছে" else "➕ Practice-এ যোগ করো")
                            }
                        }

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

        if (selectedWords.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { selectedWords.clear() }) {
                    Text("❌ সব বাদ দাও")
                }
                Button(
                    onClick = {
                        viewModel.buildPracticeItems(selectedWords.toList()) { items ->
                            onPracticeGroup(items)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("▶️ Practice করো (${selectedWords.size} টা word)")
                }
            }
        }
    }
}
