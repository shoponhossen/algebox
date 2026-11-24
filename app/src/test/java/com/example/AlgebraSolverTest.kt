package com.example

import com.example.model.EquationEntry
import com.example.model.FractionNode
import com.example.model.LogNode
import com.example.model.MathSlot
import com.example.model.PowerNode
import com.example.model.RootNode
import com.example.model.SymbolNode
import com.example.solver.AlgebraSolver
import com.example.solver.SolveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgebraSolverTest {

    private fun slot(text: String): MathSlot =
        MathSlot(nodes = text.map { SymbolNode(value = it.toString()) })

    @Test
    fun solvesQuadraticWithSquareTemplate() {
        // x^2 - 5x + 6 = 0 -> x1 = 3, x2 = 2
        val eq = EquationEntry(
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
        val res = AlgebraSolver.solve(listOf(eq)) as SolveResult.Success
        val vals = res.answers.map { it.primaryValue }.toSet()
        assertEquals(setOf("3", "2"), vals)
    }

    @Test
    fun solvesTenPowerAndLog10Equation() {
        // 10^x + log_10(100) = 102 -> 10^x = 100 -> x = 2
        val eq = EquationEntry(
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
        val res = AlgebraSolver.solve(listOf(eq)) as SolveResult.Success
        assertEquals("2", res.answers.first().primaryValue)
    }

    @Test
    fun solvesNthRootAndCustomLogEquation() {
        // \sqrt[3]{x + 1} + log_2(8) = 6 -> \sqrt[3]{x+1} = 3 -> x + 1 = 27 -> x = 26
        val eq = EquationEntry(
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
        val res = AlgebraSolver.solve(listOf(eq)) as SolveResult.Success
        assertEquals("26", res.answers.first().primaryValue)
    }

    @Test
    fun solvesFractionAndSqrtEquation() {
        // (2x + 1)/3 + \sqrt{16} = 7 -> (2x+1)/3 = 3 -> 2x + 1 = 9 -> x = 4
        val eq = EquationEntry(
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
        val res = AlgebraSolver.solve(listOf(eq)) as SolveResult.Success
        assertEquals("4", res.answers.first().primaryValue)
    }

    @Test
    fun promptsForMoreEquationsWhenTwoVariablesUsed() {
        val eq1 = EquationEntry(root = slot("2x+y=7"))
        val res = AlgebraSolver.solve(listOf(eq1))
        assertTrue(res is SolveResult.NeedsMoreEquations)
        val needs = res as SolveResult.NeedsMoreEquations
        assertEquals(listOf("x", "y"), needs.detectedVars)
        assertEquals(2, needs.requiredCount)
    }

    @Test
    fun solvesThreeVariableSystem() {
        // x + y + z = 6, 2x - y + z = 3, x + 2y - z = 2 -> x = 1, y = 2, z = 3
        val eqs = listOf(
            EquationEntry(root = slot("x+y+z=6")),
            EquationEntry(root = slot("2x-y+z=3")),
            EquationEntry(root = slot("x+2y-z=2"))
        )
        val res = AlgebraSolver.solve(eqs) as SolveResult.Success
        assertEquals("1", res.answers.first { it.label == "x" }.primaryValue)
        assertEquals("2", res.answers.first { it.label == "y" }.primaryValue)
        assertEquals("3", res.answers.first { it.label == "z" }.primaryValue)
    }
}
