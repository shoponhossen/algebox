package com.example.ui

import androidx.lifecycle.ViewModel
import com.example.model.EditorCursor
import com.example.model.EquationEntry
import com.example.model.MathTemplate
import com.example.model.SymbolNode
import com.example.model.areAllChildrenEmpty
import com.example.model.childSlots
import com.example.model.collectCursorStops
import com.example.model.createTemplateNode
import com.example.model.extractVariables
import com.example.model.findFirstEmptySlotId
import com.example.model.findParentOfSlot
import com.example.model.findSlot
import com.example.model.isBlank
import com.example.model.updateSlot
import com.example.solver.AlgebraSolver
import com.example.solver.SolveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AlgebraSolverUiState(
    val equations: List<EquationEntry>,
    val cursor: EditorCursor,
    val detectedVariables: List<String> = emptyList(),
    val solveResult: SolveResult? = null,
    val isKeyboardVisible: Boolean = true,
    val showSteps: Boolean = true,
    val highlightedEmptySlotId: String? = null
) {
    val nonBlankEquationCount: Int
        get() = equations.count { !it.root.isBlank() }

    val requiredEquationCount: Int
        get() = if (detectedVariables.size >= 2) detectedVariables.size else 1

    val missingEquationsCount: Int
        get() = (requiredEquationCount - nonBlankEquationCount).coerceAtLeast(0)
}

class AlgebraSolverViewModel : ViewModel() {

    private val initialEq = EquationEntry()
    private val _uiState = MutableStateFlow(
        AlgebraSolverUiState(
            equations = listOf(initialEq),
            cursor = EditorCursor(
                equationIndex = 0,
                slotId = initialEq.root.id,
                indexInSlot = 0
            )
        )
    )
    val uiState: StateFlow<AlgebraSolverUiState> = _uiState.asStateFlow()

    fun onTapSlot(equationIndex: Int, slotId: String, indexInSlot: Int? = null) {
        _uiState.update { state ->
            val eq = state.equations.getOrNull(equationIndex) ?: return@update state
            val slot = eq.root.findSlot(slotId) ?: return@update state
            val clampedIdx = (indexInSlot ?: slot.nodes.size).coerceIn(0, slot.nodes.size)
            state.copy(
                cursor = EditorCursor(equationIndex, slotId, clampedIdx),
                isKeyboardVisible = true,
                highlightedEmptySlotId = null
            )
        }
    }

    fun onSymbolInput(symbol: String) {
        _uiState.update { state ->
            val eqIdx = state.cursor.equationIndex.coerceIn(0, state.equations.lastIndex)
            val eq = state.equations[eqIdx]
            val targetSlot = eq.root.findSlot(state.cursor.slotId) ?: eq.root
            val insertAt = state.cursor.indexInSlot.coerceIn(0, targetSlot.nodes.size)

            val newNode = SymbolNode(value = symbol)
            val updatedRoot = eq.root.updateSlot(targetSlot.id) { slot ->
                val mutable = slot.nodes.toMutableList()
                mutable.add(insertAt, newNode)
                slot.copy(nodes = mutable)
            }

            val updatedEquations = state.equations.toMutableList()
            updatedEquations[eqIdx] = eq.copy(root = updatedRoot)
            val vars = collectVars(updatedEquations)

            state.copy(
                equations = updatedEquations,
                cursor = EditorCursor(eqIdx, targetSlot.id, insertAt + 1),
                detectedVariables = vars,
                solveResult = null,
                highlightedEmptySlotId = null
            )
        }
    }

    fun onTemplateInsert(template: MathTemplate) {
        _uiState.update { state ->
            val eqIdx = state.cursor.equationIndex.coerceIn(0, state.equations.lastIndex)
            val eq = state.equations[eqIdx]
            val targetSlot = eq.root.findSlot(state.cursor.slotId) ?: eq.root
            val insertAt = state.cursor.indexInSlot.coerceIn(0, targetSlot.nodes.size)

            val (newNode, focusSlotId) = createTemplateNode(template)
            val updatedRoot = eq.root.updateSlot(targetSlot.id) { slot ->
                val mutable = slot.nodes.toMutableList()
                mutable.add(insertAt, newNode)
                slot.copy(nodes = mutable)
            }

            val updatedEquations = state.equations.toMutableList()
            updatedEquations[eqIdx] = eq.copy(root = updatedRoot)
            val vars = collectVars(updatedEquations)

            state.copy(
                equations = updatedEquations,
                cursor = EditorCursor(eqIdx, focusSlotId, 0),
                detectedVariables = vars,
                solveResult = null,
                highlightedEmptySlotId = null
            )
        }
    }

