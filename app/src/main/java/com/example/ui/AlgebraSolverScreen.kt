package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import shopon.openstudy.algesolver.R
import com.example.model.EquationEntry
import com.example.model.extractVariables
import com.example.solver.SolveResult
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.MathMonoFont
import com.example.ui.theme.MathSerifFont
import com.example.ui.theme.OnAmberContainer

@Composable
fun AlgebraSolverScreen(
    viewModel: AlgebraSolverViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 840.dp

            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        WorkspaceActionBar(
                            onClearAll = viewModel::onClearAll,
                            isKeyboardVisible = uiState.isKeyboardVisible,
                            onToggleKeyboard = { viewModel.onToggleKeyboard() }
                        )
                        WorkspaceScrollArea(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        MathKeyboard(
                            onSymbol = viewModel::onSymbolInput,
                            onTemplate = viewModel::onTemplateInsert,
                            onBackspace = viewModel::onBackspace,
                            onClear = viewModel::onClearAll,
                            onMoveLeft = viewModel::onMoveLeft,
                            onMoveRight = viewModel::onMoveRight,
                            onNextBox = viewModel::onNextPlaceholderBox,
                            onSolve = viewModel::onSolve,
                            onHideKeyboard = { viewModel.onToggleKeyboard(false) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    WorkspaceActionBar(
                        onClearAll = viewModel::onClearAll,
                        isKeyboardVisible = uiState.isKeyboardVisible,
                        onToggleKeyboard = { viewModel.onToggleKeyboard() }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        WorkspaceScrollArea(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!uiState.isKeyboardVisible) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.onToggleKeyboard(true) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("show_keyboard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Keyboard,
                                        contentDescription = stringResource(R.string.show_keyboard),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Keypad")
                                }
                                Button(
                                    onClick = viewModel::onSolve,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("floating_solve_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.solve_button),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.isKeyboardVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        MathKeyboard(
                            onSymbol = viewModel::onSymbolInput,
                            onTemplate = viewModel::onTemplateInsert,
                            onBackspace = viewModel::onBackspace,
                            onClear = viewModel::onClearAll,
                            onMoveLeft = viewModel::onMoveLeft,
                            onMoveRight = viewModel::onMoveRight,
                            onNextBox = viewModel::onNextPlaceholderBox,
                            onSolve = viewModel::onSolve,
                            onHideKeyboard = { viewModel.onToggleKeyboard(false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceActionBar(
    onClearAll: () -> Unit,
    isKeyboardVisible: Boolean,
    onToggleKeyboard: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_algebox_logo_1795532687770),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onToggleKeyboard,
                modifier = Modifier.testTag("topbar_toggle_keyboard")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Keyboard,
                    contentDescription = if (isKeyboardVisible) {
                        stringResource(R.string.hide_keyboard)
                    } else {
                        stringResource(R.string.show_keyboard)
                    },
                    tint = if (isKeyboardVisible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(
                onClick = onClearAll,
                modifier = Modifier.testTag("topbar_clear_all")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.clear_all),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkspaceScrollArea(
    uiState: AlgebraSolverUiState,
    viewModel: AlgebraSolverViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = if (uiState.isKeyboardVisible) 18.dp else 84.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Equation Input Cards
        items(uiState.equations.size, key = { idx -> uiState.equations[idx].id }) { index ->
            val equation = uiState.equations[index]
            EquationEditorCard(
                equationIndex = index,
                equation = equation,
                isFocused = uiState.cursor.equationIndex == index,
                canRemove = uiState.equations.size > 1,
                uiState = uiState,
                onTapSlot = { slotId, indexInSlot ->
                    viewModel.onTapSlot(index, slotId, indexInSlot)
                },
                onClear = { viewModel.onClearEquation(index) },
                onRemove = { viewModel.onRemoveEquation(index) }
            )
        }

        // Multi-Variable Prompt & System Controls
        item(key = "multi_var_banner") {
            MultiVariableBanner(
                uiState = uiState,
                onAddEquation = viewModel::onAddEquation
            )
        }

        // Solution & Step-by-Step Result
        if (uiState.solveResult != null) {
            item(key = "solve_result") {
                SolveResultSection(
                    result = uiState.solveResult,
                    showSteps = uiState.showSteps,
                    onToggleSteps = viewModel::onToggleSteps
                )
            }
        }
    }
}

@Composable
private fun EquationEditorCard(
    equationIndex: Int,
    equation: EquationEntry,
    isFocused: Boolean,
    canRemove: Boolean,
    uiState: AlgebraSolverUiState,
    onTapSlot: (slotId: String, indexInSlot: Int?) -> Unit,
    onClear: () -> Unit,
    onRemove: () -> Unit
) {
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val borderWidth = if (isFocused) 2.dp else 1.dp
    val eqVars = equation.root.extractVariables()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 700.dp)
            .testTag("equation_card_$equationIndex"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 3.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTapSlot(equation.root.id, equation.root.nodes.size) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.equation_label, equationIndex + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isFocused) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (eqVars.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            eqVars.forEach { v ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = v,
                                        fontFamily = MathSerifFont,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (equation.root.nodes.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onClear)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (canRemove) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("remove_equation_$equationIndex")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.remove_equation, equationIndex + 1),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                MathEquationCanvas(
                    equationIndex = equationIndex,
                    rootSlot = equation.root,
                    cursor = uiState.cursor,
                    highlightedEmptySlotId = uiState.highlightedEmptySlotId,
                    onTapSlot = onTapSlot
                )
            }
        }
    }
}

@Composable
private fun MultiVariableBanner(
    uiState: AlgebraSolverUiState,
    onAddEquation: () -> Unit
) {
    val vars = uiState.detectedVariables
    if (vars.size >= 2) {
        val varList = vars.joinToString(", ")
        val missing = uiState.missingEquationsCount

        if (missing > 0) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AmberContainer,
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("multi_var_prompt_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.multi_var_prompt, vars.size, varList, missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnAmberContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (uiState.equations.size < vars.size && uiState.equations.size < 3) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onAddEquation,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberWarning,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            modifier = Modifier.testTag("prompt_add_equation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Eq ${uiState.equations.size + 1}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.multi_var_ready, vars.size, varList),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    } else if (uiState.equations.size < 3) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = onAddEquation,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.testTag("add_equation_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.add_equation, uiState.equations.size + 1),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.add_equation, uiState.equations.size + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SolveResultSection(
    result: SolveResult,
    showSteps: Boolean,
    onToggleSteps: () -> Unit
) {
    when (result) {
        is SolveResult.Success -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("solution_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = result.summaryTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.answers.forEach { ans ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${ans.label} =",
                                            fontFamily = MathSerifFont,
                                            fontStyle = FontStyle.Italic,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = ans.primaryValue,
                                                fontFamily = MathMonoFont,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (ans.secondaryValue != null) {
                                                Text(
                                                    text = ans.secondaryValue,
                                                    fontFamily = MathMonoFont,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (result.steps.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("steps_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onToggleSteps)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.steps_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = if (showSteps) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (showSteps) {
                                        stringResource(R.string.hide_steps)
                                    } else {
                                        stringResource(R.string.show_steps)
                                    },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = showSteps) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                    result.steps.forEach { step ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = step.stepNumber.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = step.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = step.mathDetail,
                                                    fontFamily = MathMonoFont,
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        is SolveResult.NeedsMoreEquations -> {
            FeedbackBanner(
                icon = Icons.Rounded.Info,
                title = "More Equations Required",
                message = "Detected ${result.detectedVars.size} variables (${result.detectedVars.joinToString(", ")}). Fill in Equation ${result.currentCount + 1} above to solve.",
                containerColor = AmberContainer,
                contentColor = OnAmberContainer,
                accentColor = AmberWarning
            )
        }
        is SolveResult.IncompletePlaceholder -> {
            FeedbackBanner(
                icon = Icons.Rounded.Info,
                title = "Empty Placeholder Box",
                message = result.message,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                accentColor = MaterialTheme.colorScheme.error
            )
        }
        is SolveResult.Error -> {
            FeedbackBanner(
                icon = Icons.Rounded.ErrorOutline,
                title = "Check Equation",
                message = result.message,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                accentColor = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun FeedbackBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feedback_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
        }
    }
}
