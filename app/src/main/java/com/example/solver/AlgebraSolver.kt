package com.example.solver

import com.example.model.EquationEntry
import com.example.model.extractVariables
import com.example.model.findFirstEmptySlotId
import com.example.model.isBlank
import com.example.model.toReadableString
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

data class AnswerItem(
    val label: String,
    val primaryValue: String,
    val secondaryValue: String? = null
)

data class SolutionStep(
    val stepNumber: Int,
    val title: String,
    val mathDetail: String
)

sealed interface SolveResult {
    data class Success(
        val summaryTitle: String,
        val answers: List<AnswerItem>,
        val steps: List<SolutionStep>
    ) : SolveResult

    data class NeedsMoreEquations(
        val detectedVars: List<String>,
        val currentCount: Int,
        val requiredCount: Int
    ) : SolveResult

    data class IncompletePlaceholder(
        val message: String,
        val equationIndex: Int,
        val firstEmptySlotId: String
    ) : SolveResult

    data class Error(val message: String) : SolveResult
}

private data class NumVal(val d: Double, val r: Rational? = null) {
    operator fun plus(o: NumVal): NumVal =
        NumVal(d + o.d, if (r != null && o.r != null) r + o.r else null)

    operator fun minus(o: NumVal): NumVal =
        NumVal(d - o.d, if (r != null && o.r != null) r - o.r else null)

    operator fun times(o: NumVal): NumVal =
        NumVal(d * o.d, if (r != null && o.r != null) r * o.r else null)

    operator fun div(o: NumVal): NumVal? {
        if (abs(o.d) < 1e-15) return null
        val rat = if (r != null && o.r != null && !o.r.isZero()) r / o.r else null
        return NumVal(d / o.d, rat)
    }

    operator fun unaryMinus(): NumVal = NumVal(-d, r?.unaryMinus())

    fun isZero(): Boolean = if (r != null) r.isZero() else abs(d) < 1e-10
    fun isOne(): Boolean = if (r != null) r.isOne() else abs(d - 1.0) < 1e-10

    fun formatExactAndApprox(): Pair<String, String?> {
        if (r != null) {
            val exact = r.format()
            return if (r.isInteger()) {
                exact to null
            } else {
                exact to "≈ ${formatNumber(d)}"
            }
        }
        return formatNumber(d) to null
    }

    fun formatCompact(): String = r?.format() ?: formatNumber(d)

    companion object {
        val ZERO = NumVal(0.0, Rational.ZERO)
        val ONE = NumVal(1.0, Rational.ONE)
    }
}

private data class Poly2(val c2: NumVal, val c1: NumVal, val c0: NumVal) {
    operator fun plus(o: Poly2) = Poly2(c2 + o.c2, c1 + o.c1, c0 + o.c0)
    operator fun minus(o: Poly2) = Poly2(c2 - o.c2, c1 - o.c1, c0 - o.c0)
    operator fun unaryMinus() = Poly2(-c2, -c1, -c0)

    fun mul(o: Poly2): Poly2? {
        // Degree check: result must have degree <= 2
        if (!c2.isZero() && (!o.c2.isZero() || !o.c1.isZero())) return null
        if (!c1.isZero() && !o.c2.isZero()) return null
        val n2 = c2 * o.c0 + c1 * o.c1 + c0 * o.c2
        val n1 = c1 * o.c0 + c0 * o.c1
        val n0 = c0 * o.c0
        return Poly2(n2, n1, n0)
    }

    fun isConst(): Boolean = c2.isZero() && c1.isZero()
}

private data class LinearExpr(val coeffs: List<NumVal>, val constant: NumVal) {
    operator fun plus(o: LinearExpr) = LinearExpr(
        coeffs.indices.map { coeffs[it] + o.coeffs[it] },
        constant + o.constant
    )

    operator fun minus(o: LinearExpr) = LinearExpr(
        coeffs.indices.map { coeffs[it] - o.coeffs[it] },
        constant - o.constant
    )

    operator fun unaryMinus() = LinearExpr(coeffs.map { -it }, -constant)

    fun scale(k: NumVal) = LinearExpr(coeffs.map { it * k }, constant * k)

    fun isConstant(): Boolean = coeffs.all { it.isZero() }
}

object AlgebraSolver {

    fun solve(equations: List<EquationEntry>): SolveResult {
        val nonBlankEquations = equations.filter { !it.root.isBlank() }
        if (nonBlankEquations.isEmpty()) {
            return SolveResult.Error("Enter an expression or equation using the keypad.")
        }

        // 1. Check for empty [ ] placeholder boxes in any non-blank equation
        for (idx in equations.indices) {
            val eq = equations[idx]
            if (!eq.root.isBlank()) {
                val emptyId = eq.root.findFirstEmptySlotId()
                if (emptyId != null) {
                    return SolveResult.IncompletePlaceholder(
                        message = "Tap the highlighted [ ] box in Equation ${idx + 1} to fill in a value.",
                        equationIndex = idx,
                        firstEmptySlotId = emptyId
                    )
                }
            }
        }

        // 2. Collect all distinct variables across active equations
        val allVars = linkedSetOf<String>()
        for (eq in nonBlankEquations) {
            allVars.addAll(eq.root.extractVariables())
        }
        val orderedVars = listOf("x", "y", "z").filter { it in allVars }

        // 3. Enforce multi-variable equation count rule
        if (orderedVars.size >= 2 && nonBlankEquations.size < orderedVars.size) {
            return SolveResult.NeedsMoreEquations(
                detectedVars = orderedVars,
                currentCount = nonBlankEquations.size,
                requiredCount = orderedVars.size
            )
        }

        // 4. Compile ASTs
        val compiled = try {
            nonBlankEquations.map { MathAstCompiler.compileEquation(it.root) }
        } catch (e: CompileException) {
            return SolveResult.Error(e.message ?: "Invalid mathematical expression.")
        }

        // 5. Dispatch based on variable count and equation count
        return when {
            orderedVars.isEmpty() -> solveNumericOrVerify(compiled)
            orderedVars.size == 1 && compiled.size == 1 -> {
                val varName = orderedVars[0]
                when (val item = compiled[0]) {
                    is CompiledEquation.Expression -> simplifySingleVarExpression(item, varName)
                    is CompiledEquation.Equation -> solveSingleVarEquation(item, varName)
                }
            }
            orderedVars.size == 1 && compiled.size > 1 -> {
                SolveResult.Error("Multiple equations detected for a single variable '${orderedVars[0]}'. Keep only 1 equation or introduce another variable.")
            }
            else -> solveMultiVarSystem(compiled, nonBlankEquations, orderedVars)
        }
    }

