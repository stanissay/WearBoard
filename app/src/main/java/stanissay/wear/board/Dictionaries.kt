package stanissay.wear.board

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(
    tableName = "words",
    indices = [
        Index(value = ["t9"])
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
    SELECT id, word, t9, frequency FROM words
    WHERE t9 LIKE :t9 || '%'
      AND t9 != :t9
    ORDER BY frequency DESC
    LIMIT 5
""")
    fun getPrefixSuggestions(t9: String): List<DictionaryWord>

    @Query("""
    SELECT id, word, t9, frequency FROM words
    WHERE t9 = :t9
    ORDER BY frequency DESC
""")
    fun getExactSuggestions(t9: String): List<DictionaryWord>

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

fun createDictionaryDatabase(language: KeyboardLayout, context: Context): DictionaryDatabase {
    val name = when (language) {
        KeyboardLayout.ENGLISH -> "en.db"
        KeyboardLayout.UKRAINIAN -> "uk.db"
    }

    return Room.databaseBuilder(
        context,
        DictionaryDatabase::class.java,
        name
    ).build()
}