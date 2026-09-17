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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object UIConstants {
    const val PREFS_NAME = "board_prefs"
    const val KEY_STATE = "saved_state"
    val BUTTON_SIZE = 32.dp
    val BUTTON_SIZE_L = 48.dp
    val DISPLAY_HEIGHT = 48.dp
    val DISPLAY_WIDTH = 64.dp
    val CURSOR_PADDING = 4.dp
    val CURSOR_WIDTH = 1.dp
    const val ROTATION_THRESHOLD = 30f
    const val NUMB_RATIO = 0.350f
    const val MATH_RATIO = 0.225f
    const val ACCELERATION_THRESHOLD = 12f
    const val ACCELERATION_DELAY = 1000
    const val SWIPE_RATIO = 0.4f
    const val MULTI_TAP_THRESHOLD = 1000
    const val DOUBLE_TAP_THRESHOLD = 400L
}

data class Key(
    val code: String,
    val characters: List<Char> = emptyList(),
    val primaryLabel: String = code,
    val secondaryLabel: String = characters.joinToString(""),
    val type: KeyType = KeyType.NORMAL
)

enum class KeyType {
    NORMAL,
    T9,
    FUNCTION
}

enum class KeyboardLayout {
    UKRAINIAN,
    ENGLISH,
    SYMBOLS,
    FUNCTION
}

data class KeyboardState(
    val shift: Boolean = false,
    val capsLock: Boolean = false
)

sealed interface KeyAction {
    data class Character(val key: Key, val character: Char, val isMultiTap: Boolean) : KeyAction
    data class LongPress(val key: Key) : KeyAction
    data class Function(val key: Key) : KeyAction
}

object KeyboardLayouts {
    private val ukrainian = listOf(
        listOf(
            Key(
                code = "0",
                characters = listOf(' '),
                secondaryLabel = "␣",
                type = KeyType.NORMAL
            ),
            Key(
                code = "1",
                characters = listOf('.', ',', '?', '!', ':', ';', '"', '\''),
                secondaryLabel = "...",
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = "2",
                characters = listOf('а', 'б', 'в', 'г', 'ґ'),
                type = KeyType.T9
            ),
            Key(
                code = "3",
                characters = listOf('д', 'е', 'є', 'ж', 'з'),
                type = KeyType.T9
            ),
            Key(
                code = "4",
                characters = listOf('и', 'і', 'ї', 'й', 'к', 'л'),
                type = KeyType.T9
            ),
            Key(
                code = "5",
                characters = listOf('м', 'н', 'о', 'п'),
                type = KeyType.T9
            ),
            Key(
                code = "6",
                characters = listOf('р', 'с', 'т', 'у'),
                type = KeyType.T9
            ),
            Key(
                code = "7",
                characters = listOf('ф', 'х', 'ц', 'ч'),
                type = KeyType.T9
            ),
            Key(
                code = "8",
                characters = listOf('ш', 'щ'),
                type = KeyType.T9
            ),
            Key(
                code = "9",
                characters = listOf('ь', 'ю', 'я'),
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = "space",
                primaryLabel = "␣",
                type = KeyType.FUNCTION
            ),
            Key(
                code = "delete",
                primaryLabel = "⌫",
                type = KeyType.FUNCTION
            )
        )
    )

    private val english = listOf(
        listOf(
            Key(
                code = "0",
                characters = listOf(' '),
                secondaryLabel = "␣",
                type = KeyType.NORMAL
            ),
            Key(
                code = "1",
                characters = listOf('.', ',', '?', '!', ':', ';', '"', '\''),
                secondaryLabel = "...",
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = "2",
                characters = listOf('a', 'b', 'c'),
                type = KeyType.T9
            ),
            Key(
                code = "3",
                characters = listOf('d', 'e', 'f'),
                type = KeyType.T9
            ),
            Key(
                code = "4",
                characters = listOf('g', 'h', 'i'),
                type = KeyType.T9
            ),
            Key(
                code = "5",
                characters = listOf('j', 'k', 'l'),
                type = KeyType.T9
            ),
            Key(
                code = "6",
                characters = listOf('m', 'n', 'o'),
                type = KeyType.T9
            ),
            Key(
                code = "7",
                characters = listOf('p', 'q', 'r', 's'),
                type = KeyType.T9
            ),
            Key(
                code = "8",
                characters = listOf('t', 'u', 'v'),
                type = KeyType.T9
            ),
            Key(
                code = "9",
                characters = listOf('w', 'x', 'y', 'z'),
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = "space",
                primaryLabel = "␣",
                type = KeyType.FUNCTION
            ),
            Key(
                code = "delete",
                primaryLabel = "⌫",
                type = KeyType.FUNCTION
            )
        )
    )

