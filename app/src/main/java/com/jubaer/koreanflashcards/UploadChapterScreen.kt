package com.jubaer.koreanflashcards

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UploadChapterScreen(viewModel: UploadChapterViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = queryFileName(context, uri) ?: "file"
            val pairs = ExcelCsvParser.parseFile(context, uri, fileName)
            viewModel.onFileParsed(fileName, pairs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("📤 Upload Chapter", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Button(onClick = { filePicker.launch("*/*") }) {
            Text("📁 CSV/Excel ফাইল বাছো")
        }

        if (state.fileName != null) {
            Spacer(Modifier.height(8.dp))
            Text("ফাইল: ${state.fileName}")
            Text("${state.parsedPairs.size} টা row পাওয়া গেছে (raw)")
        }

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text("⚠️ ${state.error}", color = MaterialTheme.colorScheme.error)
        }

        if (state.parsedPairs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Preview (প্রথম ৫টা):", fontWeight = FontWeight.SemiBold)
            state.parsedPairs.take(5).forEach { (k, b) ->
                Text("• $k → $b", fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chapter নাম্বার: ")
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.chapterNumber.toString(),
                    onValueChange = { txt ->
                        val n = txt.toIntOrNull()
                        if (n != null && n > 0) viewModel.onChapterNumberChanged(n)
                    },
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }

            if (state.checkingExists) {
                Spacer(Modifier.height(8.dp))
                Text("চেক করা হচ্ছে...", fontSize = 12.sp)
            }

            if (state.alreadyExists) {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚠️ Chapter ${state.chapterNumber} আগে থেকেই আছে। Save করলে replace হয়ে যাবে।")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.overwriteConfirmed,
                                onCheckedChange = { viewModel.toggleOverwriteConfirmed(it) }
                            )
                            Text("হ্যাঁ, overwrite করতে চাই")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            val canSave = !state.loading && (!state.alreadyExists || state.overwriteConfirmed)
            Button(onClick = { viewModel.saveChapter() }, enabled = canSave) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Save হচ্ছে...")
                } else {
                    Text("💾 Save করো")
                }
            }
        }

        if (state.resultWords != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "✅ Chapter ${state.chapterNumber} এ ${state.resultWords!!.size} টা unique word save হয়ে গেছে",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.resultWords!!) { w ->
                    Text("• ${w.korean_word} — ${w.bangla_meaning}", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { viewModel.reset() }) {
                Text("🔄 নতুন Chapter Upload করো")
            }
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) {
            name = it.getString(nameIndex)
        }
    }
    return name
}
