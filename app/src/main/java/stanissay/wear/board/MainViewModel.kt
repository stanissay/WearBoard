package stanissay.wear.board

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
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

                val database = createDatabase(language)

                try {
                    val dao = database.dictionaryDao()

                    val count = dao.count()
                    val first = dao.getFirstWords()
                    val last = dao.getLastWords()

                    Log.d("Dictionary", "Language: $language")
                    Log.d("Dictionary", "Total words: $count")

                    Log.d("Dictionary", "First 5:")
                    first.forEach {
                        Log.d(
                            "Dictionary",
                            "${it.word} | ${it.t9} | ${it.frequency}"
                        )
                    }

                    Log.d("Dictionary", "Last 5:")
                    last.reversed().forEach {
                        Log.d(
                            "Dictionary",
                            "${it.word} | ${it.t9} | ${it.frequency}"
                        )
                    }
                } finally {
                    database.close()
                }

                setDictionaryStatus(language, DictionaryStatus.LOADED)
            } catch (_: Exception) {
                setDictionaryStatus(language, DictionaryStatus.ERROR)
            }
        }
    }

    private fun createDatabase(language: KeyboardLayout): DictionaryDatabase {
        val name = when (language) {
            KeyboardLayout.ENGLISH -> "en.db"
            KeyboardLayout.UKRAINIAN -> "uk.db"
        }

        return Room.databaseBuilder(
            getApplication(),
            DictionaryDatabase::class.java,
            name
        ).build()
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
        val app = getApplication<Application>()

        val zipName = when (language) {
            KeyboardLayout.ENGLISH -> "en-utf8.zip"
            KeyboardLayout.UKRAINIAN -> "uk-utf8.zip"
        }

        val csvName = when (language) {
            KeyboardLayout.ENGLISH -> "en-utf8.csv"
            KeyboardLayout.UKRAINIAN -> "uk-utf8.csv"
        }

        val zipFile = File(app.filesDir, zipName)
        val csvFile = File(app.filesDir, csvName)

        unzip(zipFile, csvFile)

        val t9Map = createT9Map(
            when (language) {
                KeyboardLayout.ENGLISH -> KeyboardLayouts.english
                KeyboardLayout.UKRAINIAN -> KeyboardLayouts.ukrainian
            }
        )

        val database = createDatabase(language)

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

    fun addWordToDictionary(language: KeyboardLayout, word: String) {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val t9Map = createT9Map(
                when (language) {
                    KeyboardLayout.ENGLISH ->
                        KeyboardLayouts.english

                    KeyboardLayout.UKRAINIAN ->
                        KeyboardLayouts.ukrainian
                }
            )

            val t9 = wordToT9(cleanWord, t9Map)
            if (t9.isEmpty()) return@launch

            val database = createDatabase(language)

            try {
                database.dictionaryDao().insert(
                    DictionaryWord(
                        word = cleanWord,
                        t9 = t9,
                        frequency = 1
                    )
                )
            } finally {
                database.close()
            }
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