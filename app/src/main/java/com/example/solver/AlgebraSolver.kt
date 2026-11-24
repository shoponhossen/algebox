package com.example.solver

import com.example.model.EquationEntry
import com.example.model.extractVariables
import com.example.model.findFirstEmptySlotId
import com.example.model.isBlank
import com.example.model.toReadableString
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
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

private val DEC_CTX = MathContext.DECIMAL128

private data class NumVal(
    val d: Double,
    val r: Rational? = null,
    val bd: BigDecimal? = null
) {
    operator fun plus(o: NumVal): NumVal {
        val rat = if (r != null && o.r != null) r + o.r else null
        val dec = rat?.toTerminatingDecimal() ?: if (bd != null && o.bd != null) bd.add(o.bd, DEC_CTX) else null
        val dbl = dec?.toDouble() ?: (d + o.d)
        return NumVal(dbl, rat, dec)
    }

    operator fun minus(o: NumVal): NumVal {
        val rat = if (r != null && o.r != null) r - o.r else null
        val dec = rat?.toTerminatingDecimal() ?: if (bd != null && o.bd != null) bd.subtract(o.bd, DEC_CTX) else null
        val dbl = dec?.toDouble() ?: (d - o.d)
        return NumVal(dbl, rat, dec)
    }

    operator fun times(o: NumVal): NumVal {
        val rat = if (r != null && o.r != null) r * o.r else null
        val dec = rat?.toTerminatingDecimal() ?: if (bd != null && o.bd != null) bd.multiply(o.bd, DEC_CTX) else null
        val dbl = dec?.toDouble() ?: (d * o.d)
        return NumVal(dbl, rat, dec)
    }

    operator fun div(o: NumVal): NumVal? {
        if (o.isZero()) return null
        val rat = if (r != null && o.r != null && !o.r.isZero()) r / o.r else null
        val dec = rat?.toTerminatingDecimal() ?: if (bd != null && o.bd != null && o.bd.compareTo(BigDecimal.ZERO) != 0) {
            try {
                bd.divide(o.bd, DEC_CTX)
            } catch (_: ArithmeticException) {
                null
            }
        } else null
        val dbl = dec?.toDouble() ?: (d / o.d)
        return NumVal(dbl, rat, dec)
    }

    operator fun unaryMinus(): NumVal = NumVal(-d, r?.unaryMinus(), bd?.negate())

    fun isZero(): Boolean = when {
        r != null -> r.isZero()
        bd != null -> bd.abs(DEC_CTX) < BigDecimal("1E-12")
        else -> abs(d) < 1e-11
    }

    fun isOne(): Boolean = when {
        r != null -> r.isOne()
        bd != null -> bd.subtract(BigDecimal.ONE, DEC_CTX).abs(DEC_CTX) < BigDecimal("1E-12")
        else -> abs(d - 1.0) < 1e-11
    }

    fun equalsValue(o: NumVal): Boolean = when {
        r != null && o.r != null -> r == o.r
        bd != null && o.bd != null -> bd.subtract(o.bd, DEC_CTX).abs(DEC_CTX) < BigDecimal("1E-10")
        else -> abs(d - o.d) < 1e-9
    }

    fun formatExactAndApprox(preferDecimal: Boolean = false): Pair<String, String?> {
        if (r != null) {
            val termDec = r.formatDecimalClean()
            val fracStr = r.format()
            if (r.isInteger()) {
                return fracStr to null
            }
            if (preferDecimal && termDec != null) {
                return termDec to "fraction: $fracStr"
            }
            return if (termDec != null) {
                fracStr to "= $termDec"
            } else {
                fracStr to "≈ ${formatNumber(d)}"
            }
        }
        if (bd != null) {
            val cleaned = bd.round(MathContext(12)).stripTrailingZeros().toPlainString()
            return cleaned to null
        }
        return formatNumber(d) to null
    }

    fun formatCompact(preferDecimal: Boolean = true): String {
        if (r != null) {
            if (r.isInteger()) return r.format()
            val termDec = r.formatDecimalClean()
            if (preferDecimal && termDec != null) return termDec
            return r.format()
        }
        if (bd != null) {
            return bd.round(MathContext(10)).stripTrailingZeros().toPlainString()
        }
        return formatNumber(d)
    }

    companion object {
        val ZERO = NumVal(0.0, Rational.ZERO, BigDecimal.ZERO)
        val ONE = NumVal(1.0, Rational.ONE, BigDecimal.ONE)

        fun fromExprConst(c: Expr.Const): NumVal {
            val dec = c.rational?.toTerminatingDecimal() ?: c.decimal ?: BigDecimal.valueOf(c.value)
            return NumVal(c.value, c.rational, dec)
        }
    }
}

private data class Poly2(val c2: NumVal, val c1: NumVal, val c0: NumVal) {
    operator fun plus(o: Poly2) = Poly2(c2 + o.c2, c1 + o.c1, c0 + o.c0)
    operator fun minus(o: Poly2) = Poly2(c2 - o.c2, c1 - o.c1, c0 - o.c0)
    operator fun unaryMinus() = Poly2(-c2, -c1, -c0)

    fun mul(o: Poly2): Poly2? {
        if (!c2.isZero() && (!o.c2.isZero() || !o.c1.isZero())) return null
        if (!c1.isZero() && !o.c2.isZero()) return null
        val n2 = c2 * o.c0 + c1 * o.c1 + c0 * o.c2
        val n1 = c1 * o.c0 + c0 * o.c1
        val n0 = c0 * o.c0
        return Poly2(n2, n1, n0)
    }
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

        val allVars = linkedSetOf<String>()
        for (eq in nonBlankEquations) {
            allVars.addAll(eq.root.extractVariables())
        }
        val orderedVars = listOf("x", "y", "z").filter { it in allVars }

        if (orderedVars.size >= 2 && nonBlankEquations.size < orderedVars.size) {
            return SolveResult.NeedsMoreEquations(
                detectedVars = orderedVars,
                currentCount = nonBlankEquations.size,
                requiredCount = orderedVars.size
            )
        }

