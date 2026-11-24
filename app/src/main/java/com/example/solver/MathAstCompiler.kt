package com.example.solver

import com.example.model.FractionNode
import com.example.model.LogNode
import com.example.model.MathNode
import com.example.model.MathSlot
import com.example.model.PowerNode
import com.example.model.RootNode
import com.example.model.SymbolNode
import com.example.model.findFirstEmptySlotId
import com.example.model.toReadableString
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

data class Rational(val num: BigInteger, val den: BigInteger) {
    init {
        require(den != BigInteger.ZERO) { "Division by zero in Rational" }
    }

    operator fun plus(other: Rational): Rational =
        create(num * other.den + other.num * den, den * other.den)

    operator fun minus(other: Rational): Rational =
        create(num * other.den - other.num * den, den * other.den)

    operator fun times(other: Rational): Rational =
        create(num * other.num, den * other.den)

    operator fun div(other: Rational): Rational {
        require(other.num != BigInteger.ZERO) { "Division by zero" }
        return create(num * other.den, den * other.num)
    }

    operator fun unaryMinus(): Rational = Rational(-num, den)

    fun isZero(): Boolean = num == BigInteger.ZERO
    fun isOne(): Boolean = num == BigInteger.ONE && den == BigInteger.ONE
    fun isInteger(): Boolean = den == BigInteger.ONE

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    /**
     * Returns an exact BigDecimal if this fraction terminates in base 10
     * (i.e., denominator only has prime factors 2 and 5), or null otherwise.
     */
    fun toTerminatingDecimal(): BigDecimal? {
        if (num == BigInteger.ZERO) return BigDecimal.ZERO
        if (den == BigInteger.ONE) return BigDecimal(num)
        var d = den
        val two = BigInteger.valueOf(2)
        val five = BigInteger.valueOf(5)
        while (d.mod(two) == BigInteger.ZERO) d = d.divide(two)
        while (d.mod(five) == BigInteger.ZERO) d = d.divide(five)
        if (d != BigInteger.ONE) return null
        return BigDecimal(num).divide(BigDecimal(den))
    }

    fun formatDecimalClean(): String? = toTerminatingDecimal()?.stripTrailingZeros()?.toPlainString()

