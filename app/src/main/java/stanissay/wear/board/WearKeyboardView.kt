package stanissay.wear.board

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.wear.compose.material.MaterialTheme
import stanissay.wear.board.UIConstants.CURSOR_PADDING
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun KeyboardScreen(
    modifier: Modifier = Modifier,
    keyboard: List<List<Key>>,
    funKeyboard: List<List<Key>>,
    text: String,
    cursorPosition: Int,
    keyboardState: KeyboardState,
    suggestions: List<String>,
    onKeyAction: (KeyAction) -> Unit,
    onLongClick: () -> Unit,
    onSuggestionClick: (String) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        CircularKeypad(
            keyboard = keyboard,
            keyboardState = keyboardState,
            onKeyAction = onKeyAction,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = modifier
                .size(UIConstants.DISPLAY_WIDTH)
                .align(Alignment.Center)
                .background(MaterialTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SuggestionRow(
                suggestions = suggestions,
                modifier = Modifier.weight(1f),
                onClick = onSuggestionClick
            )

            InputField(
                text = text,
                cursorPosition = cursorPosition,
                onClick = onKeyAction,
                onLongClick = onLongClick,
                modifier = Modifier.weight(1f)
            )

            FunctionKeys(
                keyboard = funKeyboard,
                onKeyAction = onKeyAction,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun InputField(
    text: String,
    cursorPosition: Int,
    onClick: (KeyAction) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(cursorPosition, layoutResult) {
        val layout = layoutResult ?: return@LaunchedEffect
        val rect = runCatching {
            layout.getCursorRect(cursorPosition)
        }.getOrNull() ?: return@LaunchedEffect
        val target = (rect.left + with(density) { CURSOR_PADDING.toPx() }).toInt()

        if (target != scrollState.value) {
            scrollState.animateScrollTo(target)
        }
    }

    ClickableBox(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        onClick = { onClick(KeyAction.Function(Key(code = "enter", type = KeyType.FUNCTION))) },
        onLongClick = { onLongClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState),
            contentAlignment = Alignment.CenterStart
        ) {
            MainText(
                modifier = Modifier.padding(horizontal = CURSOR_PADDING),
                text = text,
                onTextLayout = {
                    layoutResult = it
                }
            )

            val layout = layoutResult

            if (layout != null && cursorPosition <= layout.layoutInput.text.length) {
                val cursorRect = runCatching {
                    layout.getCursorRect(cursorPosition)
                }.getOrNull()

                cursorRect?.let { rect ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = CURSOR_PADDING)
                            .height(
                                with(LocalDensity.current) {
                                    rect.height.toDp()
                                }
                            )
                            .width(UIConstants.CURSOR_WIDTH)
                            .offset {
                                IntOffset(
                                    rect.left.toInt(),
                                    0
                                )
                            }
                            .background(MaterialTheme.colors.surface)
                    )
                }
            }
        }
    }
}

@Composable
fun FunctionKeys(
    keyboard: List<List<Key>>,
    onKeyAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        keyboard.forEach { keys ->
            keys.forEach { key ->
                ClickableBox(
                    modifier = Modifier.size(UIConstants.BUTTON_SIZE),
                    onClick = { onKeyAction(KeyAction.Function(key)) }
                ) { MainText(text = key.primaryLabel) }
            }
        }
    }
}

@Composable
fun SuggestionRow(
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        suggestions.forEach { suggestion ->
            ClickableBox(
                modifier = Modifier.size(UIConstants.BUTTON_SIZE),
                onClick = { onClick(suggestion) }
            ) { MainText(text = suggestion) }
        }
    }
}

@Composable
fun CircularKeypad(
    keyboard: List<List<Key>>,
    keyboardState: KeyboardState,
    onKeyAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = keyboard.flatten()
    var lastKey by remember { mutableStateOf<Key?>(null) }
    var characterIndex by remember { mutableIntStateOf(0) }
    var lastPressTime by remember { mutableLongStateOf(0L) }

    fun handleKey(key: Key) {
        if (key.type == KeyType.FUNCTION) {
            onKeyAction(KeyAction.Function(key))
            return
        }

        val now = System.currentTimeMillis()
        val isMultiTap = key == lastKey &&
                now - lastPressTime <= UIConstants.MULTI_TAP_THRESHOLD && key.characters.isNotEmpty()

        if (isMultiTap) {
            characterIndex = (characterIndex + 1) % key.characters.size
        } else {
            lastKey = key
            characterIndex = 0
        }

        lastPressTime = now

        val character = key.characters.getOrNull(characterIndex)

        character?.let {
            onKeyAction(
                KeyAction.Character(
                    key = key,
                    character = it,
                    isMultiTap = isMultiTap
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val sizePx = constraints.maxWidth.toFloat()
        val center = sizePx / 2f
        val keySizePx = with(LocalDensity.current) {
            UIConstants.BUTTON_SIZE_L.toPx()
        }
        val radius = center - keySizePx / 2f
        val angleStep = (2 * PI) / keys.size

        keys.forEachIndexed { index, key ->
            val angle = angleStep * index - PI / 2
            val x = center + radius * cos(angle).toFloat()
            val y = center + radius * sin(angle).toFloat()

            ClickableBox(
                modifier = Modifier
                    .size(UIConstants.BUTTON_SIZE_L)
                    .offset {
                        IntOffset(
                            (x - keySizePx / 2f).toInt(),
                            (y - keySizePx / 2f).toInt()
                        )
                    },
                onClick = { handleKey(key) },
                onLongClick = { onKeyAction(KeyAction.LongPress(key)) },
                repeatOnLongClick = key.type == KeyType.FUNCTION,
                onLongClickRepeat = { onKeyAction(KeyAction.Function(key)) }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FirstAccentText(text = key.primaryLabel)
                    if(key.secondaryLabel.isNotBlank()) {
                        SecondAccentText(
                            text = if (keyboardState.shift || keyboardState.capsLock) {
                                key.secondaryLabel.uppercase()
                            } else {
                                key.secondaryLabel.lowercase()
                            }
                        )
                    }
                }
            }
        }
    }
}