    fun onBackspace() {
        _uiState.update { state ->
            val eqIdx = state.cursor.equationIndex.coerceIn(0, state.equations.lastIndex)
            val eq = state.equations[eqIdx]
            val currentSlot = eq.root.findSlot(state.cursor.slotId) ?: eq.root
            val idx = state.cursor.indexInSlot.coerceIn(0, currentSlot.nodes.size)

            if (idx > 0) {
                val prevNode = currentSlot.nodes[idx - 1]
                if (prevNode is SymbolNode || prevNode.areAllChildrenEmpty()) {
                    val updatedRoot = eq.root.updateSlot(currentSlot.id) { slot ->
                        val mutable = slot.nodes.toMutableList()
                        mutable.removeAt(idx - 1)
                        slot.copy(nodes = mutable)
                    }
                    val updatedEquations = state.equations.toMutableList()
                    updatedEquations[eqIdx] = eq.copy(root = updatedRoot)
                    return@update state.copy(
                        equations = updatedEquations,
                        cursor = EditorCursor(eqIdx, currentSlot.id, idx - 1),
                        detectedVariables = collectVars(updatedEquations),
                        solveResult = null,
                        highlightedEmptySlotId = null
                    )
                } else {
                    val lastChild = prevNode.childSlots().last()
                    return@update state.copy(
                        cursor = EditorCursor(eqIdx, lastChild.id, lastChild.nodes.size),
                        highlightedEmptySlotId = null
                    )
                }
            } else {
                if (currentSlot.id != eq.root.id) {
                    val parentInfo = eq.root.findParentOfSlot(currentSlot.id)
                    if (parentInfo != null) {
                        val (parentSlot, nodeIdxInParent) = parentInfo
                        val parentNode = parentSlot.nodes[nodeIdxInParent]
                        if (currentSlot.nodes.isEmpty() && parentNode.areAllChildrenEmpty()) {
                            val updatedRoot = eq.root.updateSlot(parentSlot.id) { slot ->
                                val mutable = slot.nodes.toMutableList()
                                mutable.removeAt(nodeIdxInParent)
                                slot.copy(nodes = mutable)
                            }
                            val updatedEquations = state.equations.toMutableList()
                            updatedEquations[eqIdx] = eq.copy(root = updatedRoot)
                            return@update state.copy(
                                equations = updatedEquations,
                                cursor = EditorCursor(eqIdx, parentSlot.id, nodeIdxInParent),
                                detectedVariables = collectVars(updatedEquations),
                                solveResult = null,
                                highlightedEmptySlotId = null
                            )
                        }
                    }
                }
                val stops = eq.root.collectCursorStops()
                val currStopIdx = stops.indexOfFirst {
                    it.slotId == state.cursor.slotId && it.indexInSlot == state.cursor.indexInSlot
                }
                if (currStopIdx > 0) {
                    val prevStop = stops[currStopIdx - 1]
                    return@update state.copy(
                        cursor = EditorCursor(eqIdx, prevStop.slotId, prevStop.indexInSlot),
                        highlightedEmptySlotId = null
                    )
                }
                state
            }
        }
    }

    fun onMoveLeft() {
        _uiState.update { state ->
            val eqIdx = state.cursor.equationIndex.coerceIn(0, state.equations.lastIndex)
            val eq = state.equations[eqIdx]
            val stops = eq.root.collectCursorStops()
            val currIdx = stops.indexOfFirst {
                it.slotId == state.cursor.slotId && it.indexInSlot == state.cursor.indexInSlot
            }
            when {
                currIdx > 0 -> {
                    val prev = stops[currIdx - 1]
                    state.copy(cursor = EditorCursor(eqIdx, prev.slotId, prev.indexInSlot))
                }
                eqIdx > 0 -> {
                    val prevEq = state.equations[eqIdx - 1]
                    val prevStops = prevEq.root.collectCursorStops()
                    val last = prevStops.last()
                    state.copy(cursor = EditorCursor(eqIdx - 1, last.slotId, last.indexInSlot))
                }
                else -> state
            }
        }
    }

    fun onMoveRight() {
        _uiState.update { state ->
            val eqIdx = state.cursor.equationIndex.coerceIn(0, state.equations.lastIndex)
            val eq = state.equations[eqIdx]
            val stops = eq.root.collectCursorStops()
            val currIdx = stops.indexOfFirst {
                it.slotId == state.cursor.slotId && it.indexInSlot == state.cursor.indexInSlot
            }
            when {
                currIdx != -1 && currIdx < stops.lastIndex -> {
                    val next = stops[currIdx + 1]
                    state.copy(cursor = EditorCursor(eqIdx, next.slotId, next.indexInSlot))
                }
                eqIdx < state.equations.lastIndex -> {
                    val nextEq = state.equations[eqIdx + 1]
                    val nextStops = nextEq.root.collectCursorStops()
                    val first = nextStops.first()
                    state.copy(cursor = EditorCursor(eqIdx + 1, first.slotId, first.indexInSlot))
                }
                else -> state
            }
        }
    }

