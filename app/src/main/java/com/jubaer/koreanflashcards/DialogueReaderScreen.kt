package com.jubaer.koreanflashcards

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun normalizeKorean(s: String): String = s
    .trim()
    .trim('.', ',', '?', '!', '"', '\'', '“', '”', '‘', '’', '(', ')', '「', '」')

private fun lookupMeaning(token: String, glossary: Map<String, String>): String {
    val core = normalizeKorean(token)
    return glossary[core] ?: glossary[token] ?: "❓ অর্থ পাওয়া যায়নি (টোকেন: $core)"
}

private fun underlinedTokenSet(underlined: List<String>): Set<String> =
    underlined.flatMap { it.split(Regex("\\s+")) }
        .map(::normalizeKorean)
        .filter { it.isNotBlank() }
        .toSet()

@Composable
fun DialogueReaderScreen(viewModel: DialogueReaderViewModel) {
    val state by viewModel.uiState.collectAsState()
    when (state.mode) {
        DialogueScreenMode.LIBRARY -> DialogueLibraryScreen(viewModel, state)
        DialogueScreenMode.EDITOR -> DialogueEditorScreen(viewModel, state)
        DialogueScreenMode.VIEWER -> DialogueViewerScreen(viewModel, state)
    }
}

@Composable
private fun DialogueLibraryScreen(viewModel: DialogueReaderViewModel, state: DialogueUiState) {
    var chapterToDelete by remember { mutableStateOf<DialogueChapter?>(null) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("💬 Dialogue চ্যাপ্টার", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Dialogue + Reading + Listening + Culture", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = viewModel::loadLibrary) { Text("🔄") }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = viewModel::startNewChapter, modifier = Modifier.fillMaxWidth()) { Text("➕ নতুন চ্যাপ্টার") }
        Spacer(Modifier.height(16.dp))
        state.libraryError?.let {
            Text("⚠️ $it", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        if (state.libraryLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
        } else if (state.library.isEmpty()) {
            Text("এখনো কোনো চ্যাপ্টার সেভ করা হয়নি।", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.library) { chapter ->
                    DialogueChapterCard(chapter, { viewModel.openChapterForReading(chapter) }, { viewModel.editChapter(chapter) }, { chapterToDelete = chapter })
                }
            }
        }
    }
    chapterToDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text("চ্যাপ্টার ডিলিট করবে?") },
            text = { Text("\"${doomed.chapterName}\" চ্যাপ্টারটা স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteChapter(doomed.id); chapterToDelete = null }) {
                    Text("ডিলিট করো", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { chapterToDelete = null }) { Text("বাতিল") } }
        )
    }
}

