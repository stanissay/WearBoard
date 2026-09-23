package stanissay.wear.board

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

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

    @Query("SELECT COUNT(*) FROM words")
    fun count(): Int
}

@Database(
    entities = [DictionaryWord::class],
    version = 1,
    exportSchema = false
)
abstract class DictionaryDatabase : RoomDatabase() {

    abstract fun dictionaryDao(): DictionaryDao
}