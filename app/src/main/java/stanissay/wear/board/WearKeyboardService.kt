package stanissay.wear.board

import android.annotation.SuppressLint
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class WearKeyboardService : InputMethodService() {
    private lateinit var lifecycleOwner: ImeLifecycleOwner
    private var currentLayout = KeyboardLayout.UKRAINIAN
    private var currentText by mutableStateOf("")
    private var cursorPosition by mutableIntStateOf(0)
    private var keyboardState by mutableStateOf(KeyboardState())
    private var lastShiftPressTime = 0L
    private var lastT9PressTime = 0L
    private var lastT9Key: Key? = null
    private var t9TimeoutJob: Job? = null
    private val serviceScope = CoroutineScope(Job() + Dispatchers.Main)

    private val keyboard: List<List<Key>>
        get() = KeyboardLayouts.layouts.getValue(currentLayout)
    private val funKeyboard: List<List<Key>>
        get() = KeyboardLayouts.layouts.getValue(KeyboardLayout.FUNCTION)

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner = ImeLifecycleOwner()
        lifecycleOwner.onCreate()
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(lifecycleOwner)
            decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        }

        return ComposeView(this).apply {
            setContent {
                MainTheme {
                    KeyboardScreen(
                        keyboard = keyboard,
                        funKeyboard = funKeyboard,
                        text = currentText,
                        cursorPosition = cursorPosition,
                        keyboardState = keyboardState,
                        suggestions = emptyList(),
                        onKeyAction = ::handleKey,
                        onLongClick = {},
                        onSuggestionClick = {}
                    )
                }
            }
        }
    }

    override fun onStartInputView(
        info: EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()
        updateText()
    }

    override fun onFinishInputView(finishing: Boolean) {
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        super.onFinishInputView(finishing)
    }

    override fun onDestroy() {
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    @SuppressLint("WearRecents")
    private fun handleKey(keyAction: KeyAction) {
        val connection = currentInputConnection ?: return

        when (keyAction) {
            is KeyAction.Character -> {
                val uppercase = keyboardState.shift || keyboardState.capsLock

                when (keyAction.key.type) {
                    KeyType.T9 -> {
                        val now = System.currentTimeMillis()
                        val sameKey = keyAction.key == lastT9Key
                        val sequenceActive = sameKey && now - lastT9PressTime <= UIConstants.MULTI_TAP_THRESHOLD

                        if (!sequenceActive && lastT9Key != null && !keyboardState.capsLock) {
                            keyboardState = keyboardState.copy(shift = false)
                        }

                        if (keyAction.isMultiTap) {
                            connection.deleteSurroundingText(1, 0)
                        }

                        val uppercase = keyboardState.shift || keyboardState.capsLock

                        val character =
                            if (uppercase) { keyAction.character.uppercaseChar()
                            } else { keyAction.character.lowercaseChar() }

                        connection.commitText(character.toString(), 1)

                        lastT9Key = keyAction.key
                        lastT9PressTime = now
                        t9TimeoutJob?.cancel()

                        t9TimeoutJob = serviceScope.launch {
                            delay(UIConstants.MULTI_TAP_THRESHOLD.milliseconds)

                            if (lastT9Key == keyAction.key && !keyboardState.capsLock) {
                                keyboardState = keyboardState.copy(shift = false)
                            }

                            lastT9Key = null
                            lastT9PressTime = 0L
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
                            "delete" -> {
                                connection.deleteSurroundingText(1, 0)
                            }

                            "space" -> {
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
                    "delete" -> {
                        connection.deleteSurroundingText(1, 0)
                    }

                    "space" -> {
                        connection.commitText(" ", 1)
                    }

                    "enter" -> {
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

                    "shift" -> {
                        val now = System.currentTimeMillis()

                        if (keyboardState.capsLock) {
                            keyboardState = keyboardState.copy(
                                shift = false,
                                capsLock = false
                            )
                        } else if (now - lastShiftPressTime <= UIConstants.DOUBLE_TAP_THRESHOLD) {
                            keyboardState = keyboardState.copy(
                                shift = false,
                                capsLock = true
                            )
                        } else {
                            keyboardState = keyboardState.copy(
                                shift = !keyboardState.shift
                            )
                        }

                        lastShiftPressTime = now
                    }

                    "settings" -> {
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
}