@Composable
private fun DialogueChapterCard(chapter: DialogueChapter, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dateStr = remember(chapter.createdAt) { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(chapter.createdAt)) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp)) {
            Text(chapter.chapterName, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("সেভ: $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChapterSection.values().forEach { sec ->
                    val done = !chapter.content(sec).isEmpty
                    AssistChip(onClick = onOpen, label = { Text(sec.label, fontSize = 9.sp) }, leadingIcon = { Text(if (done) "✅" else "▫️", fontSize = 9.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onEdit) { Text("✏️ এডিট") }
                TextButton(onClick = onDelete) { Text("🗑️ ডিলিট", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun DialogueEditorScreen(viewModel: DialogueReaderViewModel, state: DialogueUiState) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) viewModel.processActiveSectionImage(bitmap)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(if (state.editingChapterId == null) "➕ নতুন চ্যাপ্টার" else "✏️ চ্যাপ্টার এডিট", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = viewModel::cancelEditor) { Text("✖️ বাতিল") }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.chapterName,
            onValueChange = viewModel::setChapterName,
            label = { Text("চ্যাপ্টারের নাম") },
            placeholder = { Text("যেমন: Chapter 40") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        ScrollableTabRow(selectedTabIndex = state.activeSection.ordinal) {
            ChapterSection.values().forEach { sec ->
                val done = state.sections[sec]?.isDone == true
                Tab(
                    selected = state.activeSection == sec,
                    onClick = { viewModel.setActiveSection(sec) },
                    text = { Text("${sec.emoji} ${sec.label}${if (done) " ✅" else ""}", fontSize = 10.sp) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        state.saveError?.let {
            Text("⚠️ $it", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        val sectionState = state.sections[state.activeSection] ?: SectionEditState()
        Box(Modifier.weight(1f)) {
            if (sectionState.isDone) {
                Column(Modifier.fillMaxSize()) {
                    TextButton(onClick = { viewModel.clearSection(state.activeSection) }) { Text("🔄 এই অংশটা আবার করো") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        if (state.activeSection == ChapterSection.READING || state.activeSection == ChapterSection.LISTENING) {
                            items(sectionState.content.questions) { q -> ExamQuestionCard(q, sectionState.content.glossary, {}) }
                            items(sectionState.content.turns) { turn -> ParagraphCard(turn, sectionState.content.glossary) {} }
                        } else {
                            items(sectionState.content.turns) { turn ->
                                if (state.activeSection == ChapterSection.CULTURE) ParagraphCard(turn, sectionState.content.glossary) {}
                                else DialogueBubble(turn, sectionState.content.glossary) {}
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        when (state.activeSection) {
                            ChapterSection.READING -> "📖 Reading screenshot দাও — প্রশ্ন, option এবং underline চিনে নেবে"
                            ChapterSection.LISTENING -> "🎧 Listening screenshot দাও — প্রশ্ন, option, underline ও 듣기지문 চিনে নেবে"
                            ChapterSection.CULTURE -> "তথ্য/সংস্কৃতি অংশের Korean paragraph দাও"
                            else -> "এই অংশের কথোপকথনের screenshot বা text দাও"
                        },
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    sectionState.error?.let {
                        Text("⚠️ $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(onClick = { imagePicker.launch("image/*") }, Modifier.fillMaxWidth(), enabled = !sectionState.loading) {
                        Text("📷 Screenshot আপলোড করে AI দিয়ে চিনাও")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("— অথবা text paste করো —", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sectionState.inputText,
                        onValueChange = { viewModel.updateSectionInputText(state.activeSection, it) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        placeholder = { Text("Korean text paste করো...") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::processActiveSectionText,
                        enabled = sectionState.inputText.isNotBlank() && !sectionState.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (sectionState.loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Screenshot/AI বিশ্লেষণ চলছে...")
                        } else Text("🚀 প্রসেস করো")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = viewModel::saveChapter, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("💾 চ্যাপ্টার সেভ করো")
        }
    }
}

@Composable
private fun DialogueViewerScreen(viewModel: DialogueReaderViewModel, state: DialogueUiState) {
    val chapter = state.viewingChapter ?: return
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(chapter.chapterName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = viewModel::backToLibrary) { Text("⬅️ লাইব্রেরি") }
        }
        Text("Underline করা word/phrase-এ tap করলে বাংলা অর্থ দেখাবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        ScrollableTabRow(selectedTabIndex = state.viewingSection.ordinal) {
            ChapterSection.values().forEach { sec ->
                val hasContent = !chapter.content(sec).isEmpty
                Tab(selected = state.viewingSection == sec, onClick = { viewModel.setViewingSection(sec) }, text = { Text("${sec.emoji} ${sec.label}${if (!hasContent) " (খালি)" else ""}", fontSize = 10.sp) })
            }
        }
        Spacer(Modifier.height(12.dp))
        val content = chapter.content(state.viewingSection)
        if (content.isEmpty) {
            Text("এই অংশে কিছু সেভ করা নেই। Edit করে screenshot upload করো।", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.viewingSection == ChapterSection.READING || state.viewingSection == ChapterSection.LISTENING) {
                    items(content.questions) { q -> ExamQuestionCard(q, content.glossary) { word -> viewModel.selectWord(word) } }
                    items(content.turns) { turn -> ParagraphCard(turn, content.glossary) { word -> viewModel.selectWord(word) } }
                } else {
                    items(content.turns) { turn ->
                        if (state.viewingSection == ChapterSection.CULTURE) ParagraphCard(turn, content.glossary) { word -> viewModel.selectWord(word) }
                        else DialogueBubble(turn, content.glossary) { word -> viewModel.selectWord(word) }
                    }
                }
            }
        }
    }

    if (state.selectedWord != null) {
        val content = chapter.content(state.viewingSection)
        AlertDialog(
            onDismissRequest = { viewModel.selectWord(null) },
            title = { Text(state.selectedWord!!) },
            text = { Text(lookupMeaning(state.selectedWord!!, content.glossary)) },
            confirmButton = { TextButton(onClick = { viewModel.selectWord(null) }) { Text("বন্ধ করো") } }
        )
    }
}

@Composable
private fun UnderlinedWords(text: String, underlined: List<String>, onWordClick: (String) -> Unit) {
    val underlinedTokens = underlinedTokenSet(underlined)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            val marked = normalizeKorean(word) in underlinedTokens
            Text(
                word,
                fontSize = 16.sp,
                fontWeight = if (marked) FontWeight.Bold else FontWeight.Medium,
                textDecoration = if (marked) TextDecoration.Underline else TextDecoration.None,
                modifier = Modifier.clickable { onWordClick(word) }
            )
        }
    }
}

@Composable
private fun ExamQuestionCard(question: ExamQuestion, glossary: Map<String, String>, onWordClick: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${question.number}.", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            UnderlinedWords(question.question, question.underlined, onWordClick)
            if (question.bangla.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(question.bangla, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
            question.options.forEachIndexed { index, option ->
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}  ", fontWeight = FontWeight.Bold)
                    UnderlinedWords(option, question.underlined, onWordClick)
                }
            }
        }
    }
}

@Composable
private fun DialogueBubble(turn: DialogueTurn, glossary: Map<String, String>, onWordClick: (String) -> Unit) {
    val isSpeakerA = turn.speaker == "A"
    Column(horizontalAlignment = if (isSpeakerA) Alignment.Start else Alignment.End, modifier = Modifier.fillMaxWidth()) {
        Card(colors = CardDefaults.cardColors(containerColor = if (isSpeakerA) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth(0.88f)) {
            Column(Modifier.padding(12.dp)) {
                UnderlinedWords(turn.korean, emptyList(), onWordClick)
                if (turn.bangla.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(turn.bangla, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun ParagraphCard(turn: DialogueTurn, glossary: Map<String, String>, onWordClick: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            UnderlinedWords(turn.korean, emptyList(), onWordClick)
            if (turn.bangla.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(turn.bangla, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
