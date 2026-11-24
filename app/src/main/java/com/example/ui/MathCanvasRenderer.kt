package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.EditorCursor
import com.example.model.FractionNode
import com.example.model.LogNode
import com.example.model.MathNode
import com.example.model.MathSlot
import com.example.model.PowerNode
import com.example.model.RootNode
import com.example.model.SymbolNode
import com.example.ui.theme.MathSerifFont
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun MathEquationCanvas(
    equationIndex: Int,
    rootSlot: MathSlot,
    cursor: EditorCursor?,
    highlightedEmptySlotId: String?,
    onTapSlot: (slotId: String, indexInSlot: Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isFocusedEquation = cursor?.equationIndex == equationIndex
    val activeSlotId = if (isFocusedEquation) cursor?.slotId else null
    val activeIndex = if (isFocusedEquation) cursor?.indexInSlot else null

    val infiniteTransition = rememberInfiniteTransition(label = "caret_blink")
    val caretAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caret_alpha"
    )

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onTapSlot(rootSlot.id, rootSlot.nodes.size)
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (rootSlot.nodes.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlaceholderBox(
                    slotId = rootSlot.id,
                    isFocused = activeSlotId == rootSlot.id,
                    isHighlightedWarning = highlightedEmptySlotId == rootSlot.id,
                    caretAlpha = caretAlpha,
                    scale = 1.05f,
                    onClick = { onTapSlot(rootSlot.id, 0) }
                )
                Text(
                    text = stringResource(R.string.empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        } else {
            MathSlotRenderer(
                slot = rootSlot,
                activeSlotId = activeSlotId,
                activeIndex = activeIndex,
                highlightedEmptySlotId = highlightedEmptySlotId,
                caretAlpha = caretAlpha,
                scale = 1.0f,
                onTapSlot = onTapSlot
            )
        }
    }
}

@Composable
fun MathSlotRenderer(
    slot: MathSlot,
    activeSlotId: String?,
    activeIndex: Int?,
    highlightedEmptySlotId: String?,
    caretAlpha: Float,
    scale: Float,
    onTapSlot: (slotId: String, indexInSlot: Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isThisSlotActive = activeSlotId == slot.id

    if (slot.nodes.isEmpty()) {
        PlaceholderBox(
            slotId = slot.id,
            isFocused = isThisSlotActive,
            isHighlightedWarning = highlightedEmptySlotId == slot.id,
            caretAlpha = caretAlpha,
            scale = scale,
            onClick = { onTapSlot(slot.id, 0) },
            modifier = modifier
        )
        return
    }

    Row(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onTapSlot(slot.id, slot.nodes.size)
            }
            .padding(horizontal = (2 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isThisSlotActive && activeIndex == 0) {
            BlinkingCaret(alpha = caretAlpha, scale = scale)
        }

        slot.nodes.forEachIndexed { index, node ->
            MathNodeRenderer(
                node = node,
                parentSlotId = slot.id,
                indexInParent = index,
                activeSlotId = activeSlotId,
                activeIndex = activeIndex,
                highlightedEmptySlotId = highlightedEmptySlotId,
                caretAlpha = caretAlpha,
                scale = scale,
                onTapSlot = onTapSlot
            )
            if (isThisSlotActive && activeIndex == index + 1) {
                BlinkingCaret(alpha = caretAlpha, scale = scale)
            }
        }
    }
}

@Composable
private fun MathNodeRenderer(
    node: MathNode,
    parentSlotId: String,
    indexInParent: Int,
    activeSlotId: String?,
    activeIndex: Int?,
    highlightedEmptySlotId: String?,
    caretAlpha: Float,
    scale: Float,
    onTapSlot: (slotId: String, indexInSlot: Int?) -> Unit
) {
    when (node) {
        is SymbolNode -> SymbolTokenView(
            symbol = node.value,
            scale = scale,
            onTap = { onTapSlot(parentSlotId, indexInParent + 1) }
        )
        is PowerNode -> PowerNodeView(
            node = node,
            activeSlotId = activeSlotId,
            activeIndex = activeIndex,
            highlightedEmptySlotId = highlightedEmptySlotId,
            caretAlpha = caretAlpha,
            scale = scale,
            onTapSlot = onTapSlot
        )
        is LogNode -> LogNodeView(
            node = node,
            activeSlotId = activeSlotId,
            activeIndex = activeIndex,
            highlightedEmptySlotId = highlightedEmptySlotId,
            caretAlpha = caretAlpha,
            scale = scale,
            onTapSlot = onTapSlot
        )
        is RootNode -> RootNodeView(
            node = node,
            activeSlotId = activeSlotId,
            activeIndex = activeIndex,
            highlightedEmptySlotId = highlightedEmptySlotId,
            caretAlpha = caretAlpha,
            scale = scale,
            onTapSlot = onTapSlot
        )
        is FractionNode -> FractionNodeView(
            node = node,
            activeSlotId = activeSlotId,
            activeIndex = activeIndex,
            highlightedEmptySlotId = highlightedEmptySlotId,
            caretAlpha = caretAlpha,
            scale = scale,
            onTapSlot = onTapSlot
        )
    }
}

@Composable
private fun SymbolTokenView(
    symbol: String,
    scale: Float,
    onTap: () -> Unit
) {
    val isVar = symbol in setOf("x", "y", "z")
    val isOperator = symbol in setOf("+", "-", "−", "×", "÷", "=")
    val displayText = when (symbol) {
        "-" -> "−"
        "*" -> "×"
        "/" -> "÷"
        else -> symbol
    }
    val fontSize = (24f * scale).coerceAtLeast(13f).sp
    val horizontalPad = if (isOperator) (4f * scale).dp else (0.5f * scale).dp

    val textColor = when {
        isVar -> MaterialTheme.colorScheme.primary
        symbol == "=" -> MaterialTheme.colorScheme.secondary
        isOperator -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = displayText,
        fontFamily = if (isVar) MathSerifFont else MaterialTheme.typography.headlineMedium.fontFamily,
        fontStyle = if (isVar) FontStyle.Italic else FontStyle.Normal,
        fontWeight = when {
            isVar -> FontWeight.SemiBold
            isOperator -> FontWeight.Medium
            else -> FontWeight.Medium
        },
        fontSize = fontSize,
        color = textColor,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
            .padding(horizontal = horizontalPad)
    )
}

@Composable
private fun PowerNodeView(
    node: PowerNode,
    activeSlotId: String?,
    activeIndex: Int?,
    highlightedEmptySlotId: String?,
    caretAlpha: Float,
    scale: Float,
    onTapSlot: (slotId: String, indexInSlot: Int?) -> Unit
) {
    val expScale = (scale * 0.68f).coerceAtLeast(0.48f)
    val gapPx = with(LocalDensity.current) { (2f * scale).dp.roundToPx() }

    Layout(
        content = {
            MathSlotRenderer(
                slot = node.base,
                activeSlotId = activeSlotId,
                activeIndex = activeIndex,
                highlightedEmptySlotId = highlightedEmptySlotId,
                caretAlpha = caretAlpha,
                scale = scale,
                onTapSlot = onTapSlot
            )
            MathSlotRenderer(
                slot = node.exponent,
                activeSlotId = activeSlotId,
                activeIndex = activeIndex,
                highlightedEmptySlotId = highlightedEmptySlotId,
                caretAlpha = caretAlpha,
                scale = expScale,
                onTapSlot = onTapSlot
            )
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val basePlaceable = measurables[0].measure(loose)
        val expPlaceable = measurables[1].measure(loose)

        val expLift = max(0, (expPlaceable.height * 0.65f).roundToInt() - (basePlaceable.height * 0.25f).roundToInt())
        val totalWidth = basePlaceable.width + gapPx + expPlaceable.width
        val totalHeight = max(expLift + basePlaceable.height, expPlaceable.height)

        layout(totalWidth, totalHeight) {
            basePlaceable.placeRelative(0, totalHeight - basePlaceable.height)
            expPlaceable.placeRelative(basePlaceable.width + gapPx, 0)
        }
    }
}

@Composable
private fun LogNodeView(
    node: LogNode,
    activeSlotId: String?,
    activeIndex: Int?,
    highlightedEmptySlotId: String?,
    caretAlpha: Float,
    scale: Float,
    onTapSlot: (slotId: String, indexInSlot: Int?) -> Unit
) {
    val subScale = (scale * 0.65f).coerceAtLeast(0.48f)
    val fontSize = (21f * scale).coerceAtLeast(12f).sp
    val ink = MaterialTheme.colorScheme.onSurface
    val parenColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = (2f * scale).dp)
    ) {
        Text(
            text = "log",
            fontFamily = MathSerifFont,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            color = ink
        )
        Box(
            modifier = Modifier
                .padding(top = (12f * scale).dp, start = (1f * scale).dp, end = (3f * scale).dp)
        ) {
            MathSlotRenderer(
                slot = node.base,
                activeSlotId = activeSlotId,
                activeIndex = activeIndex,
                highlightedEmptySlotId = highlightedEmptySlotId,
                caretAlpha = caretAlpha,
                scale = subScale,
                onTapSlot = onTapSlot
            )
        }
        Text(
            text = "(",
            fontSize = (23f * scale).coerceAtLeast(13f).sp,
            color = parenColor,
            fontWeight = FontWeight.Light
        )
        MathSlotRenderer(
            slot = node.argument,
            activeSlotId = activeSlotId,
            activeIndex = activeIndex,
            highlightedEmptySlotId = highlightedEmptySlotId,
            caretAlpha = caretAlpha,
            scale = scale,
            onTapSlot = onTapSlot
        )
        Text(
            text = ")",
            fontSize = (23f * scale).coerceAtLeast(13f).sp,
            color = parenColor,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun RootNodeView(
    node: RootNode,
    activeSlotId: String?,
    activeIndex: Int?,
    highlightedEmptySlotId: String?,
    caretAlpha: Float,
    scale: Float,
    onTapSlot: (slotId: String, indexInSlot: Int?) -> Unit
) {
    val radColor = MaterialTheme.colorScheme.onSurface
    val degScale = (scale * 0.60f).coerceAtLeast(0.46f)
    val density = LocalDensity.current
    val hookWidthPx = with(density) { (16f * scale).dp.roundToPx() }
    val topPadPx = with(density) { (5f * scale).dp.roundToPx() }
    val rightPadPx = with(density) { (4f * scale).dp.roundToPx() }
    val leftExtraForDegPx = with(density) { (4f * scale).dp.roundToPx() }
    val strokeWidthPx = with(density) { (2.1f * scale).coerceAtLeast(1.3f).dp.toPx() }

    Layout(
        modifier = Modifier.padding(horizontal = (2f * scale).dp),
        content = {
            if (node.degree != null) {
                MathSlotRenderer(
                    slot = node.degree,
                    activeSlotId = activeSlotId,
                    activeIndex = activeIndex,
                    highlightedEmptySlotId = highlightedEmptySlotId,
                    caretAlpha = caretAlpha,
                    scale = degScale,
                    onTapSlot = onTapSlot
                )
            }
            Box(
                modifier = Modifier
                    .drawBehind {
                        // Draw overline vinculum + left radical arm attached to radicand box!
                        val stroke = Stroke(
                            width = strokeWidthPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val radLeft = 0f
                        val topY = strokeWidthPx
                        val bottomY = size.height - strokeWidthPx
                        val hookX = -hookWidthPx.toFloat()
                        val valleyX = hookX + hookWidthPx * 0.42f
                        val tickStartX = hookX
                        val tickY = size.height * 0.56f

                        val path = Path().apply {
                            moveTo(tickStartX, tickY)
                            lineTo(tickStartX + hookWidthPx * 0.18f, tickY - hookWidthPx * 0.1f)
                            lineTo(valleyX, bottomY)
                            lineTo(radLeft, topY)
                            lineTo(size.width + rightPadPx, topY)
                        }
                        drawPath(path = path, color = radColor, style = stroke)
                    }
                    .padding(top = (5f * scale).dp, start = (3f * scale).dp, end = (3f * scale).dp)
            ) {
                MathSlotRenderer(
                    slot = node.radicand,
                    activeSlotId = activeSlotId,
                    activeIndex = activeIndex,
                    highlightedEmptySlotId = highlightedEmptySlotId,
                    caretAlpha = caretAlpha,
                    scale = scale,
                    onTapSlot = onTapSlot
                )
            }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        if (node.degree != null) {
            val degPlaceable = measurables[0].measure(loose)
            val radPlaceable = measurables[1].measure(loose)

            val degSectionWidth = max(hookWidthPx, degPlaceable.width + leftExtraForDegPx)
            val totalWidth = degSectionWidth + radPlaceable.width + rightPadPx
            val totalHeight = max(radPlaceable.height + topPadPx / 2, degPlaceable.height + (radPlaceable.height * 0.45f).roundToInt())

            layout(totalWidth, totalHeight) {
                val radY = totalHeight - radPlaceable.height
                val degY = (radY + (radPlaceable.height * 0.42f).roundToInt() - degPlaceable.height).coerceAtLeast(0)
                val degX = (degSectionWidth - hookWidthPx * 0.5f - degPlaceable.width).roundToInt().coerceAtLeast(0)
                degPlaceable.placeRelative(degX, degY)
                radPlaceable.placeRelative(degSectionWidth, radY)
            }
        } else {
            val radPlaceable = measurables[0].measure(loose)
            val totalWidth = hookWidthPx + radPlaceable.width + rightPadPx
            val totalHeight = radPlaceable.height

            layout(totalWidth, totalHeight) {
                radPlaceable.placeRelative(hookWidthPx, 0)
            }
        }
    }
}

@Composable
private fun FractionNodeView(
    node: FractionNode,
    activeSlotId: String?,
    activeIndex: Int?,
    highlightedEmptySlotId: String?,
    caretAlpha: Float,
    scale: Float,
    onTapSlot: (slotId: String, indexInSlot: Int?) -> Unit
) {
    val fracScale = (scale * 0.84f).coerceAtLeast(0.55f)
    val barColor = MaterialTheme.colorScheme.onSurface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .padding(horizontal = (4f * scale).dp)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = (4f * scale).dp, vertical = (2f * scale).dp),
            contentAlignment = Alignment.Center
        ) {
            MathSlotRenderer(
                slot = node.numerator,
                activeSlotId = activeSlotId,
                activeIndex = activeIndex,
                highlightedEmptySlotId = highlightedEmptySlotId,
                caretAlpha = caretAlpha,
                scale = fracScale,
                onTapSlot = onTapSlot
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = (32f * scale).dp)
                .height((2.2f * scale).coerceAtLeast(1.4f).dp)
                .clip(RoundedCornerShape(1.dp))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .padding(horizontal = (4f * scale).dp, vertical = (2f * scale).dp),
            contentAlignment = Alignment.Center
        ) {
            MathSlotRenderer(
                slot = node.denominator,
                activeSlotId = activeSlotId,
                activeIndex = activeIndex,
                highlightedEmptySlotId = highlightedEmptySlotId,
                caretAlpha = caretAlpha,
                scale = fracScale,
                onTapSlot = onTapSlot
            )
        }
    }
}

@Composable
fun PlaceholderBox(
    slotId: String,
    isFocused: Boolean,
    isHighlightedWarning: Boolean,
    caretAlpha: Float,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val idleBorder = MaterialTheme.colorScheme.outline
    val idleBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val warnColor = MaterialTheme.colorScheme.error
    val warnBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)

    val borderColor = when {
        isHighlightedWarning -> warnColor
        isFocused -> primaryColor
        else -> idleBorder
    }
    val fillColor = when {
        isHighlightedWarning -> warnBg
        isFocused -> primaryBg
        else -> idleBg
    }

    val boxWidth = (34f * scale).coerceAtLeast(22f).dp
    val boxHeight = (34f * scale).coerceAtLeast(22f).dp
    val desc = stringResource(R.string.placeholder_box_desc)

    Box(
        modifier = modifier
            .testTag("placeholder_box_$slotId")
            .semantics { contentDescription = desc }
            .clickable(onClick = onClick)
            .padding((2f * scale).dp)
            .size(width = boxWidth, height = boxHeight)
            .drawBehind {
                val corner = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                drawRoundRect(
                    color = fillColor,
                    cornerRadius = corner
                )
                val strokeStyle = if (isFocused || isHighlightedWarning) {
                    Stroke(width = 2.dp.toPx())
                } else {
                    Stroke(
                        width = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = corner,
                    style = strokeStyle
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isFocused) {
            BlinkingCaret(alpha = caretAlpha, scale = scale * 0.8f)
        }
    }
}

@Composable
private fun BlinkingCaret(
    alpha: Float,
    scale: Float
) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    val h = (26f * scale).coerceAtLeast(14f).dp
    Canvas(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(width = 2.2.dp, height = h)
    ) {
        drawLine(
            color = color,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Round
        )
    }
}
