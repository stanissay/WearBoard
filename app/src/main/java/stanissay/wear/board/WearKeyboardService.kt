package stanissay.wear.board

import android.annotation.SuppressLint
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.room.Room
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class WearKeyboardService : InputMethodService() {
    private lateinit var lifecycleOwner: ImeLifecycleOwner
    private val serviceScope = CoroutineScope(Job() + Dispatchers.Main)
    private var suggestionJob: Job? = null
    private var t9TimeoutJob: Job? = null
    private var currentLayout by mutableStateOf(KeyboardLayout.ENGLISH)
    private var currentText by mutableStateOf("")
    private var suggestions by mutableStateOf<List<String>>(emptyList())
    private var cursorPosition by mutableIntStateOf(0)
    private var keyboardState by mutableStateOf(KeyboardState())
    private var lastShiftPressTime = 0L
    private var lastT9PressTime = 0L
    private var lastT9Key: Key? = null
    private var keyboardVisible = false
    private val keyboard: List<List<Key>>
        get() = if (keyboardState.symbolsMode) { KeyboardLayouts.symbols
        } else { KeyboardLayouts.layouts.getValue(currentLayout) }
    private val funKeyboard: List<List<Key>>
        get() = KeyboardLayouts.functions

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner = ImeLifecycleOwner()
        lifecycleOwner.onCreate()
        enableAllSubtypes()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return true
    }

    override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(subtype)

        currentLayout = when (subtype.languageTag.substringBefore("-")) {
            "en" -> KeyboardLayout.ENGLISH
            "uk" -> KeyboardLayout.UKRAINIAN
            else -> KeyboardLayout.ENGLISH
        }
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(lifecycleOwner)
            decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        }

        return ComposeView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true

            setContent {
                MainTheme {
                    KeyboardScreen(
                        keyboard = keyboard,
                        funKeyboard = funKeyboard,
                        text = currentText,
                        cursorPosition = cursorPosition,
                        keyboardState = keyboardState,
                        suggestions = suggestions,
                        onKeyAction = ::handleKey,
                        onLongClick = ::showKeyboardPicker,
                        onSuggestionClick = {},
                        onLangChange = { changeLanguage() },
                        onCloseKeyboard = { requestHideSelf(0) },
                        onExtended = {
                            keyboardState = keyboardState.copy(
                                symbolsMode = !keyboardState.symbolsMode
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            event.source and InputDevice.SOURCE_ROTARY_ENCODER ==
            InputDevice.SOURCE_ROTARY_ENCODER &&
            event.action == MotionEvent.ACTION_SCROLL &&
            keyboardVisible
        ) {
            val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)

            if (delta != 0f) {
                moveCursor(if (delta > 0f) -1 else 1)
            }

            return true
        }

        return super.onGenericMotionEvent(event)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        val subtype = getSystemService(InputMethodManager::class.java)
            .currentInputMethodSubtype

        currentLayout = when (subtype?.languageTag?.substringBefore("-")) {
            "uk" -> KeyboardLayout.UKRAINIAN
            "en" -> KeyboardLayout.ENGLISH
            else -> KeyboardLayout.ENGLISH
        }

        readVoiceResult()
    }

    override fun onWindowShown() {
        super.onWindowShown()

        readVoiceResult()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardVisible = true
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()
        updateText()
        readVoiceResult()
    }

    override fun onFinishInputView(finishing: Boolean) {
        super.onFinishInputView(finishing)
        keyboardVisible = false
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleOwner.onDestroy()
    }

    @SuppressLint("WearRecents")
    private fun handleKey(keyAction: KeyAction) {
        val connection = currentInputConnection ?: return

        when (keyAction) {
            is KeyAction.Character -> {
                val uppercase = keyboardState.shift || keyboardState.capsLock

                when (keyAction.key.type) {
                    KeyType.T9 -> {
                        if (!isT9Enabled() || !keyAction.key.characters.any { it.isLetter() }) {
                            val now = System.currentTimeMillis()
                            val sameKey = keyAction.key == lastT9Key
                            val sequenceActive = sameKey && now - lastT9PressTime <= MainConstants.MULTI_TAP_THRESHOLD

                            if (!sequenceActive && lastT9Key != null && !keyboardState.capsLock) {
                                keyboardState = keyboardState.copy(shift = false)
                            }

                            if (keyAction.isMultiTap) {
                                connection.deleteSurroundingText(1, 0)
                            }

                            val uppercase = keyboardState.shift || keyboardState.capsLock

                            val character =
                                if (uppercase) {
                                    keyAction.character.uppercaseChar()
                                } else {
                                    keyAction.character.lowercaseChar()
                                }

                            connection.commitText(character.toString(), 1)

                            lastT9Key = keyAction.key
                            lastT9PressTime = now
                            t9TimeoutJob?.cancel()

                            t9TimeoutJob = serviceScope.launch {
                                delay(MainConstants.MULTI_TAP_THRESHOLD.milliseconds)

                                if (lastT9Key == keyAction.key && !keyboardState.capsLock) {
                                    keyboardState = keyboardState.copy(shift = false)
                                }

                                lastT9Key = null
                                lastT9PressTime = 0L
                            }
                        } else {
                            updateSuggestions(keyAction.key)
                        }
                    }

                    KeyType.NORMAL -> {
                        val character =
                            if (uppercase) { keyAction.character.uppercaseChar()
                            } else { keyAction.character.lowercaseChar() }

                        connection.commitText(character.toString(), 1)
                    }

                    KeyType.FUNCTION -> {
                        when (keyAction.key.code) {
                            MainFunctions.DELETE -> {
                                connection.deleteSurroundingText(1, 0)
                            }

                            MainFunctions.SPACE -> {
                                connection.commitText(" ", 1)

                                if (!keyboardState.capsLock) {
                                    keyboardState = keyboardState.copy(shift = false)
                                }
                            }
                        }
                    }
                }
            }

            is KeyAction.LongPress -> {
                when (keyAction.key.type) {
                    KeyType.T9, KeyType.NORMAL -> {
                        connection.commitText(keyAction.key.code, 1)
                    }

                    KeyType.FUNCTION -> Unit
                }
            }

            is KeyAction.Function -> {
                when (keyAction.key.code) {
                    MainFunctions.DELETE -> {
                        connection.deleteSurroundingText(1, 0)
                    }

                    MainFunctions.SPACE -> {
                        connection.commitText(" ", 1)
                    }

                    MainFunctions.ENTER -> {
                        connection.sendKeyEvent(
                            KeyEvent(
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_ENTER
                            )
                        )

                        connection.sendKeyEvent(
                            KeyEvent(
                                KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_ENTER
                            )
                        )
                    }

                    MainFunctions.SHIFT -> {
                        val now = System.currentTimeMillis()

                        keyboardState = if (keyboardState.capsLock) {
                            keyboardState.copy(
                                shift = false,
                                capsLock = false
                            )
                        } else if (now - lastShiftPressTime <= MainConstants.DOUBLE_TAP_THRESHOLD) {
                            keyboardState.copy(
                                shift = false,
                                capsLock = true
                            )
                        } else {
                            keyboardState.copy(
                                shift = !keyboardState.shift
                            )
                        }

                        lastShiftPressTime = now
                    }

                    MainFunctions.VOICE -> { startVoiceInput() }

                    MainFunctions.SETTINGS -> {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        startActivity(intent)
                    }
                }
            }
        }

        updateText()
    }

    private fun updateText() {
        val connection = currentInputConnection ?: return
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)

        if (extracted != null) {
            currentText = extracted.text?.toString() ?: ""
            cursorPosition = extracted.selectionStart
        }
    }

    private fun changeLanguage() {
        val imm = getSystemService(InputMethodManager::class.java)
        val info = imm.currentInputMethodInfo ?: return
        val currentSubtype = imm.currentInputMethodSubtype
        val currentLanguage = currentSubtype?.languageTag

        val targetLanguage = when (currentLanguage) {
            "en" -> "uk"
            "uk" -> "en"
            else -> return
        }

        val subtypes = (0 until info.subtypeCount).map { info.getSubtypeAt(it) }
        val subtype = subtypes.firstOrNull { it.languageTag == targetLanguage } ?: return

        switchInputMethod(info.id, subtype)
    }

    private fun moveCursor(direction: Int) {
        val connection = currentInputConnection ?: return
        val newPosition = (cursorPosition + direction).coerceIn(0, currentText.length)

        if (newPosition == cursorPosition) return

        connection.setSelection(newPosition, newPosition)

        updateText()
    }

    private fun showKeyboardPicker() {
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    @SuppressLint("WearRecents")
    private fun startVoiceInput() {
        val language = when (currentLayout) {
            KeyboardLayout.UKRAINIAN -> "uk-UA"
            KeyboardLayout.ENGLISH -> "en-US"
        }

        getSharedPreferences(MainConstants.PREFS, MODE_PRIVATE).edit {
            remove(MainConstants.RESULT_TEXT)
        }

        val intent = Intent(this, VoiceBridgeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainConstants.EXTRA_LANGUAGE, language)
        }

        startActivity(intent)
    }

    private fun readVoiceResult() {
        val prefs = getSharedPreferences(MainConstants.PREFS, MODE_PRIVATE)
        val result = prefs.getString(MainConstants.RESULT_TEXT, null)

        if (!result.isNullOrEmpty()) {
            prefs.edit {
                remove(MainConstants.RESULT_TEXT)
            }

            currentInputConnection?.commitText(result, 1)

            updateText()
        }
    }

    private fun isT9Enabled(): Boolean {
        return getSharedPreferences(MainConstants.PREFS, MODE_PRIVATE)
            .getBoolean(MainConstants.USE_T9, true)
    }

    private fun getCurrentWord(): String {
        val text = currentText
        val cursor = cursorPosition.coerceIn(0, text.length)
        var start = cursor

        while (start > 0 && text[start - 1].isLetter()) {
            start--
        }

        return text.substring(start, cursor)
    }

    private fun updateSuggestions(key: Key) {
        val currentWord = getCurrentWord()
        val t9 = wordToT9(currentWord) + key.code

        suggestionJob?.cancel()

        suggestionJob = serviceScope.launch(Dispatchers.IO) {
            val database = createDictionaryDatabase(currentLayout)

            try {
                val result = database.dictionaryDao()
                    .getSuggestions(t9)
                    .map { it.word }

                withContext(Dispatchers.Main) {
                    suggestions = result
                }
            } finally {
                database.close()
            }
        }
    }

    private fun createDictionaryDatabase(
        language: KeyboardLayout
    ): DictionaryDatabase {
        val name = when (language) {
            KeyboardLayout.ENGLISH -> "en.db"
            KeyboardLayout.UKRAINIAN -> "uk.db"
        }

        return Room.databaseBuilder(
            applicationContext,
            DictionaryDatabase::class.java,
            name
        ).build()
    }

    private fun wordToT9(word: String): String {
        val layout = when (currentLayout) {
            KeyboardLayout.ENGLISH -> KeyboardLayouts.english
            KeyboardLayout.UKRAINIAN -> KeyboardLayouts.ukrainian
        }

        val t9Map = layout
            .flatten()
            .filter { it.type == KeyType.T9 }
            .flatMap { key ->
                key.characters
                    .filter { it.isLetter() }
                    .map { it.lowercaseChar() to key.code.first() }
            }
            .toMap()

        return buildString(word.length) {
            for (character in word) {
                val digit = t9Map[character.lowercaseChar()]
                    ?: return ""
                append(digit)
            }
        }
    }
}