package stanissay.wear.board

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
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

                importDictionary(language)
            } finally {
                connection.disconnect()
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

    private fun unzip(
        zipFile: File,
        csvFile: File
    ) {
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

                        val word = parts.firstOrNull()
                            ?.removePrefix("'")
                            ?.trim()
                            ?: return@forEachLine

                        if (word.isEmpty()) {
                            return@forEachLine
                        }

                        val t9 = wordToT9(word, t9Map)

                        if (t9.isEmpty()) {
                            return@forEachLine
                        }

                        val frequency = parts
                            .getOrNull(1)
                            ?.trim()
                            ?.toIntOrNull()
                            ?: 10

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
        } finally {
            database.close()
        }
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

    private fun wordToT9(
        word: String,
        t9Map: Map<Char, Char>
    ): String {
        return buildString(word.length) {
            for (character in word) {
                val digit = t9Map[character.lowercaseChar()]
                    ?: return ""
                append(digit)
            }
        }
    }

    fun isDictionaryLoaded(language: KeyboardLayout): Boolean {
        val fileName = when (language) {
            KeyboardLayout.ENGLISH -> "en.db"
            KeyboardLayout.UKRAINIAN -> "uk.db"
        }

        return getApplication<Application>().getDatabasePath(fileName).exists()
    }
}