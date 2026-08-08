package com.jubaer.koreanflashcards

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun lookupMeaning(token: String, glossary: Map<String, String>): String {
    val core = token.trim().trim('.', ',', '?', '!', '"', '\'')
    return glossary[core] ?: glossary[token] ?: "❓ অর্থ পাওয়া যায়নি (টোকেন: $core)"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogueReaderScreen(viewModel: DialogueReaderViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val stream = context.contentResolver.openInputStream(uri)
            val bitmap = stream?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) viewModel.processImage(bitmap)
        }
    }

    if (state.turns.isEmpty()) {
        // ---------- Input screen ----------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("💬 Dialogue Reader", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Screenshot বা text দাও — dialogue টা chat bubble আকারে দেখাবে, "
                    + "প্রতিটা word এ ট্যাপ করলে অর্থ দেখাবে।",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))

            if (state.error != null) {
                Text("⚠️ ${state.error}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("📷 Screenshot দাও")
            }

            Spacer(Modifier.height(16.dp))
            Text("— অথবা —", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.updateInputText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                placeholder = { Text("Dialogue text এখানে paste করো...") }
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.processText() },
                enabled = state.inputText.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Processing...")
                } else {
                    Text("🚀 Process করো")
                }
            }

            if (state.loading) {
                Spacer(Modifier.height(12.dp))
                Text("AI dialogue বিশ্লেষণ করছে (একটু সময় লাগতে পারে)...", fontSize = 12.sp)
            }
        }
    } else {
        // ---------- Chat bubble display ----------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💬 Dialogue", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.reset() }) { Text("🔄 নতুন") }
            }
            Text(
                "word এ ট্যাপ করলে অর্থ দেখাবে",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.turns) { turn ->
                    DialogueBubble(turn, state.glossary) { word -> viewModel.selectWord(word) }
                }
            }
        }

        if (state.selectedWord != null) {
            val meaning = lookupMeaning(state.selectedWord!!, state.glossary)
            AlertDialog(
                onDismissRequest = { viewModel.selectWord(null) },
                title = { Text(state.selectedWord!!) },
                text = { Text(meaning) },
                confirmButton = {
                    TextButton(onClick = { viewModel.selectWord(null) }) { Text("বন্ধ করো") }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogueBubble(turn: DialogueTurn, glossary: Map<String, String>, onWordClick: (String) -> Unit) {
    val isSpeakerA = turn.speaker == "A"

    Column(
        horizontalAlignment = if (isSpeakerA) Alignment.Start else Alignment.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isSpeakerA) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    turn.korean.split(" ").filter { it.isNotBlank() }.forEach { word ->
                        Text(
                            word,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onWordClick(word) }
                        )
                    }
                }
                if (turn.bangla.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(turn.bangla, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
