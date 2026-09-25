/*
 * Round Keyboard for Wear OS
 * Copyright (C) 2026 [stanissay]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package stanissay.wear.board

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class MainViewModel (app: Application) : AndroidViewModel(app) {
    var showDictionaries by mutableStateOf(false)

    fun downloadDictionary(language: KeyboardLayout) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                setDictionaryStatus(language, DictionaryStatus.DOWNLOADING)

                val dbName = when (language) {
                    KeyboardLayout.ENGLISH -> "en.db"
                    KeyboardLayout.UKRAINIAN -> "uk.db"
                }

                application.deleteDatabase(dbName)

                val url = when (language) {
                    KeyboardLayout.ENGLISH -> MainConstants.EN_DIC
                    KeyboardLayout.UKRAINIAN -> MainConstants.UK_DIC
                }

                val fileName = when (language) {
                    KeyboardLayout.ENGLISH -> "en-utf8.zip"
                    KeyboardLayout.UKRAINIAN -> "uk-utf8.zip"
                }

                val file = File(
                    getApplication<Application>().filesDir,
                    fileName
                )

                val connection = URL(url).openConnection() as HttpURLConnection

                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 60_000
                    connection.requestMethod = "GET"

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException(
                            "HTTP ${connection.responseCode}"
                        )
                    }

                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                setDictionaryStatus(language, DictionaryStatus.IMPORTING)

                importDictionary(language)

                setDictionaryStatus(language, DictionaryStatus.LOADED)
            } catch (_: Exception) {
                setDictionaryStatus(language, DictionaryStatus.ERROR)
            }
        }
    }

    private fun unzip(zipFile: File, csvFile: File) {
        ZipInputStream(
            BufferedInputStream(
                FileInputStream(zipFile)
            )
        ).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break

                if (!entry.isDirectory) {
                    csvFile.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                    return
                }
            }
        }
    }

    private fun importDictionary(language: KeyboardLayout) {
        val zipName = when (language) {
            KeyboardLayout.ENGLISH -> "en-utf8.zip"
            KeyboardLayout.UKRAINIAN -> "uk-utf8.zip"
        }

        val csvName = when (language) {
            KeyboardLayout.ENGLISH -> "en-utf8.csv"
            KeyboardLayout.UKRAINIAN -> "uk-utf8.csv"
        }

        val zipFile = File(application.filesDir, zipName)
        val csvFile = File(application.filesDir, csvName)

        unzip(zipFile, csvFile)

        val t9Map = createT9Map(
            when (language) {
                KeyboardLayout.ENGLISH -> KeyboardLayouts.english
                KeyboardLayout.UKRAINIAN -> KeyboardLayouts.ukrainian
            }
        )

        val database = createDictionaryDatabase(language, application)

        try {
            database.runInTransaction {
                csvFile.bufferedReader(Charsets.UTF_8).use { reader ->
                    val batch = ArrayList<DictionaryWord>(500)

                    reader.forEachLine { line ->
                        val parts = line.split('\t')

                        val word = parts.firstOrNull()?.removePrefix("'")?.trim() ?: return@forEachLine
                        if (word.isEmpty()) return@forEachLine

                        val t9 = wordToT9(word, t9Map)
                        if (t9.isEmpty()) return@forEachLine

                        val frequency = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 10

                        batch += DictionaryWord(
                            word = word,
                            t9 = t9,
                            frequency = frequency
                        )

                        if (batch.size >= 500) {
                            database.dictionaryDao().insertAll(batch)
                            batch.clear()
                        }
                    }

                    if (batch.isNotEmpty()) {
                        database.dictionaryDao().insertAll(batch)
                    }
                }
            }

            csvFile.delete()
            zipFile.delete()
        } finally { database.close() }
    }

    private fun createT9Map(layout: List<List<Key>>): Map<Char, Char> {
        return layout
            .flatten()
            .filter { it.type == KeyType.T9 }
            .flatMap { key ->
                key.characters
                    .filter { it.isLetter() }
                    .map { character ->
                        character.lowercaseChar() to key.code.first()
                    }
            }
            .toMap()
    }

    private fun wordToT9(word: String, t9Map: Map<Char, Char>): String {
        return buildString(word.length) {
            for (character in word) {
                val digit = t9Map[character.lowercaseChar()]
                    ?: return ""
                append(digit)
            }
        }
    }

    private val _dictionaryStatus =
        MutableStateFlow(
            mapOf(
                KeyboardLayout.ENGLISH to DictionaryStatus.NOT_LOADED,
                KeyboardLayout.UKRAINIAN to DictionaryStatus.NOT_LOADED
            )
        )
    val dictionaryStatus: StateFlow<Map<KeyboardLayout, DictionaryStatus>> = _dictionaryStatus.asStateFlow()

    private fun checkDictionaries() {
        val app = getApplication<Application>()

        _dictionaryStatus.value = mapOf(
            KeyboardLayout.ENGLISH to if (app.getDatabasePath("en.db").exists()) {
                DictionaryStatus.LOADED
            } else { DictionaryStatus.NOT_LOADED },

            KeyboardLayout.UKRAINIAN to if (app.getDatabasePath("uk.db").exists()) {
                DictionaryStatus.LOADED
            } else { DictionaryStatus.NOT_LOADED }
        )
    }

    private fun setDictionaryStatus(language: KeyboardLayout, status: DictionaryStatus) {
        _dictionaryStatus.update {
            it + (language to status)
        }
    }

    var useT9 by mutableStateOf(
        getApplication<Application>()
            .getSharedPreferences(MainConstants.PREFS, Context.MODE_PRIVATE)
            .getBoolean(MainConstants.USE_T9, true)
    )
        private set

    fun updateUseT9(value: Boolean) {
        useT9 = value

        getApplication<Application>()
            .getSharedPreferences(
                MainConstants.PREFS,
                Context.MODE_PRIVATE
            )
            .edit {
                putBoolean(MainConstants.USE_T9, value)
            }
    }

    init {
        checkDictionaries()
    }
}