package stanissay.wear.board

import android.annotation.SuppressLint
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
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
    private var manualMode by mutableStateOf(false)
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
                        manualMode = manualMode,
                        isT9Enabled = isT9Enabled(),
                        onKeyAction = ::handleKey,
                        onLongClick = ::showKeyboardPicker,
                        onSuggestionClick = { selectSuggestion(it) },
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

    private fun handleKey(keyAction: KeyAction) {
        when (keyAction) {
            is KeyAction.Character -> handleCharacter(keyAction)
            is KeyAction.LongPress -> handleLongPress(keyAction)
            is KeyAction.Function -> handleFunction(keyAction)
        }

        updateText()
    }

    private fun handleCharacter(action: KeyAction.Character) {
        val connection = currentInputConnection ?: return

        when (action.key.type) {
            KeyType.T9 -> handleT9Character(action, connection)
            KeyType.NORMAL -> handleNormalCharacter(action, connection)
            KeyType.FUNCTION -> handleFunctionCharacter(action, connection)
        }
    }

    private fun handleT9Character(action: KeyAction.Character, connection: InputConnection) {
        if (isT9Enabled() && !manualMode && action.key.characters.any { it.isLetter() }) {
            handleT9Input(action.key)
            return
        }

        handleMultiTap(action, connection)

        if (manualMode && action.key.characters.none { it.isLetter() }) {
            manualMode = false
        }
    }

    private fun handleMultiTap(action: KeyAction.Character, connection: InputConnection) {
        val now = System.currentTimeMillis()
        val sameKey = action.key == lastT9Key
        val sequenceActive = sameKey && now - lastT9PressTime <= MainConstants.MULTI_TAP_THRESHOLD

        if (!sequenceActive &&
            lastT9Key != null &&
            !keyboardState.capsLock
        ) { keyboardState = keyboardState.copy(shift = false) }

        if (action.isMultiTap) {
            connection.deleteSurroundingText(1, 0)
        }

        val uppercase = keyboardState.shift || keyboardState.capsLock

        val character = if (uppercase) {
            action.character.uppercaseChar()
        } else { action.character.lowercaseChar() }

        connection.commitText(character.toString(), 1)

        lastT9Key = action.key
        lastT9PressTime = now
        t9TimeoutJob?.cancel()

        t9TimeoutJob = serviceScope.launch {
            delay(MainConstants.MULTI_TAP_THRESHOLD.milliseconds)

            if (lastT9Key == action.key && !keyboardState.capsLock) {
                keyboardState = keyboardState.copy(shift = false)
            }

            lastT9Key = null
            lastT9PressTime = 0L
        }

        suggestions = emptyList()
    }

    private fun handleT9Input(key: Key) {
        val currentWord = getCurrentWord()
        val t9 = wordToT9(currentWord) + key.code
        val firstCharacter = key.characters.firstOrNull() ?: return

        suggestionJob?.cancel()

        suggestionJob = serviceScope.launch(Dispatchers.IO) {
            val database = createDictionaryDatabase(currentLayout, applicationContext)

            try {
                val exactMatches = database.dictionaryDao().getExactSuggestions(t9)
                val prefixMatches = database.dictionaryDao().getPrefixSuggestions(t9)
                val result = (exactMatches + prefixMatches).map { it.word }

                withContext(Dispatchers.Main) {
                    val connection = currentInputConnection ?: return@withContext
                    val word = exactMatches.firstOrNull()?.word ?: firstCharacter.toString()

                    val newWord = when {
                        keyboardState.capsLock -> {
                            word.uppercase()
                        }

                        keyboardState.shift -> {
                            word.replaceFirstChar {
                                it.uppercase()
                            }
                        }

                        currentWord.firstOrNull()?.isUpperCase() == true -> {
                            word.replaceFirstChar {
                                it.uppercase()
                            }
                        }

                        else -> {
                            word
                        }
                    }

                    if (currentWord.isNotEmpty()) {
                        connection.deleteSurroundingText(currentWord.length, 0)
                    }

                    connection.commitText(newWord, 1)

                    suggestions = result

                    if (keyboardState.shift && !keyboardState.capsLock) {
                        keyboardState = keyboardState.copy(shift = false)
                    }

                    updateText()
                }
            } finally {
                database.close()
            }
        }
    }

    private fun handleLongPress(action: KeyAction.LongPress) {
        val connection = currentInputConnection ?: return

        when (action.key.type) {
            KeyType.T9,
            KeyType.NORMAL -> {
                connection.commitText(action.key.code, 1)
                suggestions = emptyList()
                if (manualMode) manualMode = false
            }

            KeyType.FUNCTION -> Unit
        }
    }

    private fun handleNormalCharacter(action: KeyAction.Character, connection: InputConnection) {
        val uppercase = keyboardState.shift || keyboardState.capsLock

        val character =
            if (uppercase) {
                action.character.uppercaseChar()
            } else {
                action.character.lowercaseChar()
            }

        connection.commitText(character.toString(), 1)
    }

    private fun handleFunctionCharacter(action: KeyAction.Character, connection: InputConnection) {
        when (action.key.code) {
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

    private fun handleFunction(action: KeyAction.Function) {
        when (action.key.code) {
            MainFunctions.DELETE -> handleDelete()
            MainFunctions.SPACE -> handleSpace()
            MainFunctions.ENTER -> handleEnter()
            MainFunctions.SHIFT -> handleShift()
            MainFunctions.ABC -> onAbc()
            MainFunctions.ADD -> saveWord()
            MainFunctions.VOICE -> startVoiceInput()
            MainFunctions.SETTINGS -> openSettings()
        }
    }

    private fun handleDelete() {
        val connection = currentInputConnection ?: return

        connection.deleteSurroundingText(1, 0)

        updateText()

        if (manualMode || !isT9Enabled()) {
            suggestions = emptyList()
            return
        }

        val currentWord = getCurrentWord()

        if (currentWord.isEmpty()) {
            suggestions = emptyList()
            return
        }

        val t9 = wordToT9(currentWord)

        if (t9.isEmpty()) {
            suggestions = emptyList()
            return
        }

        suggestionJob?.cancel()

        suggestionJob = serviceScope.launch(Dispatchers.IO) {
            val database = createDictionaryDatabase(currentLayout, applicationContext)

            try {
                val exactMatches = database.dictionaryDao().getExactSuggestions(t9)
                val prefixMatches = database.dictionaryDao().getPrefixSuggestions(t9)
                val result = (exactMatches + prefixMatches).map { it.word }

                withContext(Dispatchers.Main) {
                    suggestions = result
                }
            } finally {
                database.close()
            }
        }
    }

    private fun handleSpace() {
        currentInputConnection?.commitText(" ", 1)
        if (manualMode) manualMode = false
        suggestions = emptyList()

        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(shift = false)
        }
    }

    private fun handleEnter() {
        val connection = currentInputConnection ?: return

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

        if (manualMode) manualMode = false
        suggestions = emptyList()
    }

    private fun handleShift() {
        val now = System.currentTimeMillis()

        keyboardState = if (keyboardState.capsLock) {
            keyboardState.copy(
                shift = false,
                capsLock = false
            )
        } else if (
            now - lastShiftPressTime <= MainConstants.DOUBLE_TAP_THRESHOLD
        ) {
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

    @SuppressLint("WearRecents")
    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(intent)
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

    private fun onAbc() {
        val connection = currentInputConnection ?: return
        val currentWord = getCurrentWord()

        if (currentWord.isNotEmpty()) {
            connection.deleteSurroundingText(currentWord.length, 0)
        }

        suggestions = emptyList()
        lastT9Key = null
        lastT9PressTime = 0L
        t9TimeoutJob?.cancel()
        manualMode = true

        updateText()
    }

    private fun saveWord() {
        val word = getCurrentWord()

        if (word.isEmpty()) {
            manualMode = false
            return
        }

        val t9 = wordToT9(word)

        if (t9.isEmpty()) {
            manualMode = false
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val database = createDictionaryDatabase(currentLayout, applicationContext)

            try {
                database.dictionaryDao().insertOrIncrement(
                    DictionaryWord(
                        word = word,
                        t9 = t9,
                        frequency = 1
                    )
                )
            } finally {
                database.close()
            }

            withContext(Dispatchers.Main) {
                manualMode = false
                suggestions = emptyList()
            }
        }
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

    private fun selectSuggestion(word: String) {
        val connection = currentInputConnection ?: return
        val currentWord = getCurrentWord()

        if (currentWord.isNotEmpty()) {
            connection.deleteSurroundingText(currentWord.length, 0)
        }

        connection.commitText(word, 1)

        suggestions = emptyList()

        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(shift = false)
        }

        serviceScope.launch(Dispatchers.IO) {
            val database = createDictionaryDatabase(currentLayout, applicationContext)

            try {
                database.dictionaryDao().incrementFrequency(word)
            } finally {
                database.close()
            }
        }

        updateText()
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