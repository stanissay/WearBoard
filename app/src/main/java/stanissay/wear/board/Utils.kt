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
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material.*
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

object MainConstants {
    val BASE_SIZE = 36.dp

    val BUTTON_SIZE_S = 16.dp
    val BUTTON_SIZE = 32.dp
    val BUTTON_SIZE_L = 48.dp

    val DISPLAY_SIZE = 64.dp

    val NULL_PADDING = 0.dp
    val MAIN_PADDING = 8.dp
    val CURSOR_PADDING = 4.dp

    val THICKNESS = 1.dp
    val ELEVATION = 8.dp

    const val SWIPE_RATIO = 0.4f
    const val MULTI_TAP_THRESHOLD = 1000
    const val DOUBLE_TAP_THRESHOLD = 400L

    const val ACCELERATION_THRESHOLD = 12f
    const val ACCELERATION_DELAY = 1000

    const val VOICE_REQUEST_CODE = 1001
}

object MainFunctions {
    const val ENTER = KeyEvent.KEYCODE_ENTER.toString()
    const val SPACE = KeyEvent.KEYCODE_SPACE.toString()
    const val SHIFT = KeyEvent.KEYCODE_SHIFT_LEFT.toString()
    const val DELETE = KeyEvent.KEYCODE_DEL.toString()
    const val SETTINGS = "settings"
    const val VOICE = "voice"
}

object MainColors {
    val BACKGROUND = Color(0xFF000000)
    val FIRST_ACCENT = Color(0xFF81C784)
    val SECOND_ACCENT = Color(0xFFFF8A65)
    val WHITE = Color(0xFFCECECE)
    val GRAY = Color(0xFF616161)
    val TRANSPARENT = Color(0x00000000)
}