    fun format(): String = if (den == BigInteger.ONE) num.toString() else "$num/$den"

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)

        fun of(n: Long, d: Long = 1L): Rational =
            create(BigInteger.valueOf(n), BigInteger.valueOf(d))

        fun create(n: BigInteger, d: BigInteger): Rational {
            require(d != BigInteger.ZERO) { "Division by zero" }
            if (n == BigInteger.ZERO) return ZERO
            val sign = if (d < BigInteger.ZERO) -BigInteger.ONE else BigInteger.ONE
            val sn = n * sign
            val sd = d * sign
            val g = sn.abs().gcd(sd)
            return Rational(sn / g, sd / g)
        }

        fun parseDecimal(text: String): Rational? {
            if (text.isEmpty() || text == ".") return null
            val parts = text.split(".")
            if (parts.size > 2) return null
            return try {
                if (parts.size == 1) {
                    Rational(BigInteger(parts[0]), BigInteger.ONE)
                } else {
                    val intPart = if (parts[0].isEmpty()) "0" else parts[0]
                    val fracPart = parts[1]
                    if (fracPart.length > 18) return null
                    val scale = BigInteger.TEN.pow(fracPart.length)
                    val combined = BigInteger(intPart) * scale + BigInteger(if (fracPart.isEmpty()) "0" else fracPart)
                    create(combined, scale)
                }
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}

sealed interface Expr {
    data class Const(
        val value: Double,
        val rational: Rational? = null,
        val decimal: BigDecimal? = null,
        val symbolName: String? = null,
        val fromDecimalLiteral: Boolean = false
    ) : Expr
    data class Var(val name: String) : Expr
    data class Neg(val inner: Expr) : Expr
    data class Add(val left: Expr, val right: Expr) : Expr
    data class Sub(val left: Expr, val right: Expr) : Expr
    data class Mul(val left: Expr, val right: Expr) : Expr
    data class Div(val left: Expr, val right: Expr) : Expr
    data class Pow(val base: Expr, val exp: Expr) : Expr
    data class Log(val base: Expr, val arg: Expr) : Expr
    data class Root(val degree: Expr?, val radicand: Expr) : Expr
}

sealed interface CompiledEquation {
    data class Expression(val expr: Expr, val text: String) : CompiledEquation
    data class Equation(val lhs: Expr, val rhs: Expr, val lhsText: String, val rhsText: String) : CompiledEquation
}

class CompileException(message: String) : IllegalArgumentException(message)

object MathAstCompiler {

    private sealed interface Token {
        data class Num(val text: String) : Token
        data class Variable(val name: String) : Token
        data class Op(val op: String) : Token // +, -, *, /
        data object LParen : Token
        data object RParen : Token
        data class Embedded(val expr: Expr) : Token
    }

    fun compileEquation(root: MathSlot): CompiledEquation {
        if (root.nodes.isEmpty()) {
            throw CompileException("Equation is empty.")
        }
        val emptyId = root.findFirstEmptySlotId()
        if (emptyId != null) {
            throw CompileException("Fill in all [ ] placeholder boxes before solving.")
        }

        val eqIndices = root.nodes.mapIndexedNotNull { index, node ->
            if (node is SymbolNode && node.value == "=") index else null
        }

        return when (eqIndices.size) {
            0 -> {
                val expr = compileNodeList(root.nodes)
                CompiledEquation.Expression(expr, formatNodeList(root.nodes))
            }
            1 -> {
                val idx = eqIndices[0]
                val lhsNodes = root.nodes.subList(0, idx)
                val rhsNodes = root.nodes.subList(idx + 1, root.nodes.size)
                if (lhsNodes.isEmpty() || rhsNodes.isEmpty()) {
                    throw CompileException("Both sides of '=' must contain an expression.")
                }
                CompiledEquation.Equation(
                    lhs = compileNodeList(lhsNodes),
                    rhs = compileNodeList(rhsNodes),
                    lhsText = formatNodeList(lhsNodes),
                    rhsText = formatNodeList(rhsNodes)
                )
            }
            else -> throw CompileException("An equation can contain at most one '=' sign.")
        }
    }

    fun compileSlot(slot: MathSlot): Expr {
        if (slot.nodes.isEmpty()) {
            throw CompileException("Fill in all [ ] placeholder boxes before solving.")
        }
        if (slot.nodes.any { it is SymbolNode && it.value == "=" }) {
            throw CompileException("'=' cannot be placed inside a placeholder box.")
        }
        return compileNodeList(slot.nodes)
    }

    private fun compileNodeList(nodes: List<MathNode>): Expr {
        val rawTokens = tokenize(nodes)
        val tokens = insertImplicitMultiplication(rawTokens)
        val parser = Parser(tokens)
        val expr = parser.parseExpression()
        if (parser.hasMore()) {
            throw CompileException("Unexpected syntax near '${parser.peekDescription()}'.")
        }
        return expr
    }

    private fun tokenize(nodes: List<MathNode>): List<Token> {
        val tokens = mutableListOf<Token>()
        val numBuffer = StringBuilder()

        fun flushNumber() {
            if (numBuffer.isNotEmpty()) {
                val str = numBuffer.toString()
                if (str == ".") throw CompileException("Invalid standalone decimal point '.'")
                tokens.add(Token.Num(str))
                numBuffer.clear()
            }
        }

        for (node in nodes) {
            when (node) {
                is SymbolNode -> {
                    val v = node.value
                    when {
                        v.isNotEmpty() && v.all { it.isDigit() || it == '.' } -> {
                            for (ch in v) {
                                if (ch == '.' && numBuffer.contains(".")) {
                                    throw CompileException("Invalid number with multiple decimal points.")
                                }
                                numBuffer.append(ch)
                            }
                        }
                        v in setOf("x", "y", "z") -> {
                            flushNumber()
                            tokens.add(Token.Variable(v))
                        }
                        v == "e" -> {
                            flushNumber()
                            tokens.add(
                                Token.Embedded(
                                    Expr.Const(
                                        value = Math.E,
                                        rational = null,
                                        decimal = BigDecimal.valueOf(Math.E),
                                        symbolName = "e"
                                    )
                                )
                            )
                        }
                        v == "+" -> {
                            flushNumber()
                            tokens.add(Token.Op("+"))
                        }
                        v == "-" || v == "−" -> {
                            flushNumber()
                            tokens.add(Token.Op("-"))
                        }
                        v == "×" || v == "*" -> {
                            flushNumber()
                            tokens.add(Token.Op("*"))
                        }
                        v == "÷" || v == "/" -> {
                            flushNumber()
                            tokens.add(Token.Op("/"))
                        }
                        v == "(" -> {
                            flushNumber()
                            tokens.add(Token.LParen)
                        }
                        v == ")" -> {
                            flushNumber()
                            tokens.add(Token.RParen)
                        }
                        v == "=" -> throw CompileException("Unexpected '=' inside expression.")
                        else -> throw CompileException("Unsupported symbol '$v'.")
                    }
                }
                is PowerNode -> {
                    flushNumber()
                    val baseExpr = compileSlot(node.base)
                    val expExpr = compileSlot(node.exponent)
                    tokens.add(Token.Embedded(Expr.Pow(baseExpr, expExpr)))
                }
                is LogNode -> {
                    flushNumber()
                    val baseExpr = compileSlot(node.base)
                    val argExpr = compileSlot(node.argument)
                    tokens.add(Token.Embedded(Expr.Log(baseExpr, argExpr)))
                }
                is RootNode -> {
                    flushNumber()
                    val degExpr = node.degree?.let { compileSlot(it) }
                    val radExpr = compileSlot(node.radicand)
                    tokens.add(Token.Embedded(Expr.Root(degExpr, radExpr)))
                }
                is FractionNode -> {
                    flushNumber()
                    val numExpr = compileSlot(node.numerator)
                    val denExpr = compileSlot(node.denominator)
                    tokens.add(Token.Embedded(Expr.Div(numExpr, denExpr)))
                }
            }
        }
        flushNumber()
        return tokens
    }

    private fun insertImplicitMultiplication(tokens: List<Token>): List<Token> {
        if (tokens.isEmpty()) return tokens
        val out = mutableListOf<Token>()
        for (i in tokens.indices) {
            val curr = tokens[i]
            if (i > 0) {
                val prev = tokens[i - 1]
                val prevEndsValue = prev is Token.Num || prev is Token.Variable ||
                    prev is Token.RParen || prev is Token.Embedded
                val currStartsValue = curr is Token.Num || curr is Token.Variable ||
                    curr is Token.LParen || curr is Token.Embedded
                if (prevEndsValue && currStartsValue) {
                    out.add(Token.Op("*"))
                }
            }
            out.add(curr)
        }
        return out
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun hasMore(): Boolean = pos < tokens.size

        fun peekDescription(): String =
            if (pos < tokens.size) tokens[pos].toString() else "end"

        fun parseExpression(): Expr {
            var left = parseTerm()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Op && (tok.op == "+" || tok.op == "-")) {
                    pos++
                    val right = parseTerm()
                    left = if (tok.op == "+") Expr.Add(left, right) else Expr.Sub(left, right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseTerm(): Expr {
            var left = parseUnary()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Op && (tok.op == "*" || tok.op == "/")) {
                    pos++
                    val right = parseUnary()
                    left = if (tok.op == "*") Expr.Mul(left, right) else Expr.Div(left, right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseUnary(): Expr {
            if (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Op && tok.op == "+") {
                    pos++
                    return parseUnary()
                }
                if (tok is Token.Op && tok.op == "-") {
                    pos++
                    return Expr.Neg(parseUnary())
                }
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Expr {
            if (pos >= tokens.size) {
                throw CompileException("Unexpected end of expression.")
            }
            return when (val tok = tokens[pos]) {
                is Token.Num -> {
                    pos++
                    val normalizedText = if (tok.text.startsWith(".")) "0${tok.text}" else tok.text
                    val d = normalizedText.toDoubleOrNull()
                        ?: throw CompileException("Invalid number '${tok.text}'.")
                    val bd = try {
                        BigDecimal(normalizedText, MathContext.DECIMAL128)
                    } catch (_: NumberFormatException) {
                        null
                    }
                    Expr.Const(
                        value = d,
                        rational = Rational.parseDecimal(normalizedText),
                        decimal = bd,
                        fromDecimalLiteral = normalizedText.contains('.')
                    )
                }
                is Token.Variable -> {
                    pos++
                    Expr.Var(tok.name)
                }
                is Token.Embedded -> {
                    pos++
                    tok.expr
                }
                is Token.LParen -> {
                    pos++
                    val inner = parseExpression()
                    if (pos >= tokens.size || tokens[pos] !is Token.RParen) {
                        throw CompileException("Missing closing parenthesis ')'.")
                    }
                    pos++
                    inner
                }
                is Token.RParen -> throw CompileException("Unexpected closing parenthesis ')'.")
                is Token.Op -> throw CompileException("Unexpected operator '${tok.op}'.")
            }
        }
    }

    private fun formatNodeList(nodes: List<MathNode>): String =
        MathSlot(nodes = nodes).toReadableString()
}

fun Expr.containsDecimalLiteral(): Boolean = when (this) {
    is Expr.Const -> fromDecimalLiteral
    is Expr.Var -> false
    is Expr.Neg -> inner.containsDecimalLiteral()
    is Expr.Add -> left.containsDecimalLiteral() || right.containsDecimalLiteral()
    is Expr.Sub -> left.containsDecimalLiteral() || right.containsDecimalLiteral()
    is Expr.Mul -> left.containsDecimalLiteral() || right.containsDecimalLiteral()
    is Expr.Div -> left.containsDecimalLiteral() || right.containsDecimalLiteral()
    is Expr.Pow -> base.containsDecimalLiteral() || exp.containsDecimalLiteral()
    is Expr.Log -> base.containsDecimalLiteral() || arg.containsDecimalLiteral()
    is Expr.Root -> (degree?.containsDecimalLiteral() ?: false) || radicand.containsDecimalLiteral()
}

fun Expr.variables(): Set<String> {
    val set = linkedSetOf<String>()
    fun walk(e: Expr) {
        when (e) {
            is Expr.Const -> Unit
            is Expr.Var -> set.add(e.name)
            is Expr.Neg -> walk(e.inner)
            is Expr.Add -> { walk(e.left); walk(e.right) }
            is Expr.Sub -> { walk(e.left); walk(e.right) }
            is Expr.Mul -> { walk(e.left); walk(e.right) }
            is Expr.Div -> { walk(e.left); walk(e.right) }
            is Expr.Pow -> { walk(e.base); walk(e.exp) }
            is Expr.Log -> { walk(e.base); walk(e.arg) }
            is Expr.Root -> { e.degree?.let(::walk); walk(e.radicand) }
        }
    }
    walk(this)
    return set
}

fun Expr.countVarOccurrences(varName: String): Int = when (this) {
    is Expr.Const -> 0
    is Expr.Var -> if (name == varName) 1 else 0
    is Expr.Neg -> inner.countVarOccurrences(varName)
    is Expr.Add -> left.countVarOccurrences(varName) + right.countVarOccurrences(varName)
    is Expr.Sub -> left.countVarOccurrences(varName) + right.countVarOccurrences(varName)
    is Expr.Mul -> left.countVarOccurrences(varName) + right.countVarOccurrences(varName)
    is Expr.Div -> left.countVarOccurrences(varName) + right.countVarOccurrences(varName)
    is Expr.Pow -> base.countVarOccurrences(varName) + exp.countVarOccurrences(varName)
    is Expr.Log -> base.countVarOccurrences(varName) + arg.countVarOccurrences(varName)
    is Expr.Root -> (degree?.countVarOccurrences(varName) ?: 0) + radicand.countVarOccurrences(varName)
}

fun Expr.evaluateDouble(env: Map<String, Double> = emptyMap()): Double {
    return when (this) {
        is Expr.Const -> value
        is Expr.Var -> env[name] ?: throw IllegalArgumentException("Unbound variable $name")
        is Expr.Neg -> -inner.evaluateDouble(env)
        is Expr.Add -> left.evaluateDouble(env) + right.evaluateDouble(env)
        is Expr.Sub -> left.evaluateDouble(env) - right.evaluateDouble(env)
        is Expr.Mul -> left.evaluateDouble(env) * right.evaluateDouble(env)
        is Expr.Div -> {
            val d = right.evaluateDouble(env)
            if (abs(d) < 1e-15) Double.NaN else left.evaluateDouble(env) / d
        }
        is Expr.Pow -> {
            val b = base.evaluateDouble(env)
            val e = exp.evaluateDouble(env)
            if (b < 0.0 && abs(e - e.roundToLong()) < 1e-12) {
                val k = e.roundToLong()
                val res = abs(b).pow(k.toDouble())
                if (k % 2L == 0L) res else -res
            } else {
                b.pow(e)
            }
        }
        is Expr.Log -> {
            val b = base.evaluateDouble(env)
            val a = arg.evaluateDouble(env)
            if (b <= 0.0 || abs(b - 1.0) < 1e-12 || a <= 0.0) Double.NaN
            else ln(a) / ln(b)
        }
        is Expr.Root -> {
            val n = degree?.evaluateDouble(env) ?: 2.0
            val r = radicand.evaluateDouble(env)
            if (abs(n) < 1e-12) Double.NaN
            else {
                val nInt = n.roundToLong()
                val isInt = abs(n - nInt) < 1e-12 && nInt >= 1L
                if (r < 0.0) {
                    if (isInt && nInt % 2L == 1L) {
                        -((-r).pow(1.0 / nInt.toDouble()))
                    } else {
                        Double.NaN
                    }
                } else {
                    if (abs(n - 2.0) < 1e-12) sqrt(r) else r.pow(1.0 / n)
                }
            }
        }
    }
}

fun Expr.toMathText(): String = when (this) {
    is Expr.Const -> symbolName ?: decimal?.stripTrailingZeros()?.toPlainString() ?: rational?.format() ?: formatNumber(value)
    is Expr.Var -> name
    is Expr.Neg -> "-${wrapIfCompound(inner)}"
    is Expr.Add -> "${left.toMathText()} + ${right.toMathText()}"
    is Expr.Sub -> "${left.toMathText()} − ${wrapIfAddSub(right)}"
    is Expr.Mul -> "${wrapIfAddSub(left)} × ${wrapIfAddSub(right)}"
    is Expr.Div -> "${wrapIfCompound(left)} / ${wrapIfCompound(right)}"
    is Expr.Pow -> "${wrapIfCompound(base)}^(${exp.toMathText()})"
    is Expr.Log -> "log_${base.toMathText()}(${arg.toMathText()})"
    is Expr.Root -> if (degree == null) "√(${radicand.toMathText()})" else "${degree.toMathText()}√(${radicand.toMathText()})"
}

private fun wrapIfAddSub(e: Expr): String = when (e) {
    is Expr.Add, is Expr.Sub -> "(${e.toMathText()})"
    else -> e.toMathText()
}

private fun wrapIfCompound(e: Expr): String = when (e) {
    is Expr.Const, is Expr.Var -> e.toMathText()
    else -> "(${e.toMathText()})"
}

fun formatNumber(d: Double): String {
    if (d.isNaN()) return "Undefined"
    if (d.isInfinite()) return if (d > 0) "∞" else "-∞"
    val clean = if (abs(d) < 1e-11) 0.0 else d
    val roundedLong = clean.roundToLong()
    if (abs(clean - roundedLong.toDouble()) < 1e-10 && abs(clean) < 1e14) {
        return roundedLong.toString()
    }
    // Use BigDecimal.valueOf(clean) only after rounding tiny IEEE-754 noise at 10 decimal digits
    return BigDecimal.valueOf(clean)
        .round(MathContext(10))
        .stripTrailingZeros()
        .toPlainString()
}
