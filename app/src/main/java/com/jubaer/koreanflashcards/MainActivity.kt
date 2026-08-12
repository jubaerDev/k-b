package com.jubaer.koreanflashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private lateinit var repo: FlashcardRepository
    private val flashcardViewModel: FlashcardViewModel by viewModels { FlashcardViewModelFactory(repo) }
    private val browseViewModel: BrowseVocabularyViewModel by viewModels { BrowseVocabularyViewModelFactory(repo) }
    private val uploadViewModel: UploadChapterViewModel by viewModels { UploadChapterViewModelFactory(repo) }
    private val questionBankViewModel: QuestionBankViewModel by viewModels { QuestionBankViewModelFactory(repo) }
    private val bookViewViewModel: BookViewViewModel by viewModels { BookViewViewModelFactory(repo) }
    private val categoryVocabViewModel: CategoryVocabViewModel by viewModels { CategoryVocabViewModelFactory(repo) }
    private val customSetViewModel: CustomSetViewModel by viewModels { CustomSetViewModelFactory(repo) }
    private val dialogueReaderViewModel: DialogueReaderViewModel by viewModels { DialogueReaderViewModelFactory(repo) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getInstance(applicationContext)
        repo = FlashcardRepository(ApiClient.api, db)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        repo,
                        flashcardViewModel,
                        browseViewModel,
                        uploadViewModel,
                        questionBankViewModel,
                        bookViewViewModel,
                        categoryVocabViewModel,
                        customSetViewModel,
                        dialogueReaderViewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    repo: FlashcardRepository,
    flashcardViewModel: FlashcardViewModel,
    browseViewModel: BrowseVocabularyViewModel,
    uploadViewModel: UploadChapterViewModel,
    questionBankViewModel: QuestionBankViewModel,
    bookViewViewModel: BookViewViewModel,
    categoryVocabViewModel: CategoryVocabViewModel,
    customSetViewModel: CustomSetViewModel,
    dialogueReaderViewModel: DialogueReaderViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun runSync() {
        scope.launch {
            if (!NetworkUtils.isOnline(context)) {
                syncMessage = "⚠️ Internet নেই — পুরনো (local) data দেখানো হচ্ছে"
                return@launch
            }
            syncing = true
            syncMessage = null
            try {
                repo.syncFromServer()
                flashcardViewModel.loadSetupData()
                browseViewModel.loadChapters()
                syncMessage = "✅ Sync সম্পন্ন"
            } catch (e: Exception) {
                syncMessage = "⚠️ Sync ব্যর্থ: ${e.message}"
            } finally {
                syncing = false
            }
        }
    }

    LaunchedEffect(Unit) { runSync() }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val menuItems = listOf(
        Triple("🎴", "Flashcards", 0),
        Triple("📖", "Words", 1),
        Triple("📤", "Upload", 2),
        Triple("📝", "Quiz", 3),
        Triple("📚", "Book", 4),
        Triple("🗂️", "Category", 5),
        Triple("🗃️", "My Sets", 6),
        Triple("💬", "Dialogue", 7)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Korean Flashcards", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("মেনু", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
                menuItems.forEach { (icon, label, index) ->
                    NavigationDrawerItem(
                        label = { Text("$icon  $label") },
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Korean Flashcards") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", fontSize = 20.sp)
                        }
                    },
                    actions = {
                        if (syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                        } else {
                            IconButton(onClick = { runSync() }) { Text("🔄") }
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                if (syncMessage != null) {
                    Text(
                        syncMessage!!,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> FlashcardApp(flashcardViewModel)
                        1 -> BrowseVocabularyScreen(browseViewModel, onPracticeGroup = { items ->
                            flashcardViewModel.startSessionWithItems(items)
                            selectedTab = 0
                        })
                        2 -> UploadChapterScreen(uploadViewModel)
                        3 -> QuestionBankScreen(questionBankViewModel)
                        4 -> BookViewScreen(bookViewViewModel)
                        5 -> CategoryVocabScreen(categoryVocabViewModel, onPracticeGroup = { items ->
                            flashcardViewModel.startSessionWithItems(items)
                            selectedTab = 0
                        })
                        6 -> CustomSetScreen(customSetViewModel, onPracticeGroup = { items ->
                            flashcardViewModel.startSessionWithItems(items)
                            selectedTab = 0
                        })
                        7 -> DialogueReaderScreen(dialogueReaderViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun FlashcardApp(viewModel: FlashcardViewModel) {
    val state by viewModel.uiState.collectAsState()

    when (val s = state) {
        is UiState.Setup -> SetupScreen(s, viewModel)
        is UiState.Practicing -> PracticeScreen(s, viewModel)
        is UiState.Finished -> FinishedScreen(s, viewModel)
    }
}

// ---------- Setup: Chapter list (Anki-স্টাইল "Sets") ----------

@Composable
fun SetupScreen(state: UiState.Setup, viewModel: FlashcardViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.error != null) {
            Text(
                "⚠️ ${state.error}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = { viewModel.loadSetupData() }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("আবার চেষ্টা করো")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.startSession(null, SessionMode.REVIEW) },
                modifier = Modifier.weight(1f)
            ) { Text("REVIEW ALL") }
            Button(
                onClick = { viewModel.startSession(null, SessionMode.PRACTICE) },
                modifier = Modifier.weight(1f)
            ) { Text("PRACTICE ALL") }
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.chapterSummaries) { summary ->
                ChapterSetCard(summary, viewModel)
            }
        }
    }
}

@Composable
fun ChapterSetCard(summary: ChapterSummary, viewModel: FlashcardViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Chapter ${summary.chapter}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Memorized: ${summary.memorized}/${summary.total}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TextButton(onClick = { viewModel.startSession(summary.chapter, SessionMode.REVIEW) }) {
                    Text("REVIEW")
                }
                TextButton(onClick = { viewModel.startSession(summary.chapter, SessionMode.PRACTICE) }) {
                    Text("PRACTICE")
                }
            }
        }
    }
}

