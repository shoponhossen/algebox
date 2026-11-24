package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.MathTemplate
import com.example.ui.theme.MathSerifFont

@Composable
fun MathKeyboard(
    onSymbol: (String) -> Unit,
    onTemplate: (MathTemplate) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onNextBox: () -> Unit,
    onSolve: () -> Unit,
    onHideKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header strip: Template label + Hide Keyboard button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Templates  •  Tap [ ] boxes to fill",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onHideKeyboard)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("hide_keyboard_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardHide,
                        contentDescription = stringResource(R.string.hide_keyboard),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Hide",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Visual Placeholder Template Strip (the 8 v1 templates)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TemplateKeyTile(
                    tag = "key_template_power",
                    desc = "Power placeholder box",
                    onClick = { onTemplate(MathTemplate.POWER) }
                ) { PowerPreview(baseBox = true, expText = null) }

                TemplateKeyTile(
                    tag = "key_template_square",
                    desc = "Square placeholder box",
                    onClick = { onTemplate(MathTemplate.SQUARE) }
                ) { PowerPreview(baseBox = true, expText = "2") }

                TemplateKeyTile(
                    tag = "key_template_ten_power",
                    desc = "10 to the power placeholder box",
                    onClick = { onTemplate(MathTemplate.TEN_POWER) }
                ) { PowerPreview(baseBox = false, baseText = "10", expText = null) }

                TemplateKeyTile(
                    tag = "key_template_fraction",
                    desc = "Fraction placeholder box",
                    onClick = { onTemplate(MathTemplate.FRACTION) }
                ) { FractionPreview() }

                TemplateKeyTile(
                    tag = "key_template_sqrt",
                    desc = "Square root placeholder box",
                    onClick = { onTemplate(MathTemplate.SQRT) }
                ) { RootPreview(withDegree = false) }

                TemplateKeyTile(
                    tag = "key_template_nth_root",
                    desc = "Nth root placeholder box",
                    onClick = { onTemplate(MathTemplate.NTH_ROOT) }
                ) { RootPreview(withDegree = true) }

                TemplateKeyTile(
                    tag = "key_template_log_any",
                    desc = "Logarithm base placeholder box",
                    onClick = { onTemplate(MathTemplate.LOG_ANY) }
                ) { LogPreview(base10 = false) }

                TemplateKeyTile(
                    tag = "key_template_log_10",
                    desc = "Log base 10 placeholder box",
                    onClick = { onTemplate(MathTemplate.LOG_10) }
                ) { LogPreview(base10 = true) }
            }

            // 2. Variables & Cursor Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("x", "y", "z").forEach { v ->
                    KeypadButton(
                        label = v,
                        isVar = true,
                        tag = "key_var_$v",
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { onSymbol(v) },
                        modifier = Modifier.weight(1f)
                    )
                }
                KeypadButton(
                    label = "(",
                    tag = "key_lparen",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onSymbol("(") },
                    modifier = Modifier.weight(0.85f)
                )
                KeypadButton(
                    label = ")",
                    tag = "key_rparen",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onSymbol(")") },
                    modifier = Modifier.weight(0.85f)
                )
                KeypadIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    desc = "Move cursor left",
                    tag = "key_left",
                    onClick = onMoveLeft,
                    modifier = Modifier.weight(0.95f)
                )
                KeypadIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    desc = "Move cursor right",
                    tag = "key_right",
                    onClick = onMoveRight,
                    modifier = Modifier.weight(0.95f)
                )
                Surface(
                    onClick = onNextBox,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(44.dp)
                        .testTag("key_next_box")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tab,
                            contentDescription = stringResource(R.string.next_box),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "Box",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. Main Numeric & Operator Grid (5 columns x 4 rows)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DigitKey("7", Modifier.weight(1f)) { onSymbol("7") }
                    DigitKey("8", Modifier.weight(1f)) { onSymbol("8") }
                    DigitKey("9", Modifier.weight(1f)) { onSymbol("9") }
                    OperatorKey("÷", "key_div", Modifier.weight(1f)) { onSymbol("÷") }
                    KeypadIconButton(
                        icon = Icons.AutoMirrored.Rounded.Backspace,
                        desc = "Backspace",
                        tag = "key_backspace",
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onBackspace,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DigitKey("4", Modifier.weight(1f)) { onSymbol("4") }
                    DigitKey("5", Modifier.weight(1f)) { onSymbol("5") }
                    DigitKey("6", Modifier.weight(1f)) { onSymbol("6") }
                    OperatorKey("×", "key_mul", Modifier.weight(1f)) { onSymbol("×") }
                    KeypadButton(
                        label = "AC",
                        tag = "key_clear",
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onClear,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DigitKey("1", Modifier.weight(1f)) { onSymbol("1") }
                    DigitKey("2", Modifier.weight(1f)) { onSymbol("2") }
                    DigitKey("3", Modifier.weight(1f)) { onSymbol("3") }
                    OperatorKey("−", "key_sub", Modifier.weight(1f)) { onSymbol("-") }
                    KeypadButton(
                        label = "=",
                        tag = "key_equals",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { onSymbol("=") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DigitKey("0", Modifier.weight(1f)) { onSymbol("0") }
                    DigitKey(".", Modifier.weight(1f)) { onSymbol(".") }
                    OperatorKey("+", "key_add", Modifier.weight(1f)) { onSymbol("+") }
                    Surface(
                        onClick = onSolve,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .weight(2.06f)
                            .height(48.dp)
                            .testTag("key_solve")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.solve_button),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitKey(
    digit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    KeypadButton(
        label = digit,
        tag = "key_digit_$digit",
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun OperatorKey(
    symbol: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    KeypadButton(
        label = symbol,
        tag = tag,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.primary,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun KeypadButton(
    label: String,
    tag: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVar: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .height(48.dp)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = if (isVar) MathSerifFont else MaterialTheme.typography.titleMedium.fontFamily,
                fontStyle = if (isVar) FontStyle.Italic else FontStyle.Normal,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isVar) 19.sp else 18.sp
            )
        }
    }
}

@Composable
private fun KeypadIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .height(48.dp)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TemplateKeyTile(
    tag: String,
    desc: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 58.dp)
            .testTag(tag)
            .semantics { contentDescription = desc }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun MiniBox(sizeDp: Int = 12) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(3.dp)
            )
    )
}

@Composable
private fun PowerPreview(
    baseBox: Boolean,
    baseText: String = "",
    expText: String?
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 5.dp)) {
            if (baseBox) {
                MiniBox(13)
            } else {
                Text(
                    text = baseText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        if (expText != null) {
            Text(
                text = expText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            MiniBox(9)
        }
    }
}

@Composable
private fun FractionPreview() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MiniBox(11)
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(1.8.dp)
                .background(MaterialTheme.colorScheme.onSurface)
        )
        MiniBox(11)
    }
}

@Composable
private fun RootPreview(withDegree: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (withDegree) {
            Box(modifier = Modifier.padding(bottom = 8.dp, end = 1.dp)) {
                MiniBox(8)
            }
        }
        Text(
            text = "√",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        MiniBox(13)
    }
}

@Composable
private fun LogPreview(base10: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "log",
            fontFamily = MathSerifFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(modifier = Modifier.padding(top = 7.dp, start = 1.dp, end = 3.dp)) {
            if (base10) {
                Text(
                    text = "10",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                MiniBox(8)
            }
        }
        MiniBox(12)
    }
}