    private fun evalConstant(expr: Expr): NumVal? {
        return when (expr) {
            is Expr.Const -> NumVal(expr.value, expr.rational)
            is Expr.Var -> null
            is Expr.Neg -> evalConstant(expr.inner)?.let { -it }
            is Expr.Add -> {
                val l = evalConstant(expr.left) ?: return null
                val r = evalConstant(expr.right) ?: return null
                l + r
            }
            is Expr.Sub -> {
                val l = evalConstant(expr.left) ?: return null
                val r = evalConstant(expr.right) ?: return null
                l - r
            }
            is Expr.Mul -> {
                val l = evalConstant(expr.left) ?: return null
                val r = evalConstant(expr.right) ?: return null
                l * r
            }
            is Expr.Div -> {
                val l = evalConstant(expr.left) ?: return null
                val r = evalConstant(expr.right) ?: return null
                l / r
            }
            is Expr.Pow -> {
                val b = evalConstant(expr.base) ?: return null
                val e = evalConstant(expr.exp) ?: return null
                val d = Expr.Pow(Expr.Const(b.d), Expr.Const(e.d)).evaluateDouble()
                if (d.isNaN() || d.isInfinite()) return null
                val rat = if (b.r != null && e.r != null && e.r.isInteger()) {
                    val expInt = if (e.r.num.bitLength() <= 31) e.r.num.toInt() else null
                    if (expInt != null && expInt in -12..12) {
                        rationalPow(b.r, expInt)
                    } else null
                } else null
                NumVal(d, rat)
            }
            is Expr.Log -> {
                val b = evalConstant(expr.base) ?: return null
                val a = evalConstant(expr.arg) ?: return null
                if (b.d <= 0.0 || abs(b.d - 1.0) < 1e-12 || a.d <= 0.0) return null
                val d = ln(a.d) / ln(b.d)
                if (d.isNaN() || d.isInfinite()) return null
                val exactInt = d.roundToLong()
                val rat = if (b.r != null && a.r != null && abs(d - exactInt.toDouble()) < 1e-10 && exactInt in -20L..20L) {
                    val check = rationalPow(b.r, exactInt.toInt())
                    if (check == a.r) Rational.of(exactInt) else null
                } else null
                NumVal(d, rat)
            }
            is Expr.Root -> {
                val nVal = expr.degree?.let { evalConstant(it) } ?: NumVal(2.0, Rational.of(2))
                val rVal = evalConstant(expr.radicand) ?: return null
                val d = Expr.Root(Expr.Const(nVal.d), Expr.Const(rVal.d)).evaluateDouble()
                if (d.isNaN() || d.isInfinite()) return null
                val rat = if (nVal.r != null && nVal.r.isInteger() && rVal.r != null) {
                    val nInt = if (nVal.r.num.bitLength() <= 31) nVal.r.num.toInt() else null
                    if (nInt != null && nInt in 1..12) {
                        exactRationalRoot(rVal.r, nInt)
                    } else null
                } else null
                NumVal(d, rat)
            }
        }
    }