    private val functions = listOf(
        listOf(
            Key(
                code = "shift",
                primaryLabel = "⇧"
            ),
            Key(
                code = "settings",
                primaryLabel = "⚙"
            )
        )
    )

    private val symbols = listOf(
        listOf(
            Key("+"),
            Key("-"),
            Key("*"),
            Key("/"),
            Key("=")
        ),
        listOf(
            Key("!"),
            Key("?"),
            Key(":"),
            Key(";"),
            Key("'"),
            Key("\"")
        )
    )

    val layouts = mapOf(
        KeyboardLayout.UKRAINIAN to ukrainian,
        KeyboardLayout.ENGLISH to english,
        KeyboardLayout.SYMBOLS to symbols,
        KeyboardLayout.FUNCTION to functions
    )
}

object MainColors {
    val BACKGROUND = Color(0xFF000000)
    val FIRST_ACCENT = Color(0xFF81C784)
    val SECOND_ACCENT = Color(0xFFFF8A65)
    val WHITE = Color(0xFFCECECE)
    val GRAY = Color(0xFF616161)
}

val TypographyStyle = Typography(
    body1 = TextStyle(fontSize = 12.sp),
    body2 = TextStyle(fontSize = 10.sp)
)

val ColorStyle = Colors(
    primary = MainColors.FIRST_ACCENT,
    secondary = MainColors.SECOND_ACCENT,
    background = MainColors.BACKGROUND,
    surface = MainColors.GRAY,
    onBackground = MainColors.WHITE,
)

val ShapesStyle = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun MainTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = TypographyStyle,
        colors = ColorStyle,
        shapes = ShapesStyle,
        content = content
    )
}

@Composable
fun ClickableBox(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentAlignment: Alignment = Alignment.Center,
    rippleRadius: Dp = Dp.Unspecified,
    rippleColor: Color = MaterialTheme.colors.primary,
    bounded: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    repeatOnLongClick: Boolean = false,
    onLongClickRepeat: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    val isPressed by interactionSource.collectIsPressedAsState()
    var isLongPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed, isLongPressed) {
        if (!isPressed) {
            isLongPressed = false
            return@LaunchedEffect
        }

        if (!isLongPressed || !repeatOnLongClick || onLongClickRepeat == null) {
            return@LaunchedEffect
        }

        while (true) {
            delay(70L.milliseconds)
            onLongClickRepeat()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )

    val wrappedOnClick = {
        haptic.performHapticFeedback(
            HapticFeedbackType.KeyboardTap
        )
        onClick()
    }

    val wrappedOnLongClick = onLongClick?.let { original ->
        {
            haptic.performHapticFeedback(
                HapticFeedbackType.LongPress
            )

            isLongPressed = true
            original()
        }
    }

    val clickModifier = when {
        onLongClick != null -> {
            Modifier.combinedClickable(
                onClick = wrappedOnClick,
                onLongClick = wrappedOnLongClick,
                indication = ripple(
                    bounded,
                    rippleRadius,
                    rippleColor
                ),
                interactionSource = interactionSource
            )
        }

        else -> {
            Modifier.clickable(
                onClick = wrappedOnClick,
                indication = ripple(
                    bounded,
                    rippleRadius,
                    rippleColor
                ),
                interactionSource = interactionSource
            )
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(clickModifier)
    ) {
        Box(
            modifier = Modifier
                .align(contentAlignment)
                .matchParentSize(),
            contentAlignment = contentAlignment
        ) {
            content()
        }
    }
}

@Composable
fun MainText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.body1,
    fontSize: TextUnit = style.fontSize,
    textAlign: TextAlign = TextAlign.Center,
    color: Color = MaterialTheme.colors.onBackground,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    textDecoration: TextDecoration = TextDecoration.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = textAlign,
        style = style,
        color = color,
        maxLines = maxLines,
        textDecoration = textDecoration,
        overflow = overflow,
        fontSize = fontSize,
        onTextLayout = onTextLayout
    )
}

@Composable
fun FirstAccentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.body1,
    fontSize: TextUnit = style.fontSize,
    textAlign: TextAlign = TextAlign.Center,
    color: Color = MaterialTheme.colors.primary,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    textDecoration: TextDecoration = TextDecoration.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = textAlign,
        style = style,
        color = color,
        maxLines = maxLines,
        textDecoration = textDecoration,
        overflow = overflow,
        fontSize = fontSize,
        onTextLayout = onTextLayout
    )
}

@Composable
fun SecondAccentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.body2,
    fontSize: TextUnit = style.fontSize,
    textAlign: TextAlign = TextAlign.Center,
    color: Color = MaterialTheme.colors.secondary,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    textDecoration: TextDecoration = TextDecoration.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = textAlign,
        style = style,
        color = color,
        maxLines = maxLines,
        textDecoration = textDecoration,
        overflow = overflow,
        fontSize = fontSize,
        onTextLayout = onTextLayout
    )
}