// ---------- Practice: বড় card + progress bar + Hard/Good/Easy ----------

@Composable
fun PracticeScreen(state: UiState.Practicing, viewModel: FlashcardViewModel) {
    val card = state.queue[state.index]
    var favorited by remember(card.korean) { mutableStateOf(false) }
    var dragTotal by remember(card.korean) { mutableStateOf(0f) }
    val progress = (state.index + 1f) / state.queue.size

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.index + 1} / ${state.queue.size}",
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .pointerInput(card.korean) {
                    detectHorizontalDragGestures(
                        onDragEnd = { dragTotal = 0f },
                        onDragCancel = { dragTotal = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragTotal += dragAmount
                            if (dragTotal > 180f) {
                                // ডান দিকে swipe → পরের card
                                viewModel.skipCard()
                                dragTotal = 0f
                            } else if (dragTotal < -180f) {
                                // বাম দিকে swipe → আগের card
                                viewModel.previousCard()
                                dragTotal = 0f
                            }
                        }
                    )
                }
                .clickable { if (!state.showAnswer) viewModel.revealAnswer() }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { favorited = !favorited },
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                ) {
                    Text(if (favorited) "❤️" else "🤍", fontSize = 22.sp)
                }

                Text(
                    "👈 আগের | পরের 👉",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(card.korean, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Chapter ${card.chapter} | Box ${card.boxLevel}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (state.showAnswer) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            card.bangla,
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!state.showAnswer) {
                    IconButton(
                        onClick = { viewModel.revealAnswer() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    ) {
                        Text("🔄", fontSize = 24.sp)
                    }
                }
            }
        }

        // Hard/Good/Easy সবসময় দেখাবে — meaning না দেখেই "জানি" বললে সরাসরি চাপা যাবে
        Text(
            "শব্দটা জানা থাকলে meaning না দেখেই বেছে নাও:",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.answer(Rating.HARD) },
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F)),
                modifier = Modifier.weight(1f)
            ) { Text("Hard") }
            Button(
                onClick = { viewModel.answer(Rating.GOOD) },
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF388E3C)),
                modifier = Modifier.weight(1f)
            ) { Text("Good") }
            Button(
                onClick = { viewModel.answer(Rating.EASY) },
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1976D2)),
                modifier = Modifier.weight(1f)
            ) { Text("Easy") }
        }
    }
}

@Composable
fun FinishedScreen(state: UiState.Finished, viewModel: FlashcardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text("আজকের জন্য সব শেষ!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("✅ সঠিক: ${state.correctCount}  |  ❌ ভুল: ${state.wrongCount}")
        Spacer(Modifier.height(32.dp))
        Button(onClick = { viewModel.backToSetup() }) {
            Text("Setup এ ফিরে যাও")
        }
    }
}
