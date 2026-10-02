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

import android.annotation.SuppressLint
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
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
import java.text.BreakIterator
import kotlin.time.Duration.Companion.milliseconds

class WearKeyboardService : InputMethodService() {
    private lateinit var lifecycleOwner: ImeLifecycleOwner
    private val serviceScope = CoroutineScope(Job() + Dispatchers.Main)
    private var suggestionJob: Job? = null
    private var t9TimeoutJob: Job? = null
    private var currentLayout by mutableStateOf(KeyboardLayout.ENGLISH)
    private var dictionaryDatabase: DictionaryDatabase? = null
    private var currentText by mutableStateOf("")
    private var suggestions by mutableStateOf<List<String>>(emptyList())
    private var cursorPosition by mutableIntStateOf(0)
    private var keyboardState by mutableStateOf(KeyboardState())
    private var autoShiftEnabled by mutableStateOf(false)
    private var lastShiftPressTime = 0L
    private var lastT9PressTime = 0L
    private var t9RequestId = 0L
    private var lastT9Key: Key? = null
    private var keyboardVisible = false
    private var spaceAfterSuggestion = false
    private var manualMode by mutableStateOf(false)
    private val keyboard: List<List<Key>>
        get() = if (keyboardState.symbolsMode) {
            if(keyboardState.emojiMode) KeyboardLayouts.emoji else KeyboardLayouts.symbols
        } else { KeyboardLayouts.layouts.getValue(currentLayout) }
    private val funKeyboard: List<List<Key>>
        get() = KeyboardLayouts.functions
    var enabledLanguages by mutableStateOf(KeyboardLayout.entries.toSet())
        private set

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner = ImeLifecycleOwner()
        lifecycleOwner.onCreate()
        enableAllSubtypes()
        enabledLanguages = KeyboardLayout.entries.filter { isLanguageEnabled(it) }.toSet()
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

        if(isT9Enabled()) {
            dictionaryDatabase?.close()
            dictionaryDatabase = createDictionaryDatabase(currentLayout, applicationContext)

            if (manualMode) {
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

            val requestId = ++t9RequestId

            suggestionJob?.cancel()

            suggestionJob = serviceScope.launch(Dispatchers.IO) {
                val database = dictionaryDatabase ?: return@launch
                val result = database.dictionaryDao().getSuggestions(t9, t9PrefixEnd(t9)).map { it.word }

                withContext(Dispatchers.Main) {
                    if (requestId != t9RequestId) { return@withContext }

                    suggestions = result
                }
            }
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
                        onLayoutChange = {
                            if(!keyboardState.symbolsMode) {
                                changeLanguage()
                            } else {
                                keyboardState = keyboardState.copy(
                                    emojiMode = !keyboardState.emojiMode
                                )
                            }
                        },
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

        val requestedLayout = when (subtype?.languageTag?.substringBefore("-")) {
            "uk" -> KeyboardLayout.UKRAINIAN
            "en" -> KeyboardLayout.ENGLISH
            else -> KeyboardLayout.ENGLISH
        }

        currentLayout = when {
            isLanguageEnabled(requestedLayout) -> {
                requestedLayout
            }

            isLanguageEnabled(KeyboardLayout.ENGLISH) -> {
                KeyboardLayout.ENGLISH
            }

            isLanguageEnabled(KeyboardLayout.UKRAINIAN) -> {
                KeyboardLayout.UKRAINIAN
            }

            else -> {
                KeyboardLayout.ENGLISH
            }
        }

        if (dictionaryDatabase == null && isT9Enabled()) {
            dictionaryDatabase = createDictionaryDatabase(currentLayout, applicationContext)
        }

        readVoiceResult()

        val inputType = attribute?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val inputVariation = inputType and InputType.TYPE_MASK_VARIATION

        autoShiftEnabled = inputClass == InputType.TYPE_CLASS_TEXT && inputVariation == InputType.TYPE_TEXT_VARIATION_NORMAL
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
        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(
                shift = shouldAutoShift()
            )
        }
        enabledLanguages = KeyboardLayout.entries.filter { isLanguageEnabled(it) }.toSet()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        dictionaryDatabase?.close()
        dictionaryDatabase = null
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
            is KeyAction.Normal -> handleNormal(keyAction)
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

        if (character in ".,?!;:" && spaceAfterSuggestion) {
            connection.deleteSurroundingText(1, 0)
        }

        connection.commitText(character.toString(), 1)

        spaceAfterSuggestion = false
        lastT9Key = action.key
        lastT9PressTime = now
        t9TimeoutJob?.cancel()

        t9TimeoutJob = serviceScope.launch {
            delay(MainConstants.MULTI_TAP_THRESHOLD.milliseconds)

            if (lastT9Key == action.key && !keyboardState.capsLock) {
                keyboardState = keyboardState.copy(shift = shouldAutoShift())
            }

            if (manualMode && action.character != '\'' && !action.character.isLetter()) { manualMode = false }

            lastT9Key = null
            lastT9PressTime = 0L
        }

        if (action.character != '\'') {
            suggestions = emptyList()
        }
    }