    private fun rationalPow(base: Rational, exp: Int): Rational? {
        if (exp == 0) return Rational.ONE
        if (exp < 0) {
            if (base.isZero()) return null
            val pos = rationalPow(base, -exp) ?: return null
            return Rational.ONE / pos
        }
        return try {
            Rational.create(base.num.pow(exp), base.den.pow(exp))
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun exactRationalRoot(rad: Rational, n: Int): Rational? {
        if (n == 1) return rad
        if (rad.isZero()) return Rational.ZERO
        if (rad.num < BigInteger.ZERO && n % 2 == 0) return null
        val sign = if (rad.num < BigInteger.ZERO) -BigInteger.ONE else BigInteger.ONE
        val absNum = rad.num.abs()
        val rn = integerNthRoot(absNum, n) ?: return null
        val rd = integerNthRoot(rad.den, n) ?: return null
        return Rational.create(rn * sign, rd)
    }

    private fun integerNthRoot(value: BigInteger, n: Int): BigInteger? {
        if (value == BigInteger.ZERO) return BigInteger.ZERO
        if (value == BigInteger.ONE) return BigInteger.ONE
        val approx = value.toDouble().pow(1.0 / n.toDouble()).roundToLong()
        for (delta in -2L..2L) {
            val cand = approx + delta
            if (cand >= 0L) {
                val b = BigInteger.valueOf(cand)
                if (b.pow(n) == value) return b
            }
        }
        return null
    }

    private fun solveNumericOrVerify(compiled: List<CompiledEquation>): SolveResult {
        if (compiled.size > 1) {
            return SolveResult.Error("Add variables (x, y, z) to solve a system of multiple equations.")
        }
        return when (val item = compiled[0]) {
            is CompiledEquation.Expression -> {
                val value = evalConstant(item.expr)
                    ?: return SolveResult.Error("Expression is undefined (check division by zero, negative even roots, or log domain).")
                val (primary, secondary) = value.formatExactAndApprox()
                val steps = buildNumericSteps(item.expr, item.text, primary)
                SolveResult.Success(
                    summaryTitle = "Evaluated Expression",
                    answers = listOf(AnswerItem(label = "Result", primaryValue = primary, secondaryValue = secondary)),
                    steps = steps
                )
            }
            is CompiledEquation.Equation -> {
                val lhsVal = evalConstant(item.lhs)
                    ?: return SolveResult.Error("Left side is undefined.")
                val rhsVal = evalConstant(item.rhs)
                    ?: return SolveResult.Error("Right side is undefined.")
                val isTrue = abs(lhsVal.d - rhsVal.d) < 1e-8
                SolveResult.Success(
                    summaryTitle = if (isTrue) "Equation Identity" else "Equation Verification",
                    answers = listOf(
                        AnswerItem(
                            label = "Status",
                            primaryValue = if (isTrue) "True (${lhsVal.formatCompact()} = ${rhsVal.formatCompact()})"
                            else "False (${lhsVal.formatCompact()} ≠ ${rhsVal.formatCompact()})"
                        )
                    ),
                    steps = listOf(
                        SolutionStep(1, "Evaluate left side", "${item.lhsText} = ${lhsVal.formatCompact()}"),
                        SolutionStep(2, "Evaluate right side", "${item.rhsText} = ${rhsVal.formatCompact()}"),
                        SolutionStep(3, "Compare both sides", if (isTrue) "Both sides are equal." else "Both sides are not equal.")
                    )
                )
            }
        }
    }

    private fun buildNumericSteps(expr: Expr, originalText: String, finalText: String): List<SolutionStep> {
        val steps = mutableListOf<SolutionStep>()
        steps.add(SolutionStep(1, "Original expression", originalText))
        val subNotes = mutableListOf<String>()
        fun collectSubEvaluations(e: Expr) {
            when (e) {
                is Expr.Pow -> evalConstant(e)?.let { subNotes.add("${e.toMathText()} = ${it.formatCompact()}") }
                is Expr.Log -> evalConstant(e)?.let { subNotes.add("${e.toMathText()} = ${it.formatCompact()}") }
                is Expr.Root -> evalConstant(e)?.let { subNotes.add("${e.toMathText()} = ${it.formatCompact()}") }
                is Expr.Div -> if (e.left is Expr.Const && e.right is Expr.Const) {
                    evalConstant(e)?.let { subNotes.add("${e.toMathText()} = ${it.formatCompact()}") }
                } else {
                    collectSubEvaluations(e.left); collectSubEvaluations(e.right)
                }
                is Expr.Add -> { collectSubEvaluations(e.left); collectSubEvaluations(e.right) }
                is Expr.Sub -> { collectSubEvaluations(e.left); collectSubEvaluations(e.right) }
                is Expr.Mul -> { collectSubEvaluations(e.left); collectSubEvaluations(e.right) }
                is Expr.Neg -> collectSubEvaluations(e.inner)
                else -> Unit
            }
        }
        collectSubEvaluations(expr)
        if (subNotes.isNotEmpty()) {
            steps.add(SolutionStep(2, "Evaluate powers, roots, logs & fractions", subNotes.distinct().joinToString("\n")))
        }
        steps.add(SolutionStep(steps.size + 1, "Final simplified value", finalText))
        return steps
    }

    private fun simplifySingleVarExpression(item: CompiledEquation.Expression, varName: String): SolveResult {
        val poly = extractPoly2(item.expr, varName)
        if (poly != null) {
            val formatted = formatPoly2(poly, varName)
            return SolveResult.Success(
                summaryTitle = "Simplified Expression",
                answers = listOf(AnswerItem(label = "Simplified", primaryValue = formatted)),
                steps = listOf(
                    SolutionStep(1, "Original expression", item.text),
                    SolutionStep(2, "Combine like terms", formatted)
                )
            )
        }
        return SolveResult.Error("Add '= ...' to solve for $varName, or simplify polynomial terms.")
    }

    private fun solveSingleVarEquation(eq: CompiledEquation.Equation, varName: String): SolveResult {
        val steps = mutableListOf<SolutionStep>()
        steps.add(SolutionStep(1, "Original equation", "${eq.lhsText} = ${eq.rhsText}"))

        // Try symbolic polynomial (Linear or Quadratic) first
        val lhsPoly = extractPoly2(eq.lhs, varName)
        val rhsPoly = extractPoly2(eq.rhs, varName)
        if (lhsPoly != null && rhsPoly != null) {
            return solvePolynomialEquation(lhsPoly - rhsPoly, varName, steps)
        }

        // Try structural unwrapping when variable appears once in either LHS or RHS and the other side is constant
        val lhsCount = eq.lhs.countVarOccurrences(varName)
        val rhsCount = eq.rhs.countVarOccurrences(varName)
        if (lhsCount == 1 && rhsCount == 0) {
            evalConstant(eq.rhs)?.let { rhsConst ->
                tryStructuralUnwrap(eq.lhs, rhsConst, varName, steps)?.let { return it }
            }
        } else if (rhsCount == 1 && lhsCount == 0) {
            evalConstant(eq.lhs)?.let { lhsConst ->
                tryStructuralUnwrap(eq.rhs, lhsConst, varName, steps)?.let { return it }
            }
        }

        // Fallback: General numerical solver for nonlinear / multi-occurrence equations
        return solveNumericalSingleVar(eq.lhs, eq.rhs, varName, steps)
    }

    private fun solvePolynomialEquation(
        diff: Poly2,
        varName: String,
        steps: MutableList<SolutionStep>
    ): SolveResult {
        val a = diff.c2
        val b = diff.c1
        val c = diff.c0

        // Standard form step
        val stdForm = "${formatPoly2(diff, varName)} = 0"
        steps.add(SolutionStep(steps.size + 1, "Rewrite in standard form", stdForm))

        if (a.isZero()) {
            // Linear equation: bx + c = 0
            if (b.isZero()) {
                return if (c.isZero()) {
                    SolveResult.Success(
                        summaryTitle = "Identity",
                        answers = listOf(AnswerItem(varName, "All real numbers (ℝ)")),
                        steps = steps + SolutionStep(steps.size + 1, "Conclusion", "0 = 0 is always true for every $varName.")
                    )
                } else {
                    SolveResult.Success(
                        summaryTitle = "No Solution",
                        answers = listOf(AnswerItem(varName, "No solution")),
                        steps = steps + SolutionStep(steps.size + 1, "Conclusion", "${c.formatCompact()} = 0 is a contradiction.")
                    )
                }
            }
            val negC = -c
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Isolate the variable term",
                    "${formatTerm(b, varName, first = true)} = ${negC.formatCompact()}"
                )
            )
            val sol = negC / b ?: return SolveResult.Error("Division by zero.")
            val (primary, secondary) = sol.formatExactAndApprox()
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Divide both sides by ${b.formatCompact()}",
                    "$varName = $primary" + (secondary?.let { " ($it)" } ?: "")
                )
            )
            return SolveResult.Success(
                summaryTitle = "Linear Equation Solved",
                answers = listOf(AnswerItem(varName, primary, secondary)),
                steps = steps
            )
        } else {
            // Quadratic equation: ax^2 + bx + c = 0
            val disc = (b * b) - (NumVal(4.0, Rational.of(4)) * a * c)
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Compute discriminant Δ = b² − 4ac",
                    "Δ = (${b.formatCompact()})² − 4(${a.formatCompact()})(${c.formatCompact()}) = ${disc.formatCompact()}"
                )
            )

            val twoA = NumVal(2.0, Rational.of(2)) * a
            if (abs(disc.d) < 1e-10) {
                val root = (-b) / twoA ?: return SolveResult.Error("Division by zero.")
                val (primary, secondary) = root.formatExactAndApprox()
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "One repeated root (Δ = 0)",
                        "$varName = -(${b.formatCompact()}) / (2 × ${a.formatCompact()}) = $primary"
                    )
                )
                return SolveResult.Success(
                    summaryTitle = "Quadratic Equation Solved",
                    answers = listOf(AnswerItem(varName, primary, secondary)),
                    steps = steps
                )
            } else if (disc.d > 0) {
                val sqrtDiscD = sqrt(disc.d)
                val exactSqrtDiscRat = disc.r?.let { exactRationalRoot(it, 2) }
                val sqrtDisc = NumVal(sqrtDiscD, exactSqrtDiscRat)
                val r1 = ((-b) + sqrtDisc) / twoA
                val r2 = ((-b) - sqrtDisc) / twoA
                if (r1 == null || r2 == null) return SolveResult.Error("Division by zero.")
                val (p1, s1) = r1.formatExactAndApprox()
                val (p2, s2) = r2.formatExactAndApprox()
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Apply quadratic formula x = (-b ± √Δ) / 2a",
                        "$varName₁ = $p1${s1?.let { " ($it)" } ?: ""}\n$varName₂ = $p2${s2?.let { " ($it)" } ?: ""}"
                    )
                )
                return SolveResult.Success(
                    summaryTitle = "Quadratic Equation Solved",
                    answers = listOf(
                        AnswerItem("${varName}₁", p1, s1),
                        AnswerItem("${varName}₂", p2, s2)
                    ),
                    steps = steps
                )
            } else {
                // Complex roots
                val realPart = (-b) / twoA ?: return SolveResult.Error("Division by zero.")
                val imagPart = NumVal(sqrt(-disc.d)) / NumVal(abs(twoA.d)) ?: return SolveResult.Error("Division by zero.")
                val realStr = if (realPart.isZero()) "" else "${realPart.formatCompact()} "
                val imagStr = "${imagPart.formatCompact()}i"
                val ans1 = if (realStr.isEmpty()) imagStr else "$realStr+ $imagStr"
                val ans2 = if (realStr.isEmpty()) "-$imagStr" else "$realStr− $imagStr"
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Negative discriminant (Δ < 0): complex conjugate roots",
                        "$varName₁ = $ans1\n$varName₂ = $ans2"
                    )
                )
                return SolveResult.Success(
                    summaryTitle = "Complex Roots Found",
                    answers = listOf(
                        AnswerItem("${varName}₁", ans1),
                        AnswerItem("${varName}₂", ans2)
                    ),
                    steps = steps
                )
            }
        }
    }

    private fun tryStructuralUnwrap(
        expr: Expr,
        targetVal: NumVal,
        varName: String,
        steps: MutableList<SolutionStep>
    ): SolveResult? {
        var currExpr = expr
        var currTarget = targetVal

        // Keep track if we reach a branching power like (x - 1)^2 = 9
        for (guard in 0 until 20) {
            if (currExpr is Expr.Var && currExpr.name == varName) {
                val (p, s) = currTarget.formatExactAndApprox()
                return SolveResult.Success(
                    summaryTitle = "Equation Solved",
                    answers = listOf(AnswerItem(varName, p, s)),
                    steps = steps
                )
            }
            when (currExpr) {
                is Expr.Neg -> {
                    currExpr = currExpr.inner
                    currTarget = -currTarget
                    steps.add(SolutionStep(steps.size + 1, "Multiply both sides by -1", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                }
                is Expr.Add -> {
                    val leftConst = evalConstant(currExpr.left)
                    val rightConst = evalConstant(currExpr.right)
                    if (rightConst != null) {
                        currExpr = currExpr.left
                        currTarget -= rightConst
                        steps.add(SolutionStep(steps.size + 1, "Subtract ${rightConst.formatCompact()} from both sides", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                    } else if (leftConst != null) {
                        currExpr = currExpr.right
                        currTarget -= leftConst
                        steps.add(SolutionStep(steps.size + 1, "Subtract ${leftConst.formatCompact()} from both sides", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                    } else return null
                }
                is Expr.Sub -> {
                    val leftConst = evalConstant(currExpr.left)
                    val rightConst = evalConstant(currExpr.right)
                    if (rightConst != null) {
                        currExpr = currExpr.left
                        currTarget += rightConst
                        steps.add(SolutionStep(steps.size + 1, "Add ${rightConst.formatCompact()} to both sides", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                    } else if (leftConst != null) {
                        currExpr = currExpr.right
                        currTarget = leftConst - currTarget
                        steps.add(SolutionStep(steps.size + 1, "Isolate subtrahend", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                    } else return null
                }
                is Expr.Mul -> {
                    val leftConst = evalConstant(currExpr.left)
                    val rightConst = evalConstant(currExpr.right)
                    val c = rightConst ?: leftConst ?: return null
                    if (c.isZero()) return null
                    currExpr = if (rightConst != null) currExpr.left else currExpr.right
                    currTarget = (currTarget / c) ?: return null
                    steps.add(SolutionStep(steps.size + 1, "Divide both sides by ${c.formatCompact()}", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                }
                is Expr.Div -> {
                    val rightConst = evalConstant(currExpr.right)
                    val leftConst = evalConstant(currExpr.left)
                    if (rightConst != null) {
                        if (rightConst.isZero()) return null
                        currExpr = currExpr.left
                        currTarget *= rightConst
                        steps.add(SolutionStep(steps.size + 1, "Multiply both sides by ${rightConst.formatCompact()}", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                    } else if (leftConst != null) {
                        if (currTarget.isZero()) {
                            return SolveResult.Success(
                                summaryTitle = "No Solution",
                                answers = listOf(AnswerItem(varName, "No solution")),
                                steps = steps + SolutionStep(steps.size + 1, "Analyze fraction", "A fraction with non-zero numerator cannot equal 0.")
                            )
                        }
                        currExpr = currExpr.right
                        currTarget = (leftConst / currTarget) ?: return null
                        steps.add(SolutionStep(steps.size + 1, "Cross-multiply to solve for denominator", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                    } else return null
                }
                is Expr.Root -> {
                    val deg = currExpr.degree?.let { evalConstant(it) } ?: NumVal(2.0, Rational.of(2))
                    val degInt = deg.d.roundToLong()
                    val isInt = abs(deg.d - degInt) < 1e-10 && degInt >= 1L
                    if (isInt && degInt % 2L == 0L && currTarget.d < 0.0) {
                        return SolveResult.Success(
                            summaryTitle = "No Real Solution",
                            answers = listOf(AnswerItem(varName, "No real solution")),
                            steps = steps + SolutionStep(steps.size + 1, "Check radical domain", "An even root cannot equal a negative number (${currTarget.formatCompact()}).")
                        )
                    }
                    val raised = evalConstant(Expr.Pow(Expr.Const(currTarget.d, currTarget.r), Expr.Const(deg.d, deg.r))) ?: return null
                    currExpr = currExpr.radicand
                    currTarget = raised
                    steps.add(SolutionStep(steps.size + 1, "Raise both sides to the power of ${deg.formatCompact()}", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                }
                is Expr.Log -> {
                    val baseConst = evalConstant(currExpr.base) ?: return null
                    if (baseConst.d <= 0.0 || abs(baseConst.d - 1.0) < 1e-10) {
                        return SolveResult.Error("Logarithm base must be positive and not equal to 1.")
                    }
                    val prevTargetText = currTarget.formatCompact()
                    val raised = evalConstant(Expr.Pow(Expr.Const(baseConst.d, baseConst.r), Expr.Const(currTarget.d, currTarget.r))) ?: return null
                    currExpr = currExpr.arg
                    currTarget = raised
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            "Rewrite logarithm in exponential form",
                            "${currExpr.toMathText()} = ${baseConst.formatCompact()}^($prevTargetText) = ${raised.formatCompact()}"
                        )
                    )
                }
                is Expr.Pow -> {
                    val baseConst = evalConstant(currExpr.base)
                    val expConst = evalConstant(currExpr.exp)
                    if (baseConst != null && expConst == null) {
                        if (baseConst.d <= 0.0 || abs(baseConst.d - 1.0) < 1e-10) {
                            return SolveResult.Error("Exponential base must be positive and not 1.")
                        }
                        if (currTarget.d <= 0.0) {
                            return SolveResult.Success(
                                summaryTitle = "No Real Solution",
                                answers = listOf(AnswerItem(varName, "No real solution")),
                                steps = steps + SolutionStep(steps.size + 1, "Check exponential range", "${baseConst.formatCompact()}^(...) is always positive, so it cannot equal ${currTarget.formatCompact()}.")
                            )
                        }
                        val prevTargetText = currTarget.formatCompact()
                        val logged = evalConstant(Expr.Log(Expr.Const(baseConst.d, baseConst.r), Expr.Const(currTarget.d, currTarget.r))) ?: return null
                        currExpr = currExpr.exp
                        currTarget = logged
                        steps.add(
                            SolutionStep(
                                steps.size + 1,
                                "Take log base ${baseConst.formatCompact()} of both sides",
                                "${currExpr.toMathText()} = log_${baseConst.formatCompact()}($prevTargetText) = ${logged.formatCompact()}"
                            )
                        )
                    } else if (expConst != null && baseConst == null) {
                        val expInt = expConst.d.roundToLong()
                        val isInt = abs(expConst.d - expInt) < 1e-10
                        if (isInt && expInt > 0L) {
                            if (expInt % 2L == 0L) {
                                if (currTarget.d < 0.0) {
                                    return SolveResult.Success(
                                        summaryTitle = "No Real Solution",
                                        answers = listOf(AnswerItem(varName, "No real solution")),
                                        steps = steps + SolutionStep(steps.size + 1, "Check even power", "Even power cannot equal negative number ${currTarget.formatCompact()}.")
                                    )
                                }
                                val rootPos = evalConstant(Expr.Root(Expr.Const(expConst.d, expConst.r), Expr.Const(currTarget.d, currTarget.r))) ?: return null
                                if (rootPos.isZero()) {
                                    currExpr = currExpr.base
                                    currTarget = NumVal.ZERO
                                    steps.add(SolutionStep(steps.size + 1, "Take ${expInt}th root", "${currExpr.toMathText()} = 0"))
                                } else {
                                    val branchSteps1 = steps.toMutableList()
                                    branchSteps1.add(SolutionStep(branchSteps1.size + 1, "Take ±${expInt}th root (Branch 1)", "${currExpr.base.toMathText()} = ${rootPos.formatCompact()}"))
                                    val res1 = tryStructuralUnwrap(currExpr.base, rootPos, varName, branchSteps1)
                                    val branchSteps2 = mutableListOf<SolutionStep>()
                                    branchSteps2.add(SolutionStep(1, "Take ±${expInt}th root (Branch 2)", "${currExpr.base.toMathText()} = ${(-rootPos).formatCompact()}"))
                                    val res2 = tryStructuralUnwrap(currExpr.base, -rootPos, varName, branchSteps2)
                                    if (res1 is SolveResult.Success && res2 is SolveResult.Success) {
                                        val answers = listOf(
                                            res1.answers.first().copy(label = "${varName}₁"),
                                            res2.answers.first().copy(label = "${varName}₂")
                                        )
                                        val combinedSteps = branchSteps1 + branchSteps2.mapIndexed { i, st -> st.copy(stepNumber = branchSteps1.size + i + 1) }
                                        return SolveResult.Success("Equation Solved", answers, combinedSteps)
                                    }
                                    return null
                                }
                            } else {
                                val rootVal = evalConstant(Expr.Root(Expr.Const(expConst.d, expConst.r), Expr.Const(currTarget.d, currTarget.r))) ?: return null
                                currExpr = currExpr.base
                                currTarget = rootVal
                                steps.add(SolutionStep(steps.size + 1, "Take ${expInt}th root of both sides", "${currExpr.toMathText()} = ${currTarget.formatCompact()}"))
                            }
                        } else return null
                    } else return null
                }
                else -> return null
            }
        }
        return null
    }

    private fun extractPoly2(expr: Expr, varName: String): Poly2? {
        return when (expr) {
            is Expr.Const -> Poly2(NumVal.ZERO, NumVal.ZERO, NumVal(expr.value, expr.rational))
            is Expr.Var -> if (expr.name == varName) Poly2(NumVal.ZERO, NumVal.ONE, NumVal.ZERO) else null
            is Expr.Neg -> extractPoly2(expr.inner, varName)?.unaryMinus()
            is Expr.Add -> {
                val l = extractPoly2(expr.left, varName) ?: return null
                val r = extractPoly2(expr.right, varName) ?: return null
                l + r
            }
            is Expr.Sub -> {
                val l = extractPoly2(expr.left, varName) ?: return null
                val r = extractPoly2(expr.right, varName) ?: return null
                l - r
            }
            is Expr.Mul -> {
                val l = extractPoly2(expr.left, varName) ?: return null
                val r = extractPoly2(expr.right, varName) ?: return null
                l.mul(r)
            }
            is Expr.Div -> {
                val rConst = evalConstant(expr.right) ?: return null
                if (rConst.isZero()) return null
                val l = extractPoly2(expr.left, varName) ?: return null
                val c2 = l.c2 / rConst ?: return null
                val c1 = l.c1 / rConst ?: return null
                val c0 = l.c0 / rConst ?: return null
                Poly2(c2, c1, c0)
            }
            is Expr.Pow -> {
                val expConst = evalConstant(expr.exp)
                if (expConst != null) {
                    val basePoly = extractPoly2(expr.base, varName)
                    if (basePoly != null && abs(expConst.d - expConst.d.roundToLong()) < 1e-10) {
                        val k = expConst.d.roundToLong()
                        when (k) {
                            0L -> return Poly2(NumVal.ZERO, NumVal.ZERO, NumVal.ONE)
                            1L -> return basePoly
                            2L -> return basePoly.mul(basePoly)
                        }
                    }
                }
                evalConstant(expr)?.let { Poly2(NumVal.ZERO, NumVal.ZERO, it) }
            }
            is Expr.Log, is Expr.Root -> evalConstant(expr)?.let { Poly2(NumVal.ZERO, NumVal.ZERO, it) }
        }
    }

    private fun solveNumericalSingleVar(
        lhs: Expr,
        rhs: Expr,
        varName: String,
        steps: MutableList<SolutionStep>
    ): SolveResult {
        fun f(v: Double): Double {
            val env = mapOf(varName to v)
            val l = lhs.evaluateDouble(env)
            val r = rhs.evaluateDouble(env)
            if (l.isNaN() || l.isInfinite() || r.isNaN() || r.isInfinite()) return Double.NaN
            return l - r
        }

        val samples = mutableListOf<Double>()
        for (i in -200..200) samples.add(i * 0.5)
        // Dense positive samples for log/root domains
        for (i in 1..200) samples.add(i * 0.05)
        for (i in 1..10) samples.add(10.0.pow(i.toDouble() * 0.3) * 0.001)
        val sortedSamples = samples.distinct().sorted()

        val foundRoots = mutableListOf<Double>()

        fun addRootIfValid(candidate: Double) {
            if (candidate.isNaN() || candidate.isInfinite()) return
            val err = abs(f(candidate))
            if (err.isNaN() || err > 1e-5) return
            if (foundRoots.none { abs(it - candidate) < 1e-4 }) {
                foundRoots.add(candidate)
            }
        }

        for (i in 0 until sortedSamples.size - 1) {
            val a = sortedSamples[i]
            val b = sortedSamples[i + 1]
            val fa = f(a)
            val fb = f(b)
            if (!fa.isNaN() && abs(fa) < 1e-9) {
                addRootIfValid(a)
                continue
            }
            if (!fa.isNaN() && !fb.isNaN() && fa * fb < 0.0) {
                var lo = a
                var hi = b
                var flo = fa
                for (iter in 0 until 80) {
                    val mid = 0.5 * (lo + hi)
                    val fmid = f(mid)
                    if (fmid.isNaN()) break
                    if (abs(fmid) < 1e-12 || abs(hi - lo) < 1e-12) {
                        addRootIfValid(mid)
                        break
                    }
                    if (flo * fmid <= 0.0) {
                        hi = mid
                    } else {
                        lo = mid
                        flo = fmid
                    }
                }
                addRootIfValid(0.5 * (lo + hi))
            }
        }

        // Newton refinement from select seeds for tangent roots
        val seeds = listOf(-10.0, -2.0, -0.5, 0.25, 0.5, 1.0, 2.0, 4.0, 10.0, 25.0, 100.0)
        for (seed in seeds) {
            var x = seed
            for (iter in 0 until 50) {
                val fx = f(x)
                if (fx.isNaN()) break
                if (abs(fx) < 1e-11) {
                    addRootIfValid(x)
                    break
                }
                val h = 1e-6 * maxOf(1.0, abs(x))
                val fPlus = f(x + h)
                val fMinus = f(x - h)
                val df = if (!fPlus.isNaN() && !fMinus.isNaN()) (fPlus - fMinus) / (2 * h) else Double.NaN
                if (df.isNaN() || abs(df) < 1e-12) break
                x -= fx / df
            }
            addRootIfValid(x)
        }

        if (foundRoots.isEmpty()) {
            return SolveResult.Success(
                summaryTitle = "No Real Solution Found",
                answers = listOf(AnswerItem(varName, "No real solution in search range")),
                steps = steps + SolutionStep(steps.size + 1, "Domain & root check", "No real value of $varName satisfies LHS = RHS.")
            )
        }

        val sorted = foundRoots.sorted()
        val answers = sorted.mapIndexed { index, r ->
            val label = if (sorted.size == 1) varName else "${varName}${subscriptNumber(index + 1)}"
            AnswerItem(label, formatNumber(r))
        }
        steps.add(
            SolutionStep(
                steps.size + 1,
                "Solve numerically & verify LHS = RHS",
                answers.joinToString("\n") { "${it.label} = ${it.primaryValue}" }
            )
        )
        return SolveResult.Success("Equation Solved", answers, steps)
    }

    private fun solveMultiVarSystem(
        compiled: List<CompiledEquation>,
        entries: List<EquationEntry>,
        vars: List<String>
    ): SolveResult {
        val steps = mutableListOf<SolutionStep>()
        val eqListText = entries.mapIndexed { idx, e -> "Eq ${idx + 1}: ${e.root.toReadableString()}" }
            .joinToString("\n")
        steps.add(SolutionStep(1, "System of ${vars.size} equations (${vars.joinToString(", ")})", eqListText))

        // Ensure all entries are equations with '='
        val eqPairs = mutableListOf<Pair<Expr, Expr>>()
        for ((idx, item) in compiled.withIndex()) {
            when (item) {
                is CompiledEquation.Equation -> eqPairs.add(item.lhs to item.rhs)
                is CompiledEquation.Expression -> return SolveResult.Error("Equation ${idx + 1} is missing an '=' sign.")
            }
        }

        // Try exact linear system extraction
        val linearRows = eqPairs.map { (lhs, rhs) ->
            val l = extractLinear(lhs, vars)
            val r = extractLinear(rhs, vars)
            if (l != null && r != null) l - r else null
        }

        if (linearRows.all { it != null }) {
            val rows = linearRows.filterNotNull()
            return solveLinearSystem(rows, vars, steps)
        }

        // Fallback: Nonlinear system via multi-start Newton-Raphson
        return solveNonlinearSystem(eqPairs, vars, steps)
    }

    private fun extractLinear(expr: Expr, vars: List<String>): LinearExpr? {
        val zeroCoeffs = List(vars.size) { NumVal.ZERO }
        return when (expr) {
            is Expr.Const -> LinearExpr(zeroCoeffs, NumVal(expr.value, expr.rational))
            is Expr.Var -> {
                val idx = vars.indexOf(expr.name)
                if (idx == -1) return null
                val c = MutableList(vars.size) { NumVal.ZERO }
                c[idx] = NumVal.ONE
                LinearExpr(c, NumVal.ZERO)
            }
            is Expr.Neg -> extractLinear(expr.inner, vars)?.unaryMinus()
            is Expr.Add -> {
                val l = extractLinear(expr.left, vars) ?: return null
                val r = extractLinear(expr.right, vars) ?: return null
                l + r
            }
            is Expr.Sub -> {
                val l = extractLinear(expr.left, vars) ?: return null
                val r = extractLinear(expr.right, vars) ?: return null
                l - r
            }
            is Expr.Mul -> {
                val l = extractLinear(expr.left, vars) ?: return null
                val r = extractLinear(expr.right, vars) ?: return null
                when {
                    l.isConstant() -> r.scale(l.constant)
                    r.isConstant() -> l.scale(r.constant)
                    else -> null
                }
            }
            is Expr.Div -> {
                val rConst = evalConstant(expr.right) ?: return null
                if (rConst.isZero()) return null
                val l = extractLinear(expr.left, vars) ?: return null
                val inv = NumVal(1.0 / rConst.d, rConst.r?.let { Rational.ONE / it })
                l.scale(inv)
            }
            is Expr.Pow, is Expr.Log, is Expr.Root -> {
                evalConstant(expr)?.let { LinearExpr(zeroCoeffs, it) }
            }
        }
    }

    private fun solveLinearSystem(
        rows: List<LinearExpr>,
        vars: List<String>,
        steps: MutableList<SolutionStep>
    ): SolveResult {
        val n = vars.size
        val m = rows.size
        // Build augmented matrix [A | b] where each row represents a1*x + a2*y + ... = b
        val matrix = Array(m) { r ->
            Array(n + 1) { c ->
                if (c < n) rows[r].coeffs[c] else -rows[r].constant
            }
        }

        val stdFormText = rows.mapIndexed { idx, row ->
            val lhsStr = buildString {
                var first = true
                for (j in 0 until n) {
                    val coef = row.coeffs[j]
                    if (!coef.isZero() || (j == n - 1 && first)) {
                        append(formatTerm(coef, vars[j], first))
                        first = false
                    }
                }
            }.ifEmpty { "0" }
            val rhsVal = -row.constant
            "(${idx + 1}) $lhsStr = ${rhsVal.formatCompact()}"
        }.joinToString("\n")
        steps.add(SolutionStep(steps.size + 1, "Standard linear form", stdFormText))

        var pivotRow = 0
        val pivotCols = IntArray(m) { -1 }

        for (col in 0 until n) {
            if (pivotRow >= m) break
            var bestRow = pivotRow
            for (r in pivotRow + 1 until m) {
                if (abs(matrix[r][col].d) > abs(matrix[bestRow][col].d)) {
                    bestRow = r
                }
            }
            if (matrix[bestRow][col].isZero()) continue

            if (bestRow != pivotRow) {
                val tmp = matrix[pivotRow]
                matrix[pivotRow] = matrix[bestRow]
                matrix[bestRow] = tmp
            }

            val pivotVal = matrix[pivotRow][col]
            for (c in col..n) {
                matrix[pivotRow][c] = (matrix[pivotRow][c] / pivotVal)
                    ?: return SolveResult.Error("Numerical division error during elimination.")
            }

            for (r in 0 until m) {
                if (r != pivotRow && !matrix[r][col].isZero()) {
                    val factor = matrix[r][col]
                    for (c in col..n) {
                        matrix[r][c] = matrix[r][c] - (factor * matrix[pivotRow][c])
                    }
                }
            }
            pivotCols[pivotRow] = col
            pivotRow++
        }

        // Check inconsistency (0 = nonzero)
        for (r in 0 until m) {
            val allZeroCoeffs = (0 until n).all { matrix[r][it].isZero() }
            if (allZeroCoeffs && !matrix[r][n].isZero()) {
                return SolveResult.Success(
                    summaryTitle = "No Solution",
                    answers = listOf(AnswerItem("System", "Inconsistent (no solution)")),
                    steps = steps + SolutionStep(steps.size + 1, "Row reduction result", "Elimination produced 0 = ${matrix[r][n].formatCompact()}, so the system has no solution.")
                )
            }
        }

        if (pivotRow < n) {
            return SolveResult.Success(
                summaryTitle = "Infinitely Many Solutions",
                answers = listOf(AnswerItem("System", "Dependent (infinitely many solutions)")),
                steps = steps + SolutionStep(steps.size + 1, "Row reduction result", "Equations are linearly dependent (rank $pivotRow < $n variables).")
            )
        }

        val rrefText = (0 until n).joinToString("\n") { r ->
            val (p, s) = matrix[r][n].formatExactAndApprox()
            "${vars[r]} = $p${s?.let { " ($it)" } ?: ""}"
        }
        steps.add(SolutionStep(steps.size + 1, "Gaussian elimination & back-substitution", rrefText))

        val answers = (0 until n).map { r ->
            val (p, s) = matrix[r][n].formatExactAndApprox()
            AnswerItem(vars[r], p, s)
        }

        return SolveResult.Success(
            summaryTitle = "System Solved (${vars.joinToString(", ")})",
            answers = answers,
            steps = steps
        )
    }

    private fun solveNonlinearSystem(
        eqPairs: List<Pair<Expr, Expr>>,
        vars: List<String>,
        steps: MutableList<SolutionStep>
    ): SolveResult {
        val n = vars.size
        if (eqPairs.size < n) {
            return SolveResult.Error("Provide at least $n equations for $n variables.")
        }

        fun evalResidual(vec: DoubleArray): DoubleArray? {
            val env = vars.indices.associate { vars[it] to vec[it] }
            val res = DoubleArray(eqPairs.size)
            for (i in eqPairs.indices) {
                val l = eqPairs[i].first.evaluateDouble(env)
                val r = eqPairs[i].second.evaluateDouble(env)
                if (l.isNaN() || l.isInfinite() || r.isNaN() || r.isInfinite()) return null
                res[i] = l - r
            }
            return res
        }

        val initialGuesses = listOf(
            DoubleArray(n) { 1.0 },
            DoubleArray(n) { -1.0 },
            DoubleArray(n) { 2.0 },
            DoubleArray(n) { if (it % 2 == 0) 3.0 else -2.0 },
            DoubleArray(n) { 0.5 },
            DoubleArray(n) { 5.0 }
        )

        for (guess in initialGuesses) {
            val x = guess.copyOf()
            var converged = false
            for (iter in 0 until 80) {
                val f0 = evalResidual(x) ?: break
                val norm = sqrt(f0.sumOf { it * it })
                if (norm < 1e-9) {
                    converged = true
                    break
                }
                // Jacobian (use first n equations)
                val jac = Array(n) { DoubleArray(n) }
                var jacOk = true
                for (j in 0 until n) {
                    val h = 1e-6 * maxOf(1.0, abs(x[j]))
                    x[j] += h
                    val fp = evalResidual(x)
                    x[j] -= h
                    if (fp == null) {
                        jacOk = false
                        break
                    }
                    for (i in 0 until n) {
                        jac[i][j] = (fp[i] - f0[i]) / h
                    }
                }
                if (!jacOk) break
                val delta = solveSmallLinearDouble(jac, DoubleArray(n) { -f0[it] }) ?: break
                for (j in 0 until n) x[j] += delta[j]
            }
            if (converged) {
                val check = evalResidual(x)
                if (check != null && check.all { abs(it) < 1e-6 }) {
                    val answers = vars.indices.map { i -> AnswerItem(vars[i], formatNumber(x[i])) }
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            "Solve nonlinear system numerically",
                            answers.joinToString("\n") { "${it.label} = ${it.primaryValue}" }
                        )
                    )
                    return SolveResult.Success("Nonlinear System Solved", answers, steps)
                }
            }
        }

        return SolveResult.Error("Could not find a real solution for this nonlinear system.")
    }

    private fun solveSmallLinearDouble(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val aug = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) a[r][c] else b[r] } }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) {
                if (abs(aug[r][col]) > abs(aug[pivot][col])) pivot = r
            }
            if (abs(aug[pivot][col]) < 1e-12) return null
            val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            val p = aug[col][col]
            for (c in col..n) aug[col][c] /= p
            for (r in 0 until n) {
                if (r != col && abs(aug[r][col]) > 1e-15) {
                    val f = aug[r][col]
                    for (c in col..n) aug[r][c] -= f * aug[col][c]
                }
            }
        }
        return DoubleArray(n) { aug[it][n] }
    }

    private fun formatPoly2(poly: Poly2, varName: String): String {
        val parts = mutableListOf<String>()
        if (!poly.c2.isZero()) {
            parts.add(formatTerm(poly.c2, "$varName²", first = true))
        }
        if (!poly.c1.isZero()) {
            parts.add(formatTerm(poly.c1, varName, first = parts.isEmpty()))
        }
        if (!poly.c0.isZero() || parts.isEmpty()) {
            if (parts.isEmpty()) {
                parts.add(poly.c0.formatCompact())
            } else {
                val sign = if (poly.c0.d < 0) " − " else " + "
                val mag = if (poly.c0.d < 0) -poly.c0 else poly.c0
                parts.add("$sign${mag.formatCompact()}")
            }
        }
        return parts.joinToString("")
    }

    private fun formatTerm(coef: NumVal, symbol: String, first: Boolean): String {
        val isNeg = coef.d < 0
        val mag = if (isNeg) -coef else coef
        val coefText = if (mag.isOne()) "" else mag.formatCompact()
        return if (first) {
            if (isNeg) "-$coefText$symbol" else "$coefText$symbol"
        } else {
            if (isNeg) " − $coefText$symbol" else " + $coefText$symbol"
        }
    }

    private fun subscriptNumber(n: Int): String = when (n) {
        1 -> "₁"
        2 -> "₂"
        3 -> "₃"
        4 -> "₄"
        else -> n.toString()
    }
}