        val compiled = try {
            nonBlankEquations.map { MathAstCompiler.compileEquation(it.root) }
        } catch (e: CompileException) {
            return SolveResult.Error(e.message ?: "Invalid mathematical expression.")
        }

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
                SolveResult.Error("Multiple equations detected for a single variable '${orderedVars[0]}'. Keep only 1 equation or add another variable.")
            }
            else -> solveMultiVarSystem(compiled, nonBlankEquations, orderedVars)
        }
    }

    private fun evalConstant(expr: Expr): NumVal? {
        return when (expr) {
            is Expr.Const -> NumVal.fromExprConst(expr)
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
                val dec = rat?.toTerminatingDecimal() ?: if (b.bd != null && e.r != null && e.r.isInteger()) {
                    val expInt = if (e.r.num.bitLength() <= 31) e.r.num.toInt() else null
                    if (expInt != null && expInt in 0..12) {
                        try {
                            b.bd.pow(expInt, DEC_CTX)
                        } catch (_: ArithmeticException) {
                            null
                        }
                    } else null
                } else null
                NumVal(dec?.toDouble() ?: d, rat, dec)
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
                val dec = rat?.toTerminatingDecimal() ?: if (abs(d - exactInt.toDouble()) < 1e-12) {
                    BigDecimal.valueOf(exactInt)
                } else null
                NumVal(dec?.toDouble() ?: d, rat, dec)
            }
            is Expr.Root -> {
                val nVal = expr.degree?.let { evalConstant(it) } ?: NumVal(2.0, Rational.of(2), BigDecimal.valueOf(2))
                val rVal = evalConstant(expr.radicand) ?: return null
                val d = Expr.Root(Expr.Const(nVal.d), Expr.Const(rVal.d)).evaluateDouble()
                if (d.isNaN() || d.isInfinite()) return null
                val rat = if (nVal.r != null && nVal.r.isInteger() && rVal.r != null) {
                    val nInt = if (nVal.r.num.bitLength() <= 31) nVal.r.num.toInt() else null
                    if (nInt != null && nInt in 1..12) {
                        exactRationalRoot(rVal.r, nInt)
                    } else null
                } else null
                val dec = rat?.toTerminatingDecimal()
                NumVal(dec?.toDouble() ?: d, rat, dec)
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
                val preferDecimal = item.expr.containsDecimalLiteral()
                val value = evalConstant(item.expr)
                    ?: return SolveResult.Error("Expression is undefined (check division by zero, even root of a negative number, or log domain).")
                val (primary, secondary) = value.formatExactAndApprox(preferDecimal)
                val steps = buildBeginnerNumericSteps(item.expr, item.text, primary, preferDecimal)
                SolveResult.Success(
                    summaryTitle = "Calculated Result",
                    answers = listOf(AnswerItem(label = "Answer", primaryValue = primary, secondaryValue = secondary)),
                    steps = steps
                )
            }
            is CompiledEquation.Equation -> {
                val preferDecimal = item.lhs.containsDecimalLiteral() || item.rhs.containsDecimalLiteral()
                val lhsVal = evalConstant(item.lhs)
                    ?: return SolveResult.Error("Left side is undefined.")
                val rhsVal = evalConstant(item.rhs)
                    ?: return SolveResult.Error("Right side is undefined.")
                val isTrue = lhsVal.equalsValue(rhsVal)
                val lhsStr = lhsVal.formatCompact(preferDecimal)
                val rhsStr = rhsVal.formatCompact(preferDecimal)
                SolveResult.Success(
                    summaryTitle = if (isTrue) "Both Sides Are Equal" else "Sides Are Not Equal",
                    answers = listOf(
                        AnswerItem(
                            label = "Check",
                            primaryValue = if (isTrue) "True ($lhsStr = $rhsStr)" else "False ($lhsStr ≠ $rhsStr)"
                        )
                    ),
                    steps = listOf(
                        SolutionStep(1, "Calculate the left side", "${item.lhsText} = $lhsStr"),
                        SolutionStep(2, "Calculate the right side", "${item.rhsText} = $rhsStr"),
                        SolutionStep(
                            3,
                            "Compare the two values",
                            if (isTrue) "$lhsStr matches $rhsStr, so the statement is True ✓"
                            else "$lhsStr does not equal $rhsStr, so the statement is False ✗"
                        )
                    )
                )
            }
        }
    }

    private fun buildBeginnerNumericSteps(
        expr: Expr,
        originalText: String,
        finalText: String,
        preferDecimal: Boolean
    ): List<SolutionStep> {
        val steps = mutableListOf<SolutionStep>()
        steps.add(SolutionStep(1, "Write down the expression", originalText))

        val specialSubs = mutableListOf<String>()
        val mulDivSubs = mutableListOf<String>()

        fun walkSpecial(e: Expr) {
            when (e) {
                is Expr.Pow, is Expr.Log, is Expr.Root -> {
                    evalConstant(e)?.let {
                        specialSubs.add("${e.toMathText()} = ${it.formatCompact(preferDecimal)}")
                    }
                }
                is Expr.Div -> {
                    if (e.left is Expr.Const && e.right is Expr.Const) {
                        evalConstant(e)?.let {
                            specialSubs.add("${e.toMathText()} = ${it.formatCompact(preferDecimal)}")
                        }
                    } else {
                        walkSpecial(e.left)
                        walkSpecial(e.right)
                    }
                }
                is Expr.Mul -> {
                    walkSpecial(e.left)
                    walkSpecial(e.right)
                    if (e.left !is Expr.Add && e.left !is Expr.Sub && e.right !is Expr.Add && e.right !is Expr.Sub) {
                        evalConstant(e)?.let {
                            val lTxt = evalConstant(e.left)?.formatCompact(preferDecimal) ?: e.left.toMathText()
                            val rTxt = evalConstant(e.right)?.formatCompact(preferDecimal) ?: e.right.toMathText()
                            mulDivSubs.add("$lTxt × $rTxt = ${it.formatCompact(preferDecimal)}")
                        }
                    }
                }
                is Expr.Add -> { walkSpecial(e.left); walkSpecial(e.right) }
                is Expr.Sub -> { walkSpecial(e.left); walkSpecial(e.right) }
                is Expr.Neg -> walkSpecial(e.inner)
                else -> Unit
            }
        }
        walkSpecial(expr)

        if (specialSubs.isNotEmpty()) {
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Step 1 of PEMDAS: Evaluate powers, roots, logs & fractions first",
                    specialSubs.distinct().joinToString("\n")
                )
            )
        }
        if (mulDivSubs.isNotEmpty() && expr !is Expr.Mul) {
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Multiply from left to right",
                    mulDivSubs.distinct().joinToString("\n")
                )
            )
        }

        val lastTitle = when (expr) {
            is Expr.Add -> "Add the terms together"
            is Expr.Sub -> "Subtract to get the result"
            is Expr.Mul -> "Multiply the numbers"
            is Expr.Div -> "Divide / simplify the fraction"
            else -> "Combine everything for the final answer"
        }
        steps.add(SolutionStep(steps.size + 1, lastTitle, "$originalText = $finalText"))
        return steps
    }

    private fun simplifySingleVarExpression(item: CompiledEquation.Expression, varName: String): SolveResult {
        val preferDecimal = item.expr.containsDecimalLiteral()
        val poly = extractPoly2(item.expr, varName)
        if (poly != null) {
            val formatted = formatPoly2(poly, varName, preferDecimal)
            return SolveResult.Success(
                summaryTitle = "Simplified Expression",
                answers = listOf(AnswerItem(label = "Result", primaryValue = formatted)),
                steps = listOf(
                    SolutionStep(1, "Start with the expression", item.text),
                    SolutionStep(2, "Group and combine like terms ($varName², $varName, and numbers)", formatted)
                )
            )
        }
        return SolveResult.Error("Add '= ...' to turn this into an equation we can solve for $varName.")
    }

    private fun solveSingleVarEquation(eq: CompiledEquation.Equation, varName: String): SolveResult {
        val preferDecimal = eq.lhs.containsDecimalLiteral() || eq.rhs.containsDecimalLiteral()
        val steps = mutableListOf<SolutionStep>()
        steps.add(SolutionStep(1, "Start with your equation", "${eq.lhsText} = ${eq.rhsText}"))

        // Try symbolic polynomial (Linear or Quadratic)
        val lhsPoly = extractPoly2(eq.lhs, varName)
        val rhsPoly = extractPoly2(eq.rhs, varName)
        if (lhsPoly != null && rhsPoly != null) {
            return solvePolynomialBeginnerFriendly(lhsPoly, rhsPoly, eq, varName, preferDecimal, steps)
        }

        // Try structural unwrapping when variable appears once
        val lhsCount = eq.lhs.countVarOccurrences(varName)
        val rhsCount = eq.rhs.countVarOccurrences(varName)
        if (lhsCount == 1 && rhsCount == 0) {
            evalConstant(eq.rhs)?.let { rhsConst ->
                if (eq.rhs !is Expr.Const) {
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            "Simplify the right side first",
                            "${eq.lhsText} = ${rhsConst.formatCompact(preferDecimal)}"
                        )
                    )
                }
                tryStructuralUnwrap(eq.lhs, rhsConst, varName, preferDecimal, eq, steps)?.let { return it }
            }
        } else if (rhsCount == 1 && lhsCount == 0) {
            evalConstant(eq.lhs)?.let { lhsConst ->
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Swap sides so $varName is on the left",
                        "${eq.rhsText} = ${lhsConst.formatCompact(preferDecimal)}"
                    )
                )
                tryStructuralUnwrap(eq.rhs, lhsConst, varName, preferDecimal, eq, steps)?.let { return it }
            }
        }

        return solveNumericalSingleVar(eq.lhs, eq.rhs, varName, preferDecimal, steps)
    }

    private fun solvePolynomialBeginnerFriendly(
        lhs: Poly2,
        rhs: Poly2,
        originalEq: CompiledEquation.Equation,
        varName: String,
        preferDecimal: Boolean,
        steps: MutableList<SolutionStep>
    ): SolveResult {
        val diff = lhs - rhs
        val a = diff.c2
        val b = diff.c1
        val c = diff.c0

        // Show simplified LHS = RHS if original had compound expressions
        val lhsSimplified = formatPoly2(lhs, varName, preferDecimal)
        val rhsSimplified = formatPoly2(rhs, varName, preferDecimal)
        if (lhsSimplified != originalEq.lhsText || rhsSimplified != originalEq.rhsText) {
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Simplify each side separately",
                    "$lhsSimplified = $rhsSimplified"
                )
            )
        }

        if (a.isZero()) {
            // Linear equation: keep variable on left, numbers on right!
            if (b.isZero()) {
                return if (c.isZero()) {
                    SolveResult.Success(
                        summaryTitle = "Always True (Identity)",
                        answers = listOf(AnswerItem(varName, "All real numbers (ℝ)")),
                        steps = steps + SolutionStep(
                            steps.size + 1,
                            "Notice both sides are identical",
                            "Everything cancels out to 0 = 0, so any value of $varName works!"
                        )
                    )
                } else {
                    SolveResult.Success(
                        summaryTitle = "No Solution",
                        answers = listOf(AnswerItem(varName, "No solution")),
                        steps = steps + SolutionStep(
                            steps.size + 1,
                            "Notice the variable cancels out",
                            "We get ${c.formatCompact(preferDecimal)} = 0, which is impossible — so no $varName can solve this."
                        )
                    )
                }
            }

            val varCoeff = lhs.c1 - rhs.c1 // = b
            val constRhs = rhs.c0 - lhs.c0 // = -c

            if (!rhs.c1.isZero()) {
                val action = if (rhs.c1.d > 0) "Subtract ${formatTerm(rhs.c1, varName, true)} from both sides" else "Add ${formatTerm(-rhs.c1, varName, true)} to both sides"
                val leftNow = formatPoly2(Poly2(NumVal.ZERO, varCoeff, lhs.c0), varName, preferDecimal)
                val rightNow = rhs.c0.formatCompact(preferDecimal)
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Collect $varName terms on the left ($action)",
                        "$leftNow = $rightNow"
                    )
                )
            }

            if (!lhs.c0.isZero()) {
                val moveWord = if (lhs.c0.d > 0) "Subtract ${lhs.c0.formatCompact(preferDecimal)} from" else "Add ${(-lhs.c0).formatCompact(preferDecimal)} to"
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Move plain numbers to the right ($moveWord both sides)",
                        "${formatTerm(varCoeff, varName, first = true)} = ${rhs.c0.formatCompact(preferDecimal)} ${if (lhs.c0.d > 0) "−" else "+"} ${NumVal(abs(lhs.c0.d), lhs.c0.r?.let { if (it.num < BigInteger.ZERO) -it else it }, lhs.c0.bd?.abs()).formatCompact(preferDecimal)} = ${constRhs.formatCompact(preferDecimal)}"
                    )
                )
            }

            val sol = constRhs / varCoeff ?: return SolveResult.Error("Division by zero.")
            val (primary, secondary) = sol.formatExactAndApprox(preferDecimal)

            if (!varCoeff.isOne()) {
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Get $varName alone by dividing both sides by ${varCoeff.formatCompact(preferDecimal)}",
                        "$varName = ${constRhs.formatCompact(preferDecimal)} ÷ ${varCoeff.formatCompact(preferDecimal)} = $primary"
                    )
                )
            }

            // Quick verification step for beginners
            val checkVal = originalEq.lhs.evaluateDouble(mapOf(varName to sol.d))
            val rhsCheck = originalEq.rhs.evaluateDouble(mapOf(varName to sol.d))
            if (!checkVal.isNaN() && !rhsCheck.isNaN()) {
                val checkText = sol.formatCompact(preferDecimal)
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Quick check: plug $varName = $checkText back in",
                        "Left side = ${formatNumber(checkVal)}, Right side = ${formatNumber(rhsCheck)} ✓"
                    )
                )
            }

            return SolveResult.Success(
                summaryTitle = "Linear Equation Solved",
                answers = listOf(AnswerItem(varName, primary, secondary)),
                steps = steps
            )
        }

        // Quadratic equation: ax^2 + bx + c = 0
        // Friendly Shortcut 1: Pure quadratic ax^2 + c = 0 (b == 0)
        if (b.isZero()) {
            val rhsConst = -c
            val ratio = (rhsConst / a) ?: return SolveResult.Error("Division by zero.")
            if (!a.isOne() || !c.isZero()) {
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Isolate $varName² on the left side",
                        "$varName² = ${ratio.formatCompact(preferDecimal)}"
                    )
                )
            }
            if (ratio.isZero()) {
                steps.add(SolutionStep(steps.size + 1, "Take square root", "$varName = 0"))
                return SolveResult.Success("Quadratic Solved", listOf(AnswerItem(varName, "0")), steps)
            } else if (ratio.d > 0) {
                val exactSqrt = ratio.r?.let { exactRationalRoot(it, 2) }
                val rootVal = NumVal(sqrt(ratio.d), exactSqrt, null)
                val (p1, s1) = rootVal.formatExactAndApprox(preferDecimal)
                val (p2, s2) = (-rootVal).formatExactAndApprox(preferDecimal)
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Take the square root of both sides (remember both + and −!)",
                        "$varName = ±√(${ratio.formatCompact(preferDecimal)})  ⇒  ${varName}₁ = $p1,  ${varName}₂ = $p2"
                    )
                )
                return SolveResult.Success(
                    summaryTitle = "Quadratic Equation Solved",
                    answers = listOf(AnswerItem("${varName}₁", p1, s1), AnswerItem("${varName}₂", p2, s2)),
                    steps = steps
                )
            }
        }

        // Friendly Shortcut 2: Missing constant term ax^2 + bx = 0 (c == 0)
        if (c.isZero()) {
            val root2 = (-b / a) ?: return SolveResult.Error("Division by zero.")
            val (p2, s2) = root2.formatExactAndApprox(preferDecimal)
            val innerFactor = formatPoly2(Poly2(NumVal.ZERO, a, b), varName, preferDecimal)
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Factor out $varName from both terms",
                    "$varName($innerFactor) = 0"
                )
            )
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Set each factor equal to 0",
                    "Either $varName = 0, or $innerFactor = 0  ⇒  $varName = $p2"
                )
            )
            return SolveResult.Success(
                summaryTitle = "Quadratic Equation Solved",
                answers = listOf(AnswerItem("${varName}₁", "0"), AnswerItem("${varName}₂", p2, s2)),
                steps = steps
            )
        }

        // General Quadratic: move all terms to left side = 0
        val stdForm = "${formatPoly2(diff, varName, preferDecimal)} = 0"
        if (stdForm != "${originalEq.lhsText} = ${originalEq.rhsText}") {
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Move all terms to one side so it equals 0",
                    "$stdForm\nHere a = ${a.formatCompact(preferDecimal)}, b = ${b.formatCompact(preferDecimal)}, c = ${c.formatCompact(preferDecimal)}"
                )
            )
        } else {
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Identify a, b, and c from a$varName² + b$varName + c = 0",
                    "a = ${a.formatCompact(preferDecimal)},  b = ${b.formatCompact(preferDecimal)},  c = ${c.formatCompact(preferDecimal)}"
                )
            )
        }

        val bSq = b * b
        val fourAC = NumVal(4.0, Rational.of(4), BigDecimal.valueOf(4)) * a * c
        val disc = bSq - fourAC
        steps.add(
            SolutionStep(
                steps.size + 1,
                "Check the discriminant Δ = b² − 4ac (tells us how many solutions exist)",
                "Δ = (${b.formatCompact(preferDecimal)})² − 4(${a.formatCompact(preferDecimal)})(${c.formatCompact(preferDecimal)})\nΔ = ${bSq.formatCompact(preferDecimal)} − ${fourAC.formatCompact(preferDecimal)} = ${disc.formatCompact(preferDecimal)}"
            )
        )

        val twoA = NumVal(2.0, Rational.of(2), BigDecimal.valueOf(2)) * a
        if (disc.isZero()) {
            val root = (-b) / twoA ?: return SolveResult.Error("Division by zero.")
            val (primary, secondary) = root.formatExactAndApprox(preferDecimal)
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Since Δ = 0, there is 1 repeated solution",
                    "$varName = -b / (2a) = ${(-b).formatCompact(preferDecimal)} / ${twoA.formatCompact(preferDecimal)} = $primary"
                )
            )
            return SolveResult.Success("Quadratic Equation Solved", listOf(AnswerItem(varName, primary, secondary)), steps)
        } else if (disc.d > 0) {
            val sqrtDiscD = sqrt(disc.d)
            val exactSqrtDiscRat = disc.r?.let { exactRationalRoot(it, 2) }
            val sqrtDisc = NumVal(sqrtDiscD, exactSqrtDiscRat, exactSqrtDiscRat?.toTerminatingDecimal())
            val r1 = ((-b) + sqrtDisc) / twoA ?: return SolveResult.Error("Division by zero.")
            val r2 = ((-b) - sqrtDisc) / twoA ?: return SolveResult.Error("Division by zero.")
            val (p1, s1) = r1.formatExactAndApprox(preferDecimal)
            val (p2, s2) = r2.formatExactAndApprox(preferDecimal)

            // If a == 1 and roots are clean integers, show the intuitive factored form too!
            if (a.isOne() && r1.r?.isInteger() == true && r2.r?.isInteger() == true) {
                val f1 = formatLinearFactor(varName, r1.r)
                val f2 = formatLinearFactor(varName, r2.r)
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Tip: Because √Δ = ${sqrtDisc.formatCompact(preferDecimal)} is clean, this factors neatly!",
                        "$f1$f2 = 0"
                    )
                )
            }

            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Use the quadratic formula $varName = (-b ± √Δ) / (2a)",
                    "${varName}₁ = (${(-b).formatCompact(preferDecimal)} + ${sqrtDisc.formatCompact(preferDecimal)}) / ${twoA.formatCompact(preferDecimal)} = $p1\n" +
                        "${varName}₂ = (${(-b).formatCompact(preferDecimal)} − ${sqrtDisc.formatCompact(preferDecimal)}) / ${twoA.formatCompact(preferDecimal)} = $p2"
                )
            )
            return SolveResult.Success(
                summaryTitle = "Quadratic Equation Solved",
                answers = listOf(AnswerItem("${varName}₁", p1, s1), AnswerItem("${varName}₂", p2, s2)),
                steps = steps
            )
        } else {
            val realPart = (-b) / twoA ?: return SolveResult.Error("Division by zero.")
            val imagPart = NumVal(sqrt(-disc.d)) / NumVal(abs(twoA.d)) ?: return SolveResult.Error("Division by zero.")
            val realStr = if (realPart.isZero()) "" else "${realPart.formatCompact(preferDecimal)} "
            val imagStr = "${imagPart.formatCompact(preferDecimal)}i"
            val ans1 = if (realStr.isEmpty()) imagStr else "$realStr+ $imagStr"
            val ans2 = if (realStr.isEmpty()) "-$imagStr" else "$realStr− $imagStr"
            steps.add(
                SolutionStep(
                    steps.size + 1,
                    "Since Δ < 0, there are no real roots — only complex roots (using i = √-1)",
                    "${varName}₁ = $ans1\n${varName}₂ = $ans2"
                )
            )
            return SolveResult.Success(
                summaryTitle = "Complex Roots Found",
                answers = listOf(AnswerItem("${varName}₁", ans1), AnswerItem("${varName}₂", ans2)),
                steps = steps
            )
        }
    }

    private fun formatLinearFactor(varName: String, root: Rational): String {
        val negRoot = -root
        return if (negRoot.isZero()) varName
        else if (negRoot.num > BigInteger.ZERO) "($varName + ${negRoot.format()})"
        else "($varName − ${(-negRoot).format()})"
    }

    private fun tryStructuralUnwrap(
        expr: Expr,
        targetVal: NumVal,
        varName: String,
        preferDecimal: Boolean,
        originalEq: CompiledEquation.Equation,
        steps: MutableList<SolutionStep>
    ): SolveResult? {
        var currExpr = expr
        var currTarget = targetVal

        for (guard in 0 until 20) {
            if (currExpr is Expr.Var && currExpr.name == varName) {
                val (p, s) = currTarget.formatExactAndApprox(preferDecimal)
                val checkL = originalEq.lhs.evaluateDouble(mapOf(varName to currTarget.d))
                val checkR = originalEq.rhs.evaluateDouble(mapOf(varName to currTarget.d))
                if (!checkL.isNaN() && !checkR.isNaN()) {
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            "Quick check: plug $varName = ${currTarget.formatCompact(preferDecimal)} back in",
                            "Left side = ${formatNumber(checkL)}, Right side = ${formatNumber(checkR)} ✓"
                        )
                    )
                }
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
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            "Flip the signs (multiply both sides by -1)",
                            "${currExpr.toMathText()} = ${currTarget.formatCompact(preferDecimal)}"
                        )
                    )
                }
                is Expr.Add -> {
                    val leftConst = evalConstant(currExpr.left)
                    val rightConst = evalConstant(currExpr.right)
                    val c = rightConst ?: leftConst ?: return null
                    currExpr = if (rightConst != null) currExpr.left else currExpr.right
                    currTarget -= c
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            "Undo '+ ${c.formatCompact(preferDecimal)}' by subtracting ${c.formatCompact(preferDecimal)} from both sides",
                            "${currExpr.toMathText()} = ${currTarget.formatCompact(preferDecimal)}"
                        )
                    )
                }
                is Expr.Sub -> {
                    val leftConst = evalConstant(currExpr.left)
                    val rightConst = evalConstant(currExpr.right)
                    if (rightConst != null) {
                        currExpr = currExpr.left
                        currTarget += rightConst
                        steps.add(
                            SolutionStep(
                                steps.size + 1,
                                "Undo '− ${rightConst.formatCompact(preferDecimal)}' by adding ${rightConst.formatCompact(preferDecimal)} to both sides",
                                "${currExpr.toMathText()} = ${currTarget.formatCompact(preferDecimal)}"
                            )
                        )
                    } else if (leftConst != null) {
                        currExpr = currExpr.right
                        currTarget = leftConst - currTarget
                        steps.add(
                            SolutionStep(
                                steps.size + 1,
                                "Rearrange to isolate ${currExpr.toMathText()}",
                                "${currExpr.toMathText()} = ${leftConst.formatCompact(preferDecimal)} − ${targetVal.formatCompact(preferDecimal)} = ${currTarget.formatCompact(preferDecimal)}"
                            )
                        )
                    } else return null
                }
                is Expr.Mul -> {
                    val leftConst = evalConstant(currExpr.left)
                    val rightConst = evalConstant(currExpr.right)
                    val c = rightConst ?: leftConst ?: return null
                    if (c.isZero()) return null
                    currExpr = if (rightConst != null) currExpr.left else currExpr.right
                    currTarget = (currTarget / c) ?: return null
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            "Undo multiplying by ${c.formatCompact(preferDecimal)} — divide both sides by ${c.formatCompact(preferDecimal)}",
                            "${currExpr.toMathText()} = ${currTarget.formatCompact(preferDecimal)}"
                        )
                    )
                }
                is Expr.Div -> {
                    val rightConst = evalConstant(currExpr.right)
                    val leftConst = evalConstant(currExpr.left)
                    if (rightConst != null) {
                        if (rightConst.isZero()) return null
                        val prevTarget = currTarget.formatCompact(preferDecimal)
                        currExpr = currExpr.left
                        currTarget *= rightConst
                        steps.add(
                            SolutionStep(
                                steps.size + 1,
                                "Clear the fraction by multiplying both sides by ${rightConst.formatCompact(preferDecimal)}",
                                "${currExpr.toMathText()} = $prevTarget × ${rightConst.formatCompact(preferDecimal)} = ${currTarget.formatCompact(preferDecimal)}"
                            )
                        )
                    } else if (leftConst != null) {
                        if (currTarget.isZero()) {
                            return SolveResult.Success(
                                summaryTitle = "No Solution",
                                answers = listOf(AnswerItem(varName, "No solution")),
                                steps = steps + SolutionStep(steps.size + 1, "Check fraction rule", "A fraction with non-zero top (${leftConst.formatCompact(preferDecimal)}) can never equal 0.")
                            )
                        }
                        currExpr = currExpr.right
                        currTarget = (leftConst / currTarget) ?: return null
                        steps.add(
                            SolutionStep(
                                steps.size + 1,
                                "Multiply both sides by the denominator and divide",
                                "${currExpr.toMathText()} = ${currTarget.formatCompact(preferDecimal)}"
                            )
                        )
                    } else return null
                }
                is Expr.Root -> {
                    val deg = currExpr.degree?.let { evalConstant(it) } ?: NumVal(2.0, Rational.of(2), BigDecimal.valueOf(2))
                    val degInt = deg.d.roundToLong()
                    val isInt = abs(deg.d - degInt) < 1e-10 && degInt >= 1L
                    if (isInt && degInt % 2L == 0L && currTarget.d < 0.0) {
                        return SolveResult.Success(
                            summaryTitle = "No Real Solution",
                            answers = listOf(AnswerItem(varName, "No real solution")),
                            steps = steps + SolutionStep(steps.size + 1, "Check even root rule", "An even root (like √) can never give a negative result (${currTarget.formatCompact(preferDecimal)}).")
                        )
                    }
                    val prevText = currTarget.formatCompact(preferDecimal)
                    val raised = evalConstant(Expr.Pow(Expr.Const(currTarget.d, currTarget.r, currTarget.bd), Expr.Const(deg.d, deg.r, deg.bd))) ?: return null
                    currExpr = currExpr.radicand
                    currTarget = raised
                    val actionTitle = if (degInt == 2L) "Undo the square root by squaring both sides" else "Undo the ${deg.formatCompact(preferDecimal)}th root by raising both sides to power ${deg.formatCompact(preferDecimal)}"
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            actionTitle,
                            "${currExpr.toMathText()} = ($prevText)^${deg.formatCompact(preferDecimal)} = ${currTarget.formatCompact(preferDecimal)}"
                        )
                    )
                }
                is Expr.Log -> {
                    val baseConst = evalConstant(currExpr.base) ?: return null
                    if (baseConst.d <= 0.0 || abs(baseConst.d - 1.0) < 1e-10) {
                        return SolveResult.Error("Logarithm base must be positive and not equal to 1.")
                    }
                    val prevTargetText = currTarget.formatCompact(preferDecimal)
                    val raised = evalConstant(Expr.Pow(Expr.Const(baseConst.d, baseConst.r, baseConst.bd), Expr.Const(currTarget.d, currTarget.r, currTarget.bd))) ?: return null
                    currExpr = currExpr.arg
                    currTarget = raised
                    val baseDisplay = (currExpr as? Expr.Const)?.symbolName ?: baseConst.formatCompact(preferDecimal)
                    steps.add(
                        SolutionStep(
                            steps.size + 1,
                            "Rewrite log in power form (rule: if log_b(A) = C, then A = b^C)",
                            "${currExpr.toMathText()} = $baseDisplay^($prevTargetText) = ${raised.formatCompact(preferDecimal)}"
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
                                steps = steps + SolutionStep(steps.size + 1, "Check positive powers", "${baseConst.formatCompact(preferDecimal)} raised to any real power is always positive, so it cannot equal ${currTarget.formatCompact(preferDecimal)}.")
                            )
                        }
                        val prevTargetText = currTarget.formatCompact(preferDecimal)
                        val logged = evalConstant(Expr.Log(Expr.Const(baseConst.d, baseConst.r, baseConst.bd), Expr.Const(currTarget.d, currTarget.r, currTarget.bd))) ?: return null
                        currExpr = currExpr.exp
                        currTarget = logged
                        steps.add(
                            SolutionStep(
                                steps.size + 1,
                                "Use log base ${baseConst.formatCompact(preferDecimal)} to bring down the exponent",
                                "${currExpr.toMathText()} = log_${baseConst.formatCompact(preferDecimal)}($prevTargetText) = ${logged.formatCompact(preferDecimal)}"
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
                                        steps = steps + SolutionStep(steps.size + 1, "Check even power", "An even power cannot equal a negative number (${currTarget.formatCompact(preferDecimal)}).")
                                    )
                                }
                                val rootPos = evalConstant(Expr.Root(Expr.Const(expConst.d, expConst.r, expConst.bd), Expr.Const(currTarget.d, currTarget.r, currTarget.bd))) ?: return null
                                if (rootPos.isZero()) {
                                    currExpr = currExpr.base
                                    currTarget = NumVal.ZERO
                                    steps.add(SolutionStep(steps.size + 1, "Take ${expInt}th root of both sides", "${currExpr.toMathText()} = 0"))
                                } else {
                                    val branchSteps1 = steps.toMutableList()
                                    branchSteps1.add(SolutionStep(branchSteps1.size + 1, "Undo power $expInt (Positive branch)", "${currExpr.base.toMathText()} = +${rootPos.formatCompact(preferDecimal)}"))
                                    val res1 = tryStructuralUnwrap(currExpr.base, rootPos, varName, preferDecimal, originalEq, branchSteps1)
                                    val branchSteps2 = mutableListOf<SolutionStep>()
                                    branchSteps2.add(SolutionStep(1, "Undo power $expInt (Negative branch)", "${currExpr.base.toMathText()} = ${(-rootPos).formatCompact(preferDecimal)}"))
                                    val res2 = tryStructuralUnwrap(currExpr.base, -rootPos, varName, preferDecimal, originalEq, branchSteps2)
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
                                val prevText = currTarget.formatCompact(preferDecimal)
                                val rootVal = evalConstant(Expr.Root(Expr.Const(expConst.d, expConst.r, expConst.bd), Expr.Const(currTarget.d, currTarget.r, currTarget.bd))) ?: return null
                                currExpr = currExpr.base
                                currTarget = rootVal
                                steps.add(
                                    SolutionStep(
                                        steps.size + 1,
                                        "Undo power $expInt by taking the ${expInt}th root of both sides",
                                        "${currExpr.toMathText()} = ${expInt}√($prevText) = ${currTarget.formatCompact(preferDecimal)}"
                                    )
                                )
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
            is Expr.Const -> Poly2(NumVal.ZERO, NumVal.ZERO, NumVal.fromExprConst(expr))
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
        preferDecimal: Boolean,
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
                    if (flo * fmid <= 0.0) hi = mid else { lo = mid; flo = fmid }
                }
                addRootIfValid(0.5 * (lo + hi))
            }
        }

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
                answers = listOf(AnswerItem(varName, "No real solution")),
                steps = steps + SolutionStep(steps.size + 1, "Check values", "Tested across real numbers; no $varName makes both sides equal.")
            )
        }

        val sorted = foundRoots.sorted()
        val answers = sorted.mapIndexed { index, r ->
            val label = if (sorted.size == 1) varName else "${varName}${subscriptNumber(index + 1)}"
            AnswerItem(label, formatNumber(r))
        }
        val firstAns = sorted.first()
        steps.add(
            SolutionStep(
                steps.size + 1,
                "Find the value(s) of $varName where Left Side = Right Side",
                answers.joinToString("\n") { "${it.label} = ${it.primaryValue}" }
            )
        )
        steps.add(
            SolutionStep(
                steps.size + 1,
                "Quick check at $varName = ${formatNumber(firstAns)}",
                "Left side = ${formatNumber(lhs.evaluateDouble(mapOf(varName to firstAns)))}, Right side = ${formatNumber(rhs.evaluateDouble(mapOf(varName to firstAns)))} ✓"
            )
        )
        return SolveResult.Success("Equation Solved", answers, steps)
    }

    private fun solveMultiVarSystem(
        compiled: List<CompiledEquation>,
        entries: List<EquationEntry>,
        vars: List<String>
    ): SolveResult {
        val preferDecimal = compiled.any {
            when (it) {
                is CompiledEquation.Equation -> it.lhs.containsDecimalLiteral() || it.rhs.containsDecimalLiteral()
                is CompiledEquation.Expression -> it.expr.containsDecimalLiteral()
            }
        }
        val steps = mutableListOf<SolutionStep>()
        val eqListText = entries.mapIndexed { idx, e -> "(${idx + 1}) ${e.root.toReadableString()}" }
            .joinToString("\n")
        steps.add(SolutionStep(1, "We have ${vars.size} equations for ${vars.size} unknowns (${vars.joinToString(", ")})", eqListText))

        val eqPairs = mutableListOf<Pair<Expr, Expr>>()
        for ((idx, item) in compiled.withIndex()) {
            when (item) {
                is CompiledEquation.Equation -> eqPairs.add(item.lhs to item.rhs)
                is CompiledEquation.Expression -> return SolveResult.Error("Equation ${idx + 1} is missing an '=' sign.")
            }
        }

        val linearRows = eqPairs.map { (lhs, rhs) ->
            val l = extractLinear(lhs, vars)
            val r = extractLinear(rhs, vars)
            if (l != null && r != null) l - r else null
        }

        if (linearRows.all { it != null }) {
            return solveLinearSystemBeginnerFriendly(linearRows.filterNotNull(), vars, preferDecimal, eqPairs, steps)
        }

        return solveNonlinearSystem(eqPairs, vars, steps)
    }

    private fun extractLinear(expr: Expr, vars: List<String>): LinearExpr? {
        val zeroCoeffs = List(vars.size) { NumVal.ZERO }
        return when (expr) {
            is Expr.Const -> LinearExpr(zeroCoeffs, NumVal.fromExprConst(expr))
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
                val inv = NumVal.ONE / rConst ?: return null
                l.scale(inv)
            }
            is Expr.Pow, is Expr.Log, is Expr.Root -> evalConstant(expr)?.let { LinearExpr(zeroCoeffs, it) }
        }
    }

    private fun formatLinearEq(coeffs: List<NumVal>, rhs: NumVal, vars: List<String>, preferDecimal: Boolean): String {
        val lhsStr = buildString {
            var first = true
            for (j in vars.indices) {
                val coef = coeffs[j]
                if (!coef.isZero()) {
                    append(formatTerm(coef, vars[j], first, preferDecimal))
                    first = false
                }
            }
        }.ifEmpty { "0" }
        return "$lhsStr = ${rhs.formatCompact(preferDecimal)}"
    }

    private fun solveLinearSystemBeginnerFriendly(
        rows: List<LinearExpr>,
        vars: List<String>,
        preferDecimal: Boolean,
        eqPairs: List<Pair<Expr, Expr>>,
        steps: MutableList<SolutionStep>
    ): SolveResult {
        val n = vars.size
        val m = rows.size
        val matrix = Array(m) { r ->
            Array(n + 1) { c ->
                if (c < n) rows[r].coeffs[c] else -rows[r].constant
            }
        }

        val stdFormText = rows.mapIndexed { idx, row ->
            "(${idx + 1}) ${formatLinearEq(row.coeffs, -row.constant, vars, preferDecimal)}"
        }.joinToString("\n")
        steps.add(SolutionStep(steps.size + 1, "Write each equation neatly (variables on left, number on right)", stdFormText))

        var pivotRow = 0
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
                val tmp = matrix[pivotRow]; matrix[pivotRow] = matrix[bestRow]; matrix[bestRow] = tmp
            }

            val pivotVal = matrix[pivotRow][col]
            for (c in col..n) {
                matrix[pivotRow][c] = (matrix[pivotRow][c] / pivotVal)
                    ?: return SolveResult.Error("Division error while solving system.")
            }

            val eliminatedNotes = mutableListOf<String>()
            for (r in 0 until m) {
                if (r != pivotRow && !matrix[r][col].isZero()) {
                    val factor = matrix[r][col]
                    for (c in col..n) {
                        matrix[r][c] = matrix[r][c] - (factor * matrix[pivotRow][c])
                    }
                    if (r > pivotRow) {
                        val rowCoeffs = (0 until n).map { matrix[r][it] }
                        eliminatedNotes.add("(${r + 1}) ${formatLinearEq(rowCoeffs, matrix[r][n], vars, preferDecimal)}")
                    }
                }
            }
            if (eliminatedNotes.isNotEmpty()) {
                steps.add(
                    SolutionStep(
                        steps.size + 1,
                        "Eliminate '${vars[col]}' from the remaining equation(s)",
                        eliminatedNotes.joinToString("\n")
                    )
                )
            }
            pivotRow++
        }

        for (r in 0 until m) {
            val allZeroCoeffs = (0 until n).all { matrix[r][it].isZero() }
            if (allZeroCoeffs && !matrix[r][n].isZero()) {
                return SolveResult.Success(
                    summaryTitle = "No Solution",
                    answers = listOf(AnswerItem("System", "No solution (parallel equations)")),
                    steps = steps + SolutionStep(
                        steps.size + 1,
                        "Contradiction found",
                        "Eliminating variables gave 0 = ${matrix[r][n].formatCompact(preferDecimal)}, which is impossible."
                    )
                )
            }
        }

        if (pivotRow < n) {
            return SolveResult.Success(
                summaryTitle = "Infinitely Many Solutions",
                answers = listOf(AnswerItem("System", "Infinitely many solutions")),
                steps = steps + SolutionStep(
                    steps.size + 1,
                    "Equations overlap",
                    "One equation is a combination of the others, leaving infinitely many valid answers."
                )
            )
        }

        // Show friendly back-substitution steps from last variable up to first
        val backSubLines = mutableListOf<String>()
        for (r in n - 1 downTo 0) {
            val (p, s) = matrix[r][n].formatExactAndApprox(preferDecimal)
            val valStr = if (s != null) "$p ($s)" else p
            if (r == n - 1) {
                backSubLines.add("Solve directly: ${vars[r]} = $valStr")
            } else {
                val knownList = (r + 1 until n).joinToString(", ") { k -> "${vars[k]} = ${matrix[k][n].formatCompact(preferDecimal)}" }
                backSubLines.add("Plug in $knownList  ⇒  ${vars[r]} = $valStr")
            }
        }
        steps.add(
            SolutionStep(
                steps.size + 1,
                "Back-substitute to find each variable",
                backSubLines.joinToString("\n")
            )
        )

        val env = vars.indices.associate { vars[it] to matrix[it][n].d }
        val checkLines = eqPairs.mapIndexed { idx, (l, r) ->
            "Eq (${idx + 1}): ${formatNumber(l.evaluateDouble(env))} = ${formatNumber(r.evaluateDouble(env))} ✓"
        }
        steps.add(SolutionStep(steps.size + 1, "Quick check in every equation", checkLines.joinToString("\n")))

        val answers = (0 until n).map { r ->
            val (p, s) = matrix[r][n].formatExactAndApprox(preferDecimal)
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
                val jac = Array(n) { DoubleArray(n) }
                var jacOk = true
                for (j in 0 until n) {
                    val h = 1e-6 * maxOf(1.0, abs(x[j]))
                    x[j] += h
                    val fp = evalResidual(x)
                    x[j] -= h
                    if (fp == null) { jacOk = false; break }
                    for (i in 0 until n) jac[i][j] = (fp[i] - f0[i]) / h
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
                            "Find values satisfying all equations together",
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

    private fun formatPoly2(poly: Poly2, varName: String, preferDecimal: Boolean = true): String {
        val parts = mutableListOf<String>()
        if (!poly.c2.isZero()) {
            parts.add(formatTerm(poly.c2, "$varName²", first = true, preferDecimal = preferDecimal))
        }
        if (!poly.c1.isZero()) {
            parts.add(formatTerm(poly.c1, varName, first = parts.isEmpty(), preferDecimal = preferDecimal))
        }
        if (!poly.c0.isZero() || parts.isEmpty()) {
            if (parts.isEmpty()) {
                parts.add(poly.c0.formatCompact(preferDecimal))
            } else {
                val sign = if (poly.c0.d < 0) " − " else " + "
                val mag = if (poly.c0.d < 0) -poly.c0 else poly.c0
                parts.add("$sign${mag.formatCompact(preferDecimal)}")
            }
        }
        return parts.joinToString("")
    }

    private fun formatTerm(coef: NumVal, symbol: String, first: Boolean, preferDecimal: Boolean = true): String {
        val isNeg = coef.d < 0
        val mag = if (isNeg) -coef else coef
        val coefText = if (mag.isOne()) "" else mag.formatCompact(preferDecimal)
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
