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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun lookupMeaning(token: String, glossary: Map<String, String>): String {
    val core = token.trim().trim('.', ',', '?', '!', '"', '\'', '“', '”', '‘', '’', '(', ')', '[', ']', '。', '！', '？', '，')
    return glossary[core] ?: glossary[token] ?: ""
}

@Composable
fun DialogueReaderScreen(viewModel: DialogueReaderViewModel) {
    val state by viewModel.uiState.collectAsState()

    when (state.mode) {
        DialogueScreenMode.LIBRARY -> DialogueLibraryScreen(viewModel, state)
        DialogueScreenMode.EDITOR -> DialogueEditorScreen(viewModel, state)
        DialogueScreenMode.VIEWER -> DialogueViewerScreen(viewModel, state)
    }
}

// ========================================================================
// ১. লাইব্রেরি — সেভ করা সব চ্যাপ্টার (অফলাইনে পড়ার জন্য)
// ========================================================================

@Composable
private fun DialogueLibraryScreen(viewModel: DialogueReaderViewModel, state: DialogueUiState) {
    var chapterToDelete by remember { mutableStateOf<DialogueChapter?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("💬 Dialogue চ্যাপ্টার", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "সেভ করা চ্যাপ্টার internet ছাড়াই পড়া যাবে",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = { viewModel.loadLibrary() }) { Text("🔄") }
        }
        Spacer(Modifier.height(12.dp))

        Button(onClick = { viewModel.startNewChapter() }, modifier = Modifier.fillMaxWidth()) {
            Text("➕ নতুন চ্যাপ্টার")
        }
        Spacer(Modifier.height(16.dp))

        if (state.libraryError != null) {
            Text("⚠️ ${state.libraryError}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (state.libraryLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.library.isEmpty()) {
            Text(
                "এখনো কোনো চ্যাপ্টার সেভ করা হয়নি। \"নতুন চ্যাপ্টার\" দিয়ে শুরু করো।",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.library) { chapter ->
                    DialogueChapterCard(
                        chapter = chapter,
                        onOpen = { viewModel.openChapterForReading(chapter) },
                        onEdit = { viewModel.editChapter(chapter) },
                        onDelete = { chapterToDelete = chapter }
                    )
                }
            }
        }
    }

    val doomed = chapterToDelete
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text("চ্যাপ্টার ডিলিট করবে?") },
            text = { Text("\"${doomed.chapterName}\" চ্যাপ্টারটা স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChapter(doomed.id)
                    chapterToDelete = null
                }) { Text("ডিলিট করো", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun DialogueChapterCard(
    chapter: DialogueChapter,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(chapter.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(chapter.createdAt))
    }
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(chapter.chapterName, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("সেভ: $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChapterSection.values().forEach { sec ->
                    val done = !chapter.content(sec).isEmpty
                    AssistChip(
                        onClick = onOpen,
                        label = { Text(sec.label, fontSize = 11.sp) },
                        leadingIcon = { Text(if (done) "✅" else "▫️", fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onEdit) { Text("✏️ এডিট") }
                TextButton(onClick = onDelete) { Text("🗑️ ডিলিট", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

// ========================================================================
// ২. এডিটর — নতুন চ্যাপ্টার তৈরি / পুরনোটা এডিট (৩টা অংশ: কথপোকথন ১, কথপোকথন ২, তথ্য-সংস্কৃতি)
// ========================================================================

@Composable
private fun DialogueEditorScreen(viewModel: DialogueReaderViewModel, state: DialogueUiState) {
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val stream = context.contentResolver.openInputStream(uri)
            val bitmap = stream?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) viewModel.processActiveSectionImage(bitmap)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (state.editingChapterId == null) "➕ নতুন চ্যাপ্টার" else "✏️ চ্যাপ্টার এডিট",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { viewModel.cancelEditor() }) { Text("✖️ বাতিল") }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.chapterName,
            onValueChange = { viewModel.setChapterName(it) },
            label = { Text("চ্যাপ্টারের নাম") },
            placeholder = { Text("যেমন: অধ্যায় ৫ — বাজারে") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        // ৩টা section এর ট্যাব
        TabRow(selectedTabIndex = state.activeSection.ordinal) {
            ChapterSection.values().forEach { sec ->
                val done = state.sections[sec]?.isDone == true
                Tab(
                    selected = state.activeSection == sec,
                    onClick = { viewModel.setActiveSection(sec) },
                    text = { Text("${sec.emoji} ${sec.label}${if (done) " ✅" else ""}", fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.saveError != null) {
            Text("⚠️ ${state.saveError}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        val sectionState = state.sections[state.activeSection] ?: SectionEditState()

        Box(modifier = Modifier.weight(1f)) {
            if (sectionState.isDone) {
                // ইতিমধ্যে প্রসেস হয়ে গেছে — preview দেখাও + আবার করার অপশন
                Column(modifier = Modifier.fillMaxSize()) {
                    TextButton(onClick = { viewModel.clearSection(state.activeSection) }) {
                        Text("🔄 এই অংশটা আবার করো")
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        items(sectionState.content.turns) { turn ->
                            if (state.activeSection == ChapterSection.CULTURE) {
                                ParagraphCard(turn, sectionState.content.glossary) { }
                            } else {
                                DialogueBubble(turn, sectionState.content.glossary) { }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        if (state.activeSection == ChapterSection.CULTURE)
                            "তথ্য/সংস্কৃতি অংশের কোরিয়ান প্যারাগ্রাফ দাও (স্ক্রিনশট বা টেক্সট)"
                        else
                            "এই অংশের কথোপকথন দাও (স্ক্রিনশট বা টেক্সট)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))

                    if (sectionState.error != null) {
                        Text("⚠️ ${sectionState.error}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                    }

                    Button(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sectionState.loading
                    ) { Text("📷 Screenshot দাও") }

                    Spacer(Modifier.height(12.dp))
                    Text("— অথবা —", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = sectionState.inputText,
                        onValueChange = { viewModel.updateSectionInputText(state.activeSection, it) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        placeholder = { Text("এখানে কোরিয়ান প্যারাগ্রাফ/টেক্সট paste করো...") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.processActiveSectionText() },
                        enabled = sectionState.inputText.isNotBlank() && !sectionState.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (sectionState.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Processing...")
                        } else {
                            Text("🚀 প্রসেস করো")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.saveChapter() },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("সেভ হচ্ছে...")
            } else {
                Text("💾 চ্যাপ্টার সেভ করো")
            }
        }
    }
}

// ========================================================================
// ৩. ভিউয়ার — সেভ করা চ্যাপ্টার অফলাইনে পড়া (কোনো AI call না, শুধু local data)
// ========================================================================

@Composable
private fun DialogueViewerScreen(viewModel: DialogueReaderViewModel, state: DialogueUiState) {
    val chapter = state.viewingChapter ?: return

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(chapter.chapterName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { viewModel.backToLibrary() }) { Text("⬅️ লাইব্রেরি") }
        }
        Text(
            "word এ ট্যাপ করলে অর্থ দেখাবে (offline)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(8.dp))

        TabRow(selectedTabIndex = state.viewingSection.ordinal) {
            ChapterSection.values().forEach { sec ->
                val hasContent = !chapter.content(sec).isEmpty
                Tab(
                    selected = state.viewingSection == sec,
                    onClick = { viewModel.setViewingSection(sec) },
                    text = { Text("${sec.emoji} ${sec.label}${if (!hasContent) " (খালি)" else ""}", fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        val content = chapter.content(state.viewingSection)
        if (content.isEmpty) {
            Text(
                "এই অংশে কিছু সেভ করা নেই। এডিট করে যোগ করতে পারো।",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(content.turns) { turn ->
                    if (state.viewingSection == ChapterSection.CULTURE) {
                        ParagraphCard(turn, content.glossary) { word -> viewModel.selectWord(word, content.glossary) }
                    } else {
                        DialogueBubble(turn, content.glossary) { word -> viewModel.selectWord(word, content.glossary) }
                    }
                }
            }
        }
    }

    if (state.selectedWord != null) {
        AlertDialog(
            onDismissRequest = { viewModel.selectWord(null) },
            title = { Text(state.selectedWord!!) },
            text = {
                when {
                    state.meaningLoading -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("অর্থ খোঁজা হচ্ছে…")
                    }
                    state.meaningError != null -> Text(
                        "⚠️ ${state.meaningError}",
                        color = MaterialTheme.colorScheme.error
                    )
                    !state.selectedMeaning.isNullOrBlank() -> Text(state.selectedMeaning!!)
                    else -> Text("❓ অর্থ পাওয়া যায়নি")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.selectWord(null) }) { Text("বন্ধ করো") }
            }
        )
    }
}

// ========================================================================
// শেয়ার্ড কম্পোনেন্ট: কথোপকথনের bubble (speaker A/B) ও প্যারাগ্রাফ কার্ড (culture/info)
// ========================================================================

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

/** তথ্য/সংস্কৃতি অংশের জন্য — bubble না, পুরো width জুড়ে ধারাবাহিক প্যারাগ্রাফ কার্ড। */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParagraphCard(turn: DialogueTurn, glossary: Map<String, String>, onWordClick: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
