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

import androidx.room.*

@Entity(
    tableName = "words",
    indices = [
        Index(
            value = ["t9", "frequency"]
        )
    ]
)
data class DictionaryWord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val t9: String,
    val frequency: Int
)

@Dao
interface DictionaryDao {

    @Insert
    fun insertAll(words: List<DictionaryWord>)

    @Query("""
    SELECT * FROM words
    WHERE word = :word
    LIMIT 1
""")
    fun getWord(word: String): DictionaryWord?

    @Insert
    fun insert(word: DictionaryWord)

    @Query("""
    UPDATE words
    SET frequency = frequency + 1
    WHERE word = :word
""")
    fun incrementFrequency(word: String)

    @Transaction
    fun insertOrIncrement(word: DictionaryWord) {
        val existing = getWord(word.word)

        if (existing == null) {
            insert(word)
        } else {
            incrementFrequency(word.word)
        }
    }

    @Query("""
    WITH exact AS (
        SELECT id, word, t9, frequency, 0 AS priority
        FROM words
        WHERE t9 = :t9
    ),
    prefix AS (
        SELECT id, word, t9, frequency, 1 AS priority
        FROM words
        WHERE t9 >= :t9
          AND t9 < :prefixEnd
          AND t9 != :t9
        ORDER BY frequency DESC
        LIMIT 5
    )
    SELECT id, word, t9, frequency
    FROM (
        SELECT * FROM exact
        UNION ALL
        SELECT * FROM prefix
    )
    ORDER BY priority, frequency DESC
""")
    fun getSuggestions(t9: String, prefixEnd: String): List<DictionaryWord>

    @Query("SELECT COUNT(*) FROM words")
    fun count(): Int

    @Query("SELECT * FROM words ORDER BY id ASC LIMIT 5")
    fun getFirstWords(): List<DictionaryWord>

    @Query("SELECT * FROM words ORDER BY id DESC LIMIT 5")
    fun getLastWords(): List<DictionaryWord>
}

@Database(
    entities = [DictionaryWord::class],
    version = 1,
    exportSchema = false
)
abstract class DictionaryDatabase : RoomDatabase() {

    abstract fun dictionaryDao(): DictionaryDao
}