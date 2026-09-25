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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ripple
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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
fun TransformingLazyColumnItemScope.MainCard(
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colors.surface,
    borderColor: Color = MainColors.TRANSPARENT,
    contentPadding: Dp = MainConstants.MAIN_PADDING,
    containerPadding: Dp = MainConstants.MAIN_PADDING,
    shape: Shape = ShapesStyle.medium,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .padding(containerPadding)
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                with(transformationSpec) {
                    applyContainerTransformation(scrollProgress)
                }
            }
            .shadow(
                elevation = MainConstants.ELEVATION,
                shape = shape
            )
            .clip(shape)
            .background(containerColor)
            .border(
                width = MainConstants.THICKNESS,
                color = borderColor,
                shape = shape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MainColors.TRANSPARENT)
                .padding(contentPadding)
        ) { content() }
    }
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
fun TitleText(
    text: String,
    modifier: Modifier = Modifier
) {
    MainText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.title1,
        color = MaterialTheme.colors.primary
    )
}

@Composable
fun FirstAccentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.body2
) {
    MainText(
        text = text,
        modifier = modifier,
        style = style,
        color = MaterialTheme.colors.primary
    )
}

@Composable
fun SecondAccentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.body2,
) {
    MainText(
        text = text,
        modifier = modifier,
        style = style,
        color = MaterialTheme.colors.secondary
    )
}

@Composable
fun CaptureText(
    text: String,
    modifier: Modifier = Modifier
) {
    MainText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.surface
    )
}

@Composable
fun TransformingLazyColumnItemScope.WearSpacer(transformationSpec: TransformationSpec) {
    Spacer(
        modifier = Modifier.fillMaxWidth().height(MainConstants.BASE_SIZE)
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                with(transformationSpec) {
                    applyContainerTransformation(scrollProgress)
                }
            }
    )
}