    private fun handleT9Input(key: Key) {
        val connection = currentInputConnection ?: return
        val currentWord = getCurrentWord()
        val t9 = wordToT9(currentWord) + key.code
        val firstCharacter = key.characters.firstOrNull() ?: return

        val character = when {
            keyboardState.capsLock -> firstCharacter.uppercaseChar()
            keyboardState.shift -> firstCharacter.uppercaseChar()
            else -> firstCharacter.lowercaseChar()
        }

        connection.commitText(character.toString(), 1)

        updateText()

        val requestId = ++t9RequestId

        spaceAfterSuggestion = false
        suggestionJob?.cancel()

        suggestionJob = serviceScope.launch(Dispatchers.IO) {
            val database = dictionaryDatabase ?: return@launch
            val result = database.dictionaryDao().getSuggestions(t9, t9PrefixEnd(t9)).map { it.word }

            withContext(Dispatchers.Main) {
                if (requestId != t9RequestId) { return@withContext }

                val connection = currentInputConnection ?: return@withContext
                val updatedWord = getCurrentWord()
                val finalResult = result.map {
                    when {
                        keyboardState.capsLock -> {
                            it.uppercase()
                        }

                        keyboardState.shift ||
                                currentWord.firstOrNull()?.isUpperCase() == true -> {
                            it.replaceFirstChar { char ->
                                char.uppercase()
                            }
                        }

                        else -> {
                            it
                        }
                    }
                }

                val word = finalResult.firstOrNull {
                    it.length == t9.length
                }

                if (word != null) {
                    val newWord = when {
                        keyboardState.capsLock -> {
                            word.uppercase()
                        }

                        keyboardState.shift -> {
                            word.replaceFirstChar {
                                it.uppercase()
                            }
                        }

                        updatedWord.firstOrNull()?.isUpperCase() == true -> {
                            word.replaceFirstChar {
                                it.uppercase()
                            }
                        }

                        else -> {
                            word
                        }
                    }

                    connection.deleteSurroundingText(updatedWord.length, 0)
                    connection.commitText(newWord, 1)

                    suggestions = finalResult.filter { it != word }
                } else {
                    suggestions = finalResult
                }

                if (keyboardState.shift && !keyboardState.capsLock) {
                    keyboardState = keyboardState.copy(shift = shouldAutoShift())
                }

                updateText()
            }
        }
    }

