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
import kotlinx.coroutines.launch

@Composable
fun CustomSetScreen(viewModel: CustomSetViewModel, onPracticeGroup: (List<FlashcardItem>) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    if (state.selectedSet == null) {
        CustomSetListScreen(viewModel, state)
    } else {
        CustomSetDetailScreen(viewModel, state, onPracticeGroup)
    }
}

@Composable
fun CustomSetListScreen(viewModel: CustomSetViewModel, state: CustomSetUiState) {
    var newSetName by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

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
            Text("🗃️ My Sets", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { showCreateDialog = true }) { Text("+ CREATE SET") }
        }
        Spacer(Modifier.height(12.dp))

        if (state.error != null) {
            Text("⚠️ ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (state.sets.isEmpty() && !state.loading) {
            Text(
                "এখনো কোনো custom set নেই। Chapter এর বাইরেও নিজের মতো word group বানাতে "
                    + "\"+ CREATE SET\" চাপো।",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.sets) { set ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(set.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Row {
                            TextButton(onClick = { viewModel.openSet(set) }) { Text("খোলো") }
                            TextButton(onClick = { viewModel.deleteSet(set) }) {
                                Text("🗑️", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("নতুন Set এর নাম") },
            text = {
                OutlinedTextField(
                    value = newSetName,
                    onValueChange = { newSetName = it },
                    singleLine = true,
                    placeholder = { Text("যেমন: EPS-TOPIK Important Words") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createSet(newSetName)
                    newSetName = ""
                    showCreateDialog = false
                }) { Text("তৈরি করো") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
fun CustomSetDetailScreen(
    viewModel: CustomSetViewModel,
    state: CustomSetUiState,
    onPracticeGroup: (List<FlashcardItem>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<VocabWordEntity>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.closeSet() }) { Text("⬅️") }
            Text(state.selectedSet?.name ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        if (state.error != null) {
            Text("⚠️ ${state.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAddDialog = true }) { Text("➕ Word যোগ করো") }
            if (state.selectedSetWords.isNotEmpty()) {
                Button(onClick = { onPracticeGroup(state.selectedSetWords) }) {
                    Text("▶️ Practice করো (${state.selectedSetWords.size})")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (state.selectedSetWords.isEmpty() && !state.loading) {
            Text("এই set এ এখনো কোনো word নেই। \"➕ Word যোগ করো\" চাপো।", fontSize = 13.sp)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.selectedSetWords) { w ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(w.korean, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(w.bangla, fontSize = 13.sp)
                        }
                        TextButton(onClick = { viewModel.removeWord(w.korean) }) {
                            Text("✖️", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Word খুঁজে যোগ করো") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { q ->
                            searchQuery = q
                            scope.launch { searchResults = viewModel.searchVocab(q) }
                        },
                        singleLine = true,
                        placeholder = { Text("Korean বা Bangla লিখে খোঁজো") }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(searchResults) { w ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(w.korean_word, fontWeight = FontWeight.SemiBold)
                                    Text(w.bangla_meaning, fontSize = 12.sp)
                                }
                                TextButton(onClick = { viewModel.addWords(listOf(w.korean_word)) }) {
                                    Text("➕")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("বন্ধ করো") }
            }
        )
    }
}
