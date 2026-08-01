package com.jubaer.koreanflashcards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuestionBankScreen(viewModel: QuestionBankViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("📝 Question Bank", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (state.error != null) {
            Text("⚠️ ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.loadCategories() }) { Text("আবার চেষ্টা করো") }
        }

        if (state.categories.isEmpty() && !state.loading && state.error == null) {
            Text("এখনো কোনো question যোগ হয়নি।")
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
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.questions) { q ->
                QuestionCard(q)
            }
        }
    }
}

@Composable
fun QuestionCard(q: QuestionBankRow) {
    var selected by remember(q.id) { mutableStateOf<Int?>(null) }
    var showAnswer by remember(q.id) { mutableStateOf(false) }
    val options = listOf(q.option1, q.option2, q.option3, q.option4)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(q.question_text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            options.forEachIndexed { index, opt ->
                val optionNumber = index + 1
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = selected == optionNumber, onClick = { selected = optionNumber })
                    Text("$optionNumber. $opt", fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = showAnswer, onCheckedChange = { showAnswer = it })
                Text("✅ ব্যাখ্যা/সঠিক answer দেখাও", fontSize = 13.sp)
            }

            if (showAnswer) {
                Spacer(Modifier.height(4.dp))
                if (selected == q.correct_answer) {
                    Text("✅ সঠিক! Answer: ${q.correct_answer}", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("❌ সঠিক Answer: ${q.correct_answer}", color = MaterialTheme.colorScheme.error)
                }
                if (!q.explanation.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(q.explanation, fontSize = 13.sp)
                }
            }
        }
    }
}