val TypographyStyle = Typography(
    title1 = TextStyle(fontSize = 15.sp),
    body1 = TextStyle(fontSize = 13.sp),
    body2 = TextStyle(fontSize = 11.sp)
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

data class Key(
    val code: String,
    val characters: List<Char> = emptyList(),
    val primaryLabel: String = code,
    val secondaryLabel: String = characters.joinToString(""),
    val icon: ImageVector? = null,
    val type: KeyType = KeyType.NORMAL
)

enum class KeyType {
    NORMAL,
    T9,
    FUNCTION
}

enum class KeyboardLayout {
    ENGLISH,
    UKRAINIAN
}

data class KeyboardState(
    val shift: Boolean = false,
    val capsLock: Boolean = false,
    val symbolsMode: Boolean = false
)

sealed interface KeyAction {
    data class Character(val key: Key, val character: Char, val isMultiTap: Boolean) : KeyAction
    data class LongPress(val key: Key) : KeyAction
    data class Function(val key: Key) : KeyAction
}

object KeyboardLayouts {
    private val english = listOf(
        listOf(
            Key(
                code = "0",
                characters = listOf(' ', '0'),
                secondaryLabel = "␣",
                type = KeyType.T9
            ),
            Key(
                code = "1",
                characters = listOf('.', ',', '?', '!', ':', ';', '1'),
                secondaryLabel = ".,?!:;",
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = "2",
                characters = listOf('a', 'b', 'c', '2'),
                secondaryLabel = "abc",
                type = KeyType.T9
            ),
            Key(
                code = "3",
                characters = listOf('d', 'e', 'f', '3'),
                secondaryLabel = "def",
                type = KeyType.T9
            ),
            Key(
                code = "4",
                characters = listOf('g', 'h', 'i', '4'),
                secondaryLabel = "ghi",
                type = KeyType.T9
            ),
            Key(
                code = "5",
                characters = listOf('j', 'k', 'l', '5'),
                secondaryLabel = "jkl",
                type = KeyType.T9
            ),
            Key(
                code = "6",
                characters = listOf('m', 'n', 'o', '6'),
                secondaryLabel = "mno",
                type = KeyType.T9
            ),
            Key(
                code = "7",
                characters = listOf('p', 'q', 'r', 's', '7'),
                secondaryLabel = "pqr",
                type = KeyType.T9
            ),
            Key(
                code = "8",
                characters = listOf('t', 'u', 'v', '8'),
                secondaryLabel = "tuv",
                type = KeyType.T9
            ),
            Key(
                code = "9",
                characters = listOf('w', 'x', 'y', 'z', '9'),
                secondaryLabel = "wxyz",
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = MainFunctions.SPACE,
                icon = Icons.Default.SpaceBar,
                type = KeyType.FUNCTION
            ),
            Key(
                code = MainFunctions.DELETE,
                icon = Icons.AutoMirrored.Filled.Backspace,
                type = KeyType.FUNCTION
            )
        )
    )

    private val ukrainian = listOf(
        listOf(
            Key(
                code = "0",
                characters = listOf(' ', '0'),
                secondaryLabel = "␣",
                type = KeyType.T9
            ),
            Key(
                code = "1",
                characters = listOf('.', ',', '?', '!', ':', ';', '1'),
                secondaryLabel = ".,?!:;",
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = "2",
                characters = listOf('а', 'б', 'в', 'г', 'ґ', '2'),
                secondaryLabel = "абвгґ",
                type = KeyType.T9
            ),
            Key(
                code = "3",
                characters = listOf('д', 'е', 'є', 'ж', 'з', '3'),
                secondaryLabel = "деєжз",
                type = KeyType.T9
            ),
            Key(
                code = "4",
                characters = listOf('и', 'і', 'ї', 'й', 'к', 'л', '4'),
                secondaryLabel = "иіїйкл",
                type = KeyType.T9
            ),
            Key(
                code = "5",
                characters = listOf('м', 'н', 'о', 'п', '5'),
                secondaryLabel = "мноп",
                type = KeyType.T9
            ),
            Key(
                code = "6",
                characters = listOf('р', 'с', 'т', 'у', '6'),
                secondaryLabel = "рсту",
                type = KeyType.T9
            ),
            Key(
                code = "7",
                characters = listOf('ф', 'х', 'ц', 'ч', '7'),
                secondaryLabel = "фхцч",
                type = KeyType.T9
            ),
            Key(
                code = "8",
                characters = listOf('ш', 'щ', '8'),
                secondaryLabel = "шщ",
                type = KeyType.T9
            ),
            Key(
                code = "9",
                characters = listOf('ь', 'ю', 'я', '9'),
                secondaryLabel = "ьюя",
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = MainFunctions.SPACE,
                icon = Icons.Default.SpaceBar,
                type = KeyType.FUNCTION
            ),
            Key(
                code = MainFunctions.DELETE,
                icon = Icons.AutoMirrored.Filled.Backspace,
                type = KeyType.FUNCTION
            )
        )
    )

    val symbols = listOf(
        listOf(
            Key(
                code = "+",
                characters = listOf('-', '=', '+'),
                secondaryLabel = "-=",
                type = KeyType.T9
            ),
            Key(
                code = "*",
                characters = listOf('/', '\\', '|', '%', '*'),
                secondaryLabel = "/\\|%",
                type = KeyType.T9
            ),
            Key(
                code = ")",
                characters = listOf('(', '<', '>', ')'),
                secondaryLabel = "(<>",
                type = KeyType.T9
            ),
            Key(
                code = "]",
                characters = listOf('[', '{', '}', ']'),
                secondaryLabel = "[{}",
                type = KeyType.T9
            ),
            Key(
                code = "\"",
                characters = listOf('\'', '«', '»', '\"'),
                secondaryLabel = "\'«»",
                type = KeyType.T9
            ),
            Key(
                code = "@",
                characters = listOf('#', '&', '@'),
                secondaryLabel = "#&",
                type = KeyType.T9
            ),
            Key(
                code = "$",
                characters = listOf('₴', '€', '$'),
                secondaryLabel = "₴€",
                type = KeyType.T9
            ),
            Key(
                code = "□",
                characters = listOf('△', '□', '⬡'),
                secondaryLabel = "△⬡",
                type = KeyType.T9
            ),
            Key(
                code = "°",
                characters = listOf('○', '⌀', '°'),
                secondaryLabel = "○⌀",
                type = KeyType.T9
            ),
            Key(
                code = "~",
                characters = listOf('±', '≈', '≠', '∞', '~'),
                secondaryLabel = "±≈≠∞",
                type = KeyType.T9
            )
        ),
        listOf(
            Key(
                code = "space",
                icon = Icons.Default.SpaceBar,
                type = KeyType.FUNCTION
            ),
            Key(
                code = "delete",
                icon = Icons.AutoMirrored.Filled.Backspace,
                type = KeyType.FUNCTION
            )
        )
    )

    val functions = listOf(
        listOf(
            Key(
                code = MainFunctions.SHIFT,
                icon = Icons.Default.KeyboardArrowUp
            ),
            Key(
                code = MainFunctions.VOICE,
                icon = Icons.Default.Mic
            ),
            Key(
                code = MainFunctions.SETTINGS,
                icon = Icons.Default.Settings
            )
        )
    )

    val layouts = mapOf(
        KeyboardLayout.ENGLISH to english,
        KeyboardLayout.UKRAINIAN to ukrainian
    )
}

fun Context.enableAllSubtypes() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager ?: return

    val info = imm.enabledInputMethodList
        .firstOrNull { it.packageName == packageName } ?: return

    val subtypeHashCodes = (0 until info.subtypeCount)
        .map { info.getSubtypeAt(it).hashCode() }.toIntArray()

    imm.setExplicitlyEnabledInputMethodSubtypes(info.id, subtypeHashCodes)
}

@SuppressLint("ModifierFactoryExtensionFunction")
fun TransformingLazyColumnItemScope.transformedItem(
    transformationSpec: TransformationSpec
): Modifier =
    Modifier
        .transformedHeight(this, transformationSpec)
        .graphicsLayer {
            with(transformationSpec) {
                applyContainerTransformation(scrollProgress)
            }
        }