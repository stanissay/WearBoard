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
import kotlin.collections.flatten
import kotlin.text.first
import kotlin.time.Duration.Companion.milliseconds

class WearKeyboardService : InputMethodService() {
    private lateinit var lifecycleOwner: ImeLifecycleOwner
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e -> e.printStackTrace() })
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
    private var t9Enabled by mutableStateOf(true)
    private var wordCaseMode = CaseMode.LOWER
    private val t9Maps = mutableMapOf<KeyboardLayout, Map<Char, Char>>()
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

        currentLayout = layoutFor(subtype) ?: KeyboardLayout.ENGLISH

        if(isT9Enabled()) {
            suggestionJob?.cancel()
            t9RequestId++
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

            suggestionJob = serviceScope.launch {
                val result = querySuggestions(t9)
                if (requestId != t9RequestId) return@launch
                suggestions = result
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

        loadT9Enabled()

        if(!restarting) {
            suggestions = emptyList()
            manualMode = false; spaceAfterSuggestion = false
            lastT9Key = null; wordCaseMode = CaseMode.LOWER
            suggestionJob?.cancel(); t9TimeoutJob?.cancel()
        }

        val requested = layoutFor(getSystemService(InputMethodManager::class.java).currentInputMethodSubtype)

        currentLayout = listOfNotNull(requested, KeyboardLayout.ENGLISH, KeyboardLayout.UKRAINIAN)
            .firstOrNull { isLanguageEnabled(it) } ?: KeyboardLayout.ENGLISH

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

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateText()
        if (getCurrentWord().isEmpty() && suggestions.isNotEmpty()) { suggestions = emptyList() }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        suggestionJob?.cancel(); t9TimeoutJob?.cancel(); t9RequestId++
    }

    override fun onFinishInputView(finishing: Boolean) {
        super.onFinishInputView(finishing)
        keyboardVisible = false
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        dictionaryDatabase?.close(); dictionaryDatabase = null
        lifecycleOwner.onDestroy()
        super.onDestroy()
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
            KeyType.FUNCTION -> handleFunctionCharacter(action)
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
        val isFirstLetter = currentWord.isEmpty()

        if (isFirstLetter) {
            wordCaseMode = when {
                keyboardState.capsLock -> CaseMode.ALL
                keyboardState.shift -> CaseMode.FIRST
                else -> CaseMode.LOWER
            }
        }
        val firstCharacter = key.characters.firstOrNull() ?: return

        val character = when (wordCaseMode) {
            CaseMode.ALL -> firstCharacter.uppercaseChar()
            CaseMode.FIRST -> if (isFirstLetter) firstCharacter.uppercaseChar() else firstCharacter.lowercaseChar()
            CaseMode.LOWER -> firstCharacter.lowercaseChar()
        }

        connection.commitText(character.toString(), 1)

        if (keyboardState.shift && !keyboardState.capsLock) {
            keyboardState = keyboardState.copy(shift = shouldAutoShift())
        }

        val fullT9 = wordToT9(getCurrentWord() + getWordTail())
        if (fullT9.isEmpty()) { suggestions = emptyList(); return }

        refreshT9(fullT9)
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

    private fun handleFunctionCharacter(action: KeyAction.Character) {
        spaceAfterSuggestion = false

        when (action.key.code) {
            MainFunctions.DELETE -> handleDelete()
            MainFunctions.SPACE -> handleSpace()
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

        val keepWord = spaceAfterSuggestion
        spaceAfterSuggestion = false
        t9RequestId++

        deleteBeforeCursor(connection)

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

        val full = currentWord + getWordTail()
        val fullT9 = wordToT9(full)
        if (fullT9.isEmpty()) { suggestions = emptyList(); return }

        wordCaseMode = detectCaseMode(full)
        refreshT9(fullT9, keepCurrent = keepWord)
    }

    private fun deleteBeforeCursor(connection: InputConnection) {
        if (!connection.getSelectedText(0).isNullOrEmpty()) { connection.commitText("", 1); return }
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
        currentInputConnection ?: return

        if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)

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
        val isDoubleTap = now - lastShiftPressTime <= MainConstants.DOUBLE_TAP_THRESHOLD
        lastShiftPressTime = now
        spaceAfterSuggestion = false
        if (applyShiftToCurrentWord(isDoubleTap)) return

        keyboardState = if (keyboardState.capsLock) {
            keyboardState.copy(shift = shouldAutoShift(), capsLock = false)
        } else if (isDoubleTap) {
            keyboardState.copy(shift = false, capsLock = true)
        } else {
            keyboardState.copy(shift = !keyboardState.shift)
        }
    }

    private fun refreshT9(t9: String, keepCurrent: Boolean = false, autoReplace: Boolean = true) {
        val requestId = ++t9RequestId
        spaceAfterSuggestion = false
        suggestionJob?.cancel()

        suggestionJob = serviceScope.launch {
            val result = querySuggestions(t9)
            if (requestId != t9RequestId) return@launch
            val connection = currentInputConnection ?: return@launch

            val before = getCurrentWord()
            val tail = getWordTail()
            val current = before + tail
            val cased = result.map { it.applyCase(wordCaseMode) }

            if (!autoReplace) {
                suggestions = cased.filter { it != current }
                updateText()
                return@launch
            }

            val exact = if (keepCurrent) cased.firstOrNull { it.equals(current, ignoreCase = true) } else null
            val best = exact ?: cased.firstOrNull { it.t9Length() == t9.length }

            if (best != null) {
                if (best != current) replaceWholeWord(connection, before, tail, best)
                suggestions = cased.filter { it != best }
            } else {
                val recased = current.lowercase().applyCase(wordCaseMode)
                if (recased != current) replaceWholeWord(connection, before, tail, recased)
                suggestions = cased
            }
            updateText()
        }
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
        val activeLayout = layoutFor(imm.currentInputMethodSubtype) ?: return

        spaceAfterSuggestion = false

        val languages = enabledLanguages.toList()
        if (languages.size < 2) return

        val currentIndex = languages.indexOf(activeLayout)
        if (currentIndex < 0) return
        val target = languages[(currentIndex + 1) % languages.size]

        val subtype = (0 until info.subtypeCount).map { info.getSubtypeAt(it) }
            .firstOrNull { layoutFor(it) == target } ?: return

        switchInputMethod(info.id, subtype)
    }

    private fun isLanguageEnabled(language: KeyboardLayout): Boolean {
        return getSharedPreferences(MainConstants.PREFS, MODE_PRIVATE).getBoolean(
            "language_enabled_${language.name}", true
        )
    }

    private fun moveCursor(direction: Int) {
        val connection = currentInputConnection ?: return
        val text = currentText
        val position = cursorPosition.coerceIn(0, text.length)
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        val newPosition = if (direction > 0) iterator.following(position) else iterator.preceding(position)
        if (newPosition == BreakIterator.DONE || newPosition == position) return

        connection.setSelection(newPosition, newPosition)
        updateText()

        getCurrentWord().takeIf { it.isNotEmpty() }?.let { wordCaseMode = detectCaseMode(it) }

        spaceAfterSuggestion = false
        suggestionJob?.cancel()
        t9RequestId++

        if (!isT9Enabled() || manualMode) {
            suggestions = emptyList()
            return
        }

        val requestId = t9RequestId
        val full = getFullWord()

        if (full.isEmpty()) {
            suggestions = emptyList()
            return
        }

        wordCaseMode = detectCaseMode(full)

        suggestionJob = serviceScope.launch {
            delay(250.milliseconds)

            val t9 = wordToT9(full)
            if (t9.isEmpty()) { suggestions = emptyList(); return@launch }

            val result = querySuggestions(t9)
            if (requestId != t9RequestId) return@launch

            suggestions = result.map { it.applyCase(wordCaseMode) }.filter { it != full }
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

            suggestions = emptyList()
            spaceAfterSuggestion = false
            shouldAutoShift()
        }
    }

    private fun loadT9Enabled() { t9Enabled = getSharedPreferences(MainConstants.PREFS, MODE_PRIVATE).getBoolean(MainConstants.USE_T9, true) }

    private fun isT9Enabled(): Boolean = t9Enabled

    private fun getCurrentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(64, 0) ?: return ""
        var start = before.length
        while (start > 0 && (before[start - 1].isLetter() || before[start - 1] == '\'')) start--
        return before.substring(start)
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

    private suspend fun querySuggestions(t9: String): List<String> = withContext(Dispatchers.IO) {
        val db = dictionaryDatabase ?: return@withContext emptyList()
        try {
            db.dictionaryDao().getSuggestions(t9, t9PrefixEnd(t9)).map { it.word }
        } catch (e: CancellationException) { throw e }
        catch (e: IllegalStateException) { e.printStackTrace(); emptyList() }
    }

    private fun selectSuggestion(word: String) {
        val connection = currentInputConnection ?: return
        val before = getCurrentWord().length
        val tail = getWordTailLength()

        if (before > 0 || tail > 0) {
            connection.deleteSurroundingText(before, tail)
        }

        val addSpace = tail == 0
        connection.commitText(if (addSpace) "$word " else word, 1)

        spaceAfterSuggestion = addSpace
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

    private fun getWordTail(): String {
        val after = currentInputConnection?.getTextAfterCursor(64, 0) ?: return ""
        var i = 0
        while (i < after.length && (after[i].isLetter() || after[i] == '\'')) i++
        return after.substring(0, i)
    }

    private fun getWordTailLength() = getWordTail().length

    private fun getFullWord(): String = getCurrentWord() + getWordTail()

    private fun replaceWholeWord(connection: InputConnection, before: String, tail: String, newWord: String) {
        val lettersBefore = before.count { it != '\'' }
        var cut = 0
        var seen = 0
        while (cut < newWord.length && seen < lettersBefore) {
            if (newWord[cut] != '\'') seen++
            cut++
        }
        if (tail.isEmpty()) cut = newWord.length

        connection.beginBatchEdit()
        connection.deleteSurroundingText(before.length, tail.length)
        connection.commitText(newWord.substring(0, cut), 1)
        if (cut < newWord.length) {
            connection.commitText(newWord.substring(cut), 0)
        }
        connection.endBatchEdit()
    }

    private fun String.t9Length() = count { it != '\'' }

    private fun t9Map(): Map<Char, Char> = t9Maps.getOrPut(currentLayout) {
        val layout = when (currentLayout) {
            KeyboardLayout.ENGLISH -> KeyboardLayouts.english
            KeyboardLayout.UKRAINIAN -> KeyboardLayouts.ukrainian
        }

        layout.flatten()
            .filter { it.type == KeyType.T9 }
            .flatMap { key ->
                key.characters
                    .filter { it.isLetter() }
                    .map { it.lowercaseChar() to key.code.first() }
            }
            .toMap()
    }

    private fun wordToT9(word: String): String {
        val map = t9Map()

        return buildString(word.length) {
            for (character in word) {
                if (character == '\'') continue
                val digit = map[character.lowercaseChar()] ?: return ""
                append(digit)
            }
        }
    }

    private fun shouldAutoShift(): Boolean {
        if (!autoShiftEnabled) return false
        val connection = currentInputConnection ?: return false
        val before = connection.getTextBeforeCursor(100, 0)?.toString() ?: return false
        if (before.isBlank()) return true
        val last = before.trimEnd().last()
        return (last in ".!?…" && before.last().isWhitespace()) || before.last() == '\n'
    }

    private fun applyShiftToCurrentWord(isDoubleTap: Boolean): Boolean {
        if (manualMode || !isT9Enabled()) return false
        val connection = currentInputConnection ?: return false

        val before = getCurrentWord()
        if (before.isEmpty()) return false
        val tailLength = getWordTailLength()
        val tail = if (tailLength > 0) connection.getTextAfterCursor(tailLength, 0)?.toString().orEmpty() else ""

        val t9 = wordToT9(before + tail)
        if (t9.isEmpty()) return false

        if (keyboardState.capsLock) {
            keyboardState = keyboardState.copy(shift = false, capsLock = false)
            wordCaseMode = CaseMode.LOWER
        } else {
            wordCaseMode = when {
                isDoubleTap -> CaseMode.ALL
                detectCaseMode(before + tail) == CaseMode.LOWER -> CaseMode.FIRST
                else -> CaseMode.LOWER
            }
        }

        val newBefore = before.recase(wordCaseMode)
        val newTail = tail.recase(if (wordCaseMode == CaseMode.FIRST) CaseMode.LOWER else wordCaseMode)

        if (newBefore != before || newTail != tail) {
            connection.beginBatchEdit()
            connection.deleteSurroundingText(before.length, tail.length)
            connection.commitText(newBefore, 1)
            if (newTail.isNotEmpty()) connection.commitText(newTail, 0)
            connection.endBatchEdit()
        }

        refreshT9(t9, autoReplace = false)
        return true
    }

    private fun String.recase(mode: CaseMode): String = when (mode) {
        CaseMode.ALL -> uppercase()
        CaseMode.FIRST -> lowercase().replaceFirstChar { it.uppercase() }
        CaseMode.LOWER -> lowercase()
    }

    private fun String.applyCase(mode: CaseMode): String = when (mode) {
        CaseMode.ALL -> uppercase()
        CaseMode.FIRST -> replaceFirstChar { it.uppercase() }
        CaseMode.LOWER -> this
    }

    private fun detectCaseMode(word: String): CaseMode = when {
        keyboardState.capsLock -> CaseMode.ALL
        word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() } -> CaseMode.ALL
        word.first().isUpperCase() -> CaseMode.FIRST
        else -> CaseMode.LOWER
    }

    private fun layoutFor(subtype: InputMethodSubtype?): KeyboardLayout? {
        val tag = subtype?.languageTag?.substringBefore("-") ?: return null
        return KeyboardLayout.entries.firstOrNull { it.languageTag == tag }
    }
}