    fun onNextPlaceholderBox() {
        _uiState.update { state ->
            val eqIdx = state.cursor.equationIndex.coerceIn(0, state.equations.lastIndex)
            val eq = state.equations[eqIdx]
            val stops = eq.root.collectCursorStops()
            val currPos = stops.indexOfFirst {
                it.slotId == state.cursor.slotId && it.indexInSlot == state.cursor.indexInSlot
            }.coerceAtLeast(0)

            val emptyInCurrent = stops.drop(currPos + 1).firstOrNull { stop ->
                stop.slotId != state.cursor.slotId &&
                    eq.root.findSlot(stop.slotId)?.nodes?.isEmpty() == true
            }
            if (emptyInCurrent != null) {
                return@update state.copy(
                    cursor = EditorCursor(eqIdx, emptyInCurrent.slotId, 0),
                    highlightedEmptySlotId = null
                )
            }

            for (offset in 1..state.equations.size) {
                val targetEqIdx = (eqIdx + offset) % state.equations.size
                val targetEq = state.equations[targetEqIdx]
                val emptyId = targetEq.root.findFirstEmptySlotId()
                if (emptyId != null && !(targetEqIdx == eqIdx && emptyId == state.cursor.slotId)) {
                    return@update state.copy(
                        cursor = EditorCursor(targetEqIdx, emptyId, 0),
                        highlightedEmptySlotId = null
                    )
                }
            }

            if (eqIdx < state.equations.lastIndex) {
                val nextEq = state.equations[eqIdx + 1]
                state.copy(cursor = EditorCursor(eqIdx + 1, nextEq.root.id, nextEq.root.nodes.size))
            } else {
                state.copy(cursor = EditorCursor(eqIdx, eq.root.id, eq.root.nodes.size))
            }
        }
    }

    fun onAddEquation() {
        _uiState.update { state ->
            if (state.equations.size >= 3) return@update state
            val newEq = EquationEntry()
            val updated = state.equations + newEq
            state.copy(
                equations = updated,
                cursor = EditorCursor(updated.lastIndex, newEq.root.id, 0),
                solveResult = null,
                isKeyboardVisible = true
            )
        }
    }

    fun onRemoveEquation(index: Int) {
        _uiState.update { state ->
            if (state.equations.size <= 1 || index !in state.equations.indices) return@update state
            val updated = state.equations.toMutableList().apply { removeAt(index) }
            val newEqIdx = index.coerceAtMost(updated.lastIndex)
            val newSlot = updated[newEqIdx].root
            state.copy(
                equations = updated,
                cursor = EditorCursor(newEqIdx, newSlot.id, newSlot.nodes.size),
                detectedVariables = collectVars(updated),
                solveResult = null,
                highlightedEmptySlotId = null
            )
        }
    }

    fun onClearEquation(index: Int) {
        _uiState.update { state ->
            if (index !in state.equations.indices) return@update state
            val fresh = EquationEntry()
            val updated = state.equations.toMutableList()
            updated[index] = fresh
            state.copy(
                equations = updated,
                cursor = EditorCursor(index, fresh.root.id, 0),
                detectedVariables = collectVars(updated),
                solveResult = null,
                highlightedEmptySlotId = null
            )
        }
    }

    fun onClearAll() {
        val fresh = EquationEntry()
        _uiState.value = AlgebraSolverUiState(
            equations = listOf(fresh),
            cursor = EditorCursor(0, fresh.root.id, 0),
            isKeyboardVisible = true
        )
    }

    fun onToggleKeyboard(visible: Boolean? = null) {
        _uiState.update { state ->
            state.copy(isKeyboardVisible = visible ?: !state.isKeyboardVisible)
        }
    }

    fun onToggleSteps() {
        _uiState.update { state ->
            state.copy(showSteps = !state.showSteps)
        }
    }

    fun onSolve() {
        val currentState = _uiState.value
        val result = AlgebraSolver.solve(currentState.equations)

        when (result) {
            is SolveResult.IncompletePlaceholder -> {
                _uiState.update { state ->
                    state.copy(
                        solveResult = result,
                        cursor = EditorCursor(result.equationIndex, result.firstEmptySlotId, 0),
                        highlightedEmptySlotId = result.firstEmptySlotId,
                        isKeyboardVisible = true
                    )
                }
            }
            is SolveResult.NeedsMoreEquations -> {
                _uiState.update { state ->
                    val mutableEqs = state.equations.toMutableList()
                    while (mutableEqs.size < result.requiredCount && mutableEqs.size < 3) {
                        mutableEqs.add(EquationEntry())
                    }
                    val firstBlankIdx = mutableEqs.indexOfFirst { it.root.isBlank() }
                        .takeIf { it >= 0 } ?: mutableEqs.lastIndex
                    val targetSlot = mutableEqs[firstBlankIdx].root
                    state.copy(
                        equations = mutableEqs,
                        cursor = EditorCursor(firstBlankIdx, targetSlot.id, 0),
                        solveResult = result,
                        isKeyboardVisible = true,
                        highlightedEmptySlotId = null
                    )
                }
            }
            else -> {
                _uiState.update { state ->
                    state.copy(
                        solveResult = result,
                        showSteps = true,
                        highlightedEmptySlotId = null
                    )
                }
            }
        }
    }

    private fun collectVars(equations: List<EquationEntry>): List<String> {
        val set = linkedSetOf<String>()
        equations.forEach { set.addAll(it.root.extractVariables()) }
        return listOf("x", "y", "z").filter { it in set }
    }
}
