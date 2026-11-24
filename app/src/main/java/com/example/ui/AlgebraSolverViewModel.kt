package com.example.ui

import androidx.lifecycle.ViewModel
import com.example.model.EditorCursor
import com.example.model.EquationEntry
import com.example.model.FractionNode
import com.example.model.LogNode
import com.example.model.MathNode
import com.example.model.MathSlot
import com.example.model.MathTemplate
import com.example.model.PowerNode
import com.example.model.RootNode
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
import com.example.model.newNodeId
import com.example.model.updateSlot
import com.example.solver.AlgebraSolver
import com.example.solver.SolveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AlgebraExample(
    val id: String,
    val title: String,
    val previewLabel: String,
    val equations: List<EquationEntry>
)

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

    val examples: List<AlgebraExample> by lazy { buildExamples() }

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
                    // Enter the last child slot of the structured node
                    val lastChild = prevNode.childSlots().last()
                    return@update state.copy(
                        cursor = EditorCursor(eqIdx, lastChild.id, lastChild.nodes.size),
                        highlightedEmptySlotId = null
                    )
                }
            } else {
                // At index 0 of currentSlot
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
                // Otherwise just move cursor left
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

            // Look for empty slots in current equation after cursor
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

            // Look in subsequent equations, wrapping around
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

            // If no empty box exists, jump to end of current root or next equation
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

    fun onLoadExample(example: AlgebraExample) {
        // Clone with fresh IDs
        val cloned = example.equations.map { eq -> EquationEntry(root = cloneSlot(eq.root)) }
        val lastRoot = cloned.first().root
        val vars = collectVars(cloned)
        val result = AlgebraSolver.solve(cloned)
        _uiState.update { state ->
            state.copy(
                equations = cloned,
                cursor = EditorCursor(0, lastRoot.id, lastRoot.nodes.size),
                detectedVariables = vars,
                solveResult = result,
                showSteps = true,
                highlightedEmptySlotId = null
            )
        }
    }

    private fun cloneSlot(slot: MathSlot): MathSlot {
        return MathSlot(
            id = newNodeId(),
            nodes = slot.nodes.map { node ->
                when (node) {
                    is SymbolNode -> SymbolNode(value = node.value)
                    is PowerNode -> PowerNode(
                        base = cloneSlot(node.base),
                        exponent = cloneSlot(node.exponent)
                    )
                    is LogNode -> LogNode(
                        base = cloneSlot(node.base),
                        argument = cloneSlot(node.argument)
                    )
                    is RootNode -> RootNode(
                        degree = node.degree?.let(::cloneSlot),
                        radicand = cloneSlot(node.radicand)
                    )
                    is FractionNode -> FractionNode(
                        numerator = cloneSlot(node.numerator),
                        denominator = cloneSlot(node.denominator)
                    )
                }
            }
        )
    }

    private fun collectVars(equations: List<EquationEntry>): List<String> {
        val set = linkedSetOf<String>()
        equations.forEach { set.addAll(it.root.extractVariables()) }
        return listOf("x", "y", "z").filter { it in set }
    }

    private fun buildExamples(): List<AlgebraExample> {
        fun syms(text: String): List<MathNode> = text.map { SymbolNode(value = it.toString()) }
        fun slot(text: String): MathSlot = MathSlot(nodes = syms(text))

        // 1. Quadratic: x^2 - 5x + 6 = 0
        val quadEq = EquationEntry(
            root = MathSlot(
                nodes = listOf(
                    PowerNode(base = slot("x"), exponent = slot("2")),
                    SymbolNode(value = "-"),
                    SymbolNode(value = "5"),
                    SymbolNode(value = "x"),
                    SymbolNode(value = "+"),
                    SymbolNode(value = "6"),
                    SymbolNode(value = "="),
                    SymbolNode(value = "0")
                )
            )
        )

        // 2. Fraction & Root: (2x + 1)/3 + \sqrt{16} = 7
        val fracRootEq = EquationEntry(
            root = MathSlot(
                nodes = listOf(
                    FractionNode(numerator = slot("2x+1"), denominator = slot("3")),
                    SymbolNode(value = "+"),
                    RootNode(degree = null, radicand = slot("16")),
                    SymbolNode(value = "="),
                    SymbolNode(value = "7")
                )
            )
        )

        // 3. Log & 10^x: 10^x + log_10(100) = 102
        val expLogEq = EquationEntry(
            root = MathSlot(
                nodes = listOf(
                    PowerNode(base = slot("10"), exponent = slot("x")),
                    SymbolNode(value = "+"),
                    LogNode(base = slot("10"), argument = slot("100")),
                    SymbolNode(value = "="),
                    SymbolNode(value = "102")
                )
            )
        )

        // 4. Nth Root & Log_b: \sqrt[3]{x + 1} + log_2(8) = 6
        val nthRootLogEq = EquationEntry(
            root = MathSlot(
                nodes = listOf(
                    RootNode(degree = slot("3"), radicand = slot("x+1")),
                    SymbolNode(value = "+"),
                    LogNode(base = slot("2"), argument = slot("8")),
                    SymbolNode(value = "="),
                    SymbolNode(value = "6")
                )
            )
        )

        // 5. 2-variable system: 2x + y = 7, x - y = 2
        val sys2 = listOf(
            EquationEntry(root = slot("2x+y=7")),
            EquationEntry(root = slot("x-y=2"))
        )

        // 6. 3-variable system: x + y + z = 6, 2x - y + z = 3, x + 2y - z = 2
        val sys3 = listOf(
            EquationEntry(root = slot("x+y+z=6")),
            EquationEntry(root = slot("2x-y+z=3")),
            EquationEntry(root = slot("x+2y-z=2"))
        )

        return listOf(
            AlgebraExample("quad", "Quadratic", "x² − 5x + 6 = 0", listOf(quadEq)),
            AlgebraExample("frac_root", "Fraction & √", "(2x+1)/3 + √16 = 7", listOf(fracRootEq)),
            AlgebraExample("exp_log", "10ˣ & log₁₀", "10ˣ + log₁₀(100) = 102", listOf(expLogEq)),
            AlgebraExample("nth_log", "ⁿ√ & logₐ", "³√(x+1) + log₂(8) = 6", listOf(nthRootLogEq)),
            AlgebraExample("sys2", "2-Var (x, y)", "2x+y=7, x−y=2", sys2),
            AlgebraExample("sys3", "3-Var (x, y, z)", "x+y+z=6 ...", sys3)
        )
    }
}
