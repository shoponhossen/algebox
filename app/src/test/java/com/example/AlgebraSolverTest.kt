package com.example

import com.example.model.EquationEntry
import com.example.model.FractionNode
import com.example.model.LogNode
import com.example.model.MathSlot
import com.example.model.MathTemplate
import com.example.model.PowerNode
import com.example.model.RootNode
import com.example.model.SymbolNode
import com.example.model.createTemplateNode
import com.example.solver.AlgebraSolver
import com.example.solver.SolveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgebraSolverTest {

    private fun slot(text: String): MathSlot =
        MathSlot(nodes = text.map { SymbolNode(value = it.toString()) })

    @Test
    fun solvesDecimalAdditionExactlyZeroPointTwoPlusZeroPointOne() {
        // 0.2 + 0.1 -> exact 0.3
        val expr = EquationEntry(root = slot("0.2+0.1"))
        val res = AlgebraSolver.solve(listOf(expr)) as SolveResult.Success
        assertEquals("0.3", res.answers.first().primaryValue)
    }

    @Test
    fun verifiesZeroPointTwoPlusZeroPointOneEqualsZeroPointThree() {
        val eq = EquationEntry(root = slot("0.2+0.1=0.3"))
        val res = AlgebraSolver.solve(listOf(eq)) as SolveResult.Success
        assertTrue(res.answers.first().primaryValue.startsWith("True"))
    }

    @Test
    fun solvesDecimalLinearEquationExactly() {
        // x + 0.1 = 0.3 -> x = 0.2
        val eq = EquationEntry(root = slot("x+0.1=0.3"))
        val res = AlgebraSolver.solve(listOf(eq)) as SolveResult.Success
        assertEquals("0.2", res.answers.first().primaryValue)
    }

    @Test
    fun evaluatesLogEOfE() {
        val (logNode, argId) = createTemplateNode(MathTemplate.LOG_E)
        val eq = EquationEntry(
            root = MathSlot(nodes = listOf((logNode as LogNode).copy(argument = slot("e"))))
        )
        assertTrue(argId.isNotEmpty())
        val res = AlgebraSolver.solve(listOf(eq)) as SolveResult.Success
        assertEquals("1", res.answers.first().primaryValue)
    }

    @Test
    fun solvesCubeAndCubeRootTemplates() {
        // x^3 = 27 -> x = 3
        val (cubeNode, _) = createTemplateNode(MathTemplate.CUBE)
        val eq1 = EquationEntry(
            root = MathSlot(
                nodes = listOf(
                    (cubeNode as PowerNode).copy(base = slot("x")),
                    SymbolNode(value = "="),
                    SymbolNode(value = "2"),
                    SymbolNode(value = "7")
                )
            )
        )
        val res1 = AlgebraSolver.solve(listOf(eq1)) as SolveResult.Success
        assertEquals("3", res1.answers.first().primaryValue)

        // \sqrt[3]{x} = 4 -> x = 64
        val (cbrtNode, _) = createTemplateNode(MathTemplate.CUBE_ROOT)
        val eq2 = EquationEntry(
            root = MathSlot(
                nodes = listOf(
                    (cbrtNode as RootNode).copy(radicand = slot("x")),
                    SymbolNode(value = "="),
                    SymbolNode(value = "4")
                )
            )
        )
        val res2 = AlgebraSolver.solve(listOf(eq2)) as SolveResult.Success
        assertEquals("64", res2.answers.first().primaryValue)
    }

    @Test
    fun solvesQuadraticWithSquareTemplate() {
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
    fun solvesFractionAndSqrtEquation() {
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