    private fun handleLongPress(action: KeyAction.LongPress) {
        val connection = currentInputConnection ?: return

        spaceAfterSuggestion = false

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

        spaceAfterSuggestion = false

        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(
                shift = shouldAutoShift()
            )
        }
    }

    private fun handleNormal(action: KeyAction.Normal) {
        currentInputConnection?.commitText(action.key.code, 1)

        suggestions = emptyList()
        spaceAfterSuggestion = false

        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(shift = shouldAutoShift())
        }
    }

    private fun handleFunctionCharacter(action: KeyAction.Character, connection: InputConnection) {
        spaceAfterSuggestion = false

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

        t9RequestId++

        deleteBeforeCursor(connection)

        updateText()

        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(
                shift = shouldAutoShift()
            )
        }

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

        val requestId = t9RequestId

        spaceAfterSuggestion = false
        suggestionJob?.cancel()

        suggestionJob = serviceScope.launch(Dispatchers.IO) {
            val database = dictionaryDatabase ?: return@launch
            val result = database.dictionaryDao().getSuggestions(t9, t9PrefixEnd(t9)).map { it.word }

            withContext(Dispatchers.Main) {
                if (requestId != t9RequestId) { return@withContext }

                val connection = currentInputConnection ?: return@withContext
                val currentWord = getCurrentWord()
                val finalResult = result.map {
                    when {
                        keyboardState.capsLock -> {
                            it.uppercase()
                        }

                        keyboardState.shift ||
                                currentWord.firstOrNull()?.isUpperCase() == true -> {
                            it.replaceFirstChar { char ->
                                char.uppercase()
                            }
                        }

                        else -> {
                            it
                        }
                    }
                }
                val word = finalResult.firstOrNull { it.length == currentWord.length }

                if (word != null) {
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

                    connection.deleteSurroundingText(currentWord.length, 0)

                    connection.commitText(newWord, 1)

                    suggestions = finalResult.filter { it != word }
                } else {
                    suggestions = finalResult
                }

                updateText()
            }
        }
    }

    private fun deleteBeforeCursor(connection: InputConnection) {
        val text = connection.getTextBeforeCursor(100, 0)?.toString() ?: return
        if (text.isEmpty()) return

        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        iterator.last()

        val start = iterator.previous()

        if (start >= 0) {
            connection.deleteSurroundingText(text.length - start, 0)
        }
    }

    private fun handleSpace() {
        currentInputConnection?.commitText(" ", 1)
        if (manualMode) manualMode = false
        suggestions = emptyList()
        spaceAfterSuggestion = false

        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(
                shift = shouldAutoShift()
            )
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
        spaceAfterSuggestion = false

        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(
                shift = shouldAutoShift()
            )
        }
    }

    private fun handleShift() {
        val now = System.currentTimeMillis()

        keyboardState = if (keyboardState.capsLock) {
            keyboardState.copy(
                shift = shouldAutoShift(),
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
        spaceAfterSuggestion = false
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
        val currentLanguage = imm.currentInputMethodSubtype
            ?.languageTag?.substringBefore("-") ?: return

        spaceAfterSuggestion = false

        val languages = enabledLanguages.toList()

        if (languages.size < 2) return

        val currentLayout = languages.firstOrNull { it.languageTag == currentLanguage } ?: return
        val currentIndex = languages.indexOf(currentLayout)
        val targetLayout = languages[(currentIndex + 1) % languages.size]
        val subtype = (0 until info.subtypeCount)
            .map { info.getSubtypeAt(it) }
            .firstOrNull {
                it.languageTag.substringBefore("-") ==
                        targetLayout.languageTag
            } ?: return

        switchInputMethod(info.id, subtype)
    }

    private fun isLanguageEnabled(language: KeyboardLayout): Boolean {
        return getSharedPreferences(MainConstants.PREFS, MODE_PRIVATE).getBoolean(
            "language_enabled_${language.name}", true
        )
    }

    private fun moveCursor(direction: Int) {
        val connection = currentInputConnection ?: return
        val newPosition = (cursorPosition + direction).coerceIn(0, currentText.length)

        if (newPosition == cursorPosition) return

        connection.setSelection(newPosition, newPosition)

        updateText()

        spaceAfterSuggestion = false
        suggestionJob?.cancel()
        t9RequestId++

        if (!isT9Enabled() || manualMode) {
            suggestions = emptyList()
            return
        }

        val requestId = t9RequestId

        suggestionJob = serviceScope.launch {
            delay(250.milliseconds)
            val currentWord = getCurrentWord()

            if (currentWord.isEmpty()) {
                suggestions = emptyList()
                return@launch
            }

            val t9 = wordToT9(currentWord)

            if (t9.isEmpty()) {
                suggestions = emptyList()
                return@launch
            }

            val database = dictionaryDatabase ?: return@launch

            val result = withContext(Dispatchers.IO) {
                database.dictionaryDao().getSuggestions(t9, t9PrefixEnd(t9)).map { it.word }
            }
            val finalResult = result.map {
                when {
                    keyboardState.capsLock -> {
                        it.uppercase()
                    }

                    keyboardState.shift ||
                            currentWord.firstOrNull()?.isUpperCase() == true -> {
                        it.replaceFirstChar { char ->
                            char.uppercase()
                        }
                    }

                    else -> {
                        it
                    }
                }
            }

            if (requestId != t9RequestId) return@launch

            suggestions = finalResult
        }
    }

    private fun showKeyboardPicker() {
        spaceAfterSuggestion = false
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    private fun onAbc() {
        val connection = currentInputConnection ?: return
        val currentWord = getCurrentWord()

        if (currentWord.isNotEmpty()) {
            connection.deleteSurroundingText(currentWord.length, 0)
        }

        spaceAfterSuggestion = false
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
            val database = dictionaryDatabase ?: return@launch

            database.dictionaryDao().insertOrIncrement(
                DictionaryWord(
                    word = word,
                    t9 = t9,
                    frequency = 1
                )
            )

            withContext(Dispatchers.Main) {
                spaceAfterSuggestion = false
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

        while (
            start > 0 &&
            (text[start - 1].isLetter() || text[start - 1] == '\'')
        ) { start-- }

        return text.substring(start, cursor)
    }

    private fun t9PrefixEnd(t9: String): String {
        val chars = t9.toCharArray()

        for (i in chars.lastIndex downTo 0) {
            if (chars[i] < '9') {
                chars[i]++
                return String(chars, 0, i + 1)
            }
        }

        return t9 + '\uFFFF'
    }

    private fun selectSuggestion(word: String) {
        val connection = currentInputConnection ?: return
        val currentWord = getCurrentWord()

        if (currentWord.isNotEmpty()) {
            connection.deleteSurroundingText(currentWord.length, 0)
        }

        connection.commitText("$word ", 1)

        spaceAfterSuggestion = true
        suggestions = emptyList()

        if (!keyboardState.capsLock) {
            keyboardState = keyboardState.copy(shift = false)
        }

        serviceScope.launch(Dispatchers.IO) {
            val database = dictionaryDatabase ?: return@launch

            database.dictionaryDao().incrementFrequency(word)
        }

        updateText()
    }

    private fun wordToT9(word: String): String {
        val layout = when (currentLayout) {
            KeyboardLayout.ENGLISH -> KeyboardLayouts.english
            KeyboardLayout.UKRAINIAN -> KeyboardLayouts.ukrainian
        }

        val t9Map = layout.flatten()
            .filter { it.type == KeyType.T9 }
            .flatMap { key ->
                key.characters.filter { it.isLetter() }
                    .map { it.lowercaseChar() to key.code.first() }
            }.toMap()

        return buildString(word.length) {
            for (character in word) {
                if (character == '\'') continue
                val digit = t9Map[character.lowercaseChar()] ?: return ""
                append(digit)
            }
        }
    }

    private fun shouldAutoShift(): Boolean {
        if (!autoShiftEnabled) return false
        val connection = currentInputConnection ?: return false
        val beforeCursor = connection.getTextBeforeCursor(100, 0)?.toString() ?: return true
        val text = beforeCursor.trimEnd()

        return text.isEmpty() || text.last() == '.' || text.last() == '!' || text.last() == '?'
    }
}