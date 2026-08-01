package com.jubaer.koreanflashcards

import android.content.Context
import android.net.Uri
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * CSV বা Excel (.xlsx) file থেকে (Korean, Bangla) pair এর list বের করে।
 * ধরে নেওয়া হয়েছে: প্রথম column Korean word, দ্বিতীয় column Bangla meaning।
 * প্রথম row যদি header হয় (Korean script নেই এমন) সেটা বাদ দেওয়া হয়।
 */
object ExcelCsvParser {

    fun parseFile(context: Context, uri: Uri, fileName: String): List<Pair<String, String>> {
        val stream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        return stream.use {
            if (fileName.endsWith(".xlsx", ignoreCase = true) || fileName.endsWith(".xls", ignoreCase = true)) {
                parseXlsx(it)
            } else {
                parseCsv(it)
            }
        }
    }

    private fun containsHangul(s: String): Boolean = s.any { it.code in 0xAC00..0xD7A3 }

    private fun parseXlsx(stream: InputStream): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        XSSFWorkbook(stream).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            var isFirst = true
            for (row in sheet) {
                val c0 = row.getCell(0)?.toString()?.trim() ?: ""
                val c1 = row.getCell(1)?.toString()?.trim() ?: ""
                if (isFirst) {
                    isFirst = false
                    if (c0.isNotEmpty() && !containsHangul(c0)) continue // header row
                }
                if (c0.isNotEmpty()) result.add(c0 to c1)
            }
        }
        return result
    }

    private fun parseCsv(stream: InputStream): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        var isFirst = true
        reader.forEachLine { rawLine ->
            val line = rawLine.removePrefix("\uFEFF") // utf-8-sig BOM থাকলে সরানো
            if (line.isNotBlank()) {
                val parts = line.split(",")
                val c0 = parts.getOrElse(0) { "" }.trim().trim('"')
                val c1 = parts.getOrElse(1) { "" }.trim().trim('"')
                if (isFirst) {
                    isFirst = false
                    if (c0.isNotEmpty() && !containsHangul(c0)) return@forEachLine // header row
                }
                if (c0.isNotEmpty()) result.add(c0 to c1)
            }
        }
        return result
    }
}
