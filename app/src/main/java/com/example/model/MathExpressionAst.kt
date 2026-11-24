package com.example.model

import java.util.UUID

enum class MathTemplate {
    POWER,       // []^[]
    SQUARE,      // []^2
    CUBE,        // []^3
    TEN_POWER,   // 10^[]
    LOG_ANY,     // log_[] []
    LOG_10,      // log_10 []
    LOG_E,       // log_e []
    SQRT,        // \sqrt{[]}
    CUBE_ROOT,   // \sqrt[3]{[]}
    NTH_ROOT,    // \[]roott{[]}
    FRACTION     // []/[]
}

sealed interface MathNode {
    val id: String
}

data class SymbolNode(
    override val id: String = newNodeId(),
    val value: String
) : MathNode

data class PowerNode(
    override val id: String = newNodeId(),
    val base: MathSlot = MathSlot(),
    val exponent: MathSlot = MathSlot()
) : MathNode

data class LogNode(
    override val id: String = newNodeId(),
    val base: MathSlot = MathSlot(),
    val argument: MathSlot = MathSlot()
) : MathNode

data class RootNode(
    override val id: String = newNodeId(),
    val degree: MathSlot? = null,
    val radicand: MathSlot = MathSlot()
) : MathNode

data class FractionNode(
    override val id: String = newNodeId(),
    val numerator: MathSlot = MathSlot(),
    val denominator: MathSlot = MathSlot()
) : MathNode

data class MathSlot(
    val id: String = newNodeId(),
    val nodes: List<MathNode> = emptyList()
)

data class EquationEntry(
    val id: String = newNodeId(),
    val root: MathSlot = MathSlot()
)

data class EditorCursor(
    val equationIndex: Int,
    val slotId: String,
    val indexInSlot: Int
)

data class CursorStop(
    val slotId: String,
    val indexInSlot: Int
)

fun newNodeId(): String = UUID.randomUUID().toString()

fun MathNode.childSlots(): List<MathSlot> = when (this) {
    is SymbolNode -> emptyList()
    is PowerNode -> listOf(base, exponent)
    is LogNode -> listOf(base, argument)
    is RootNode -> listOfNotNull(degree, radicand)
    is FractionNode -> listOf(numerator, denominator)
}

fun MathSlot.findSlot(targetSlotId: String): MathSlot? {
    if (id == targetSlotId) return this
    for (node in nodes) {
        for (child in node.childSlots()) {
            child.findSlot(targetSlotId)?.let { return it }
        }
    }
    return null
}

fun MathSlot.updateSlot(targetSlotId: String, transform: (MathSlot) -> MathSlot): MathSlot {
    if (id == targetSlotId) return transform(this)
    var changed = false
    val updatedNodes = nodes.map { node ->
        val updatedNode = when (node) {
            is SymbolNode -> node
            is PowerNode -> {
                val b = node.base.updateSlot(targetSlotId, transform)
                val e = node.exponent.updateSlot(targetSlotId, transform)
                if (b !== node.base || e !== node.exponent) node.copy(base = b, exponent = e) else node
            }
            is LogNode -> {
                val b = node.base.updateSlot(targetSlotId, transform)
                val a = node.argument.updateSlot(targetSlotId, transform)
                if (b !== node.base || a !== node.argument) node.copy(base = b, argument = a) else node
            }
            is RootNode -> {
                val d = node.degree?.updateSlot(targetSlotId, transform)
                val r = node.radicand.updateSlot(targetSlotId, transform)
                if (d !== node.degree || r !== node.radicand) node.copy(degree = d, radicand = r) else node
            }
            is FractionNode -> {
                val n = node.numerator.updateSlot(targetSlotId, transform)
                val d = node.denominator.updateSlot(targetSlotId, transform)
                if (n !== node.numerator || d !== node.denominator) node.copy(numerator = n, denominator = d) else node
            }
        }
        if (updatedNode !== node) changed = true
        updatedNode
    }
    return if (changed) copy(nodes = updatedNodes) else this
}

fun MathSlot.collectAllSlotsInOrder(): List<MathSlot> {
    val result = mutableListOf(this)
    for (node in nodes) {
        for (child in node.childSlots()) {
            result.addAll(child.collectAllSlotsInOrder())
        }
    }
    return result
}

fun MathSlot.collectCursorStops(): List<CursorStop> {
    val stops = mutableListOf<CursorStop>()
    stops.add(CursorStop(id, 0))
    for (i in nodes.indices) {
        val node = nodes[i]
        for (child in node.childSlots()) {
            stops.addAll(child.collectCursorStops())
        }
        stops.add(CursorStop(id, i + 1))
    }
    return stops
}

fun MathSlot.findFirstEmptySlotId(): String? {
    if (nodes.isEmpty()) return id
    for (node in nodes) {
        for (child in node.childSlots()) {
            child.findFirstEmptySlotId()?.let { return it }
        }
    }
    return null
}

fun MathSlot.findEmptySlotIds(): List<String> {
    val result = mutableListOf<String>()
    if (nodes.isEmpty()) {
        result.add(id)
    }
    for (node in nodes) {
        for (child in node.childSlots()) {
            result.addAll(child.findEmptySlotIds())
        }
    }
    return result
}

fun MathSlot.containsAnyEmptyChildSlot(): Boolean {
    for (node in nodes) {
        for (child in node.childSlots()) {
            if (child.nodes.isEmpty() || child.containsAnyEmptyChildSlot()) {
                return true
            }
        }
    }
    return false
}

fun MathSlot.extractVariables(): Set<String> {
    val vars = linkedSetOf<String>()
    fun walk(slot: MathSlot) {
        for (node in slot.nodes) {
            when (node) {
                is SymbolNode -> if (node.value in setOf("x", "y", "z")) vars.add(node.value)
                else -> node.childSlots().forEach(::walk)
            }
        }
    }
    walk(this)
    return listOf("x", "y", "z").filter { it in vars }.toCollection(linkedSetOf())
}

fun MathSlot.isBlank(): Boolean = nodes.isEmpty()

fun MathSlot.containsEquals(): Boolean =
    nodes.any { it is SymbolNode && it.value == "=" }

fun MathNode.areAllChildrenEmpty(): Boolean =
    childSlots().all { it.nodes.isEmpty() }

fun MathSlot.findParentOfSlot(targetSlotId: String): Pair<MathSlot, Int>? {
    for (i in nodes.indices) {
        val node = nodes[i]
        for (child in node.childSlots()) {
            if (child.id == targetSlotId) return this to i
            child.findParentOfSlot(targetSlotId)?.let { return it }
        }
    }
    return null
}

fun MathSlot.toReadableString(): String {
    if (nodes.isEmpty()) return "□"
    return buildString {
        for (node in nodes) {
            when (node) {
                is SymbolNode -> append(
                    when (node.value) {
                        "*" -> "×"
                        "/" -> "÷"
                        else -> node.value
                    }
                )
                is PowerNode -> {
                    val b = node.base.toReadableString()
                    val e = node.exponent.toReadableString()
                    val wrappedBase = if (node.base.nodes.size > 1) "($b)" else b
                    if (e == "2") append("$wrappedBase²")
                    else if (e == "3") append("$wrappedBase³")
                    else append("$wrappedBase^($e)")
                }
                is LogNode -> {
                    val b = node.base.toReadableString()
                    val a = node.argument.toReadableString()
                    append("log_$b($a)")
                }
                is RootNode -> {
                    val r = node.radicand.toReadableString()
                    if (node.degree == null) {
                        append("√($r)")
                    } else {
                        append("${node.degree.toReadableString()}√($r)")
                    }
                }
                is FractionNode -> {
                    val n = node.numerator.toReadableString()
                    val d = node.denominator.toReadableString()
                    val nStr = if (node.numerator.nodes.size > 1) "($n)" else n
                    val dStr = if (node.denominator.nodes.size > 1) "($d)" else d
                    append("$nStr/$dStr")
                }
            }
        }
    }
}

fun createTemplateNode(template: MathTemplate): Pair<MathNode, String> {
    return when (template) {
        MathTemplate.POWER -> {
            val baseSlot = MathSlot()
            val expSlot = MathSlot()
            PowerNode(base = baseSlot, exponent = expSlot) to baseSlot.id
        }
        MathTemplate.SQUARE -> {
            val baseSlot = MathSlot()
            val expSlot = MathSlot(nodes = listOf(SymbolNode(value = "2")))
            PowerNode(base = baseSlot, exponent = expSlot) to baseSlot.id
        }
        MathTemplate.CUBE -> {
            val baseSlot = MathSlot()
            val expSlot = MathSlot(nodes = listOf(SymbolNode(value = "3")))
            PowerNode(base = baseSlot, exponent = expSlot) to baseSlot.id
        }
        MathTemplate.TEN_POWER -> {
            val baseSlot = MathSlot(nodes = listOf(SymbolNode(value = "1"), SymbolNode(value = "0")))
            val expSlot = MathSlot()
            PowerNode(base = baseSlot, exponent = expSlot) to expSlot.id
        }
        MathTemplate.LOG_ANY -> {
            val baseSlot = MathSlot()
            val argSlot = MathSlot()
            LogNode(base = baseSlot, argument = argSlot) to baseSlot.id
        }
        MathTemplate.LOG_10 -> {
            val baseSlot = MathSlot(nodes = listOf(SymbolNode(value = "1"), SymbolNode(value = "0")))
            val argSlot = MathSlot()
            LogNode(base = baseSlot, argument = argSlot) to argSlot.id
        }
        MathTemplate.LOG_E -> {
            val baseSlot = MathSlot(nodes = listOf(SymbolNode(value = "e")))
            val argSlot = MathSlot()
            LogNode(base = baseSlot, argument = argSlot) to argSlot.id
        }
        MathTemplate.SQRT -> {
            val radSlot = MathSlot()
            RootNode(degree = null, radicand = radSlot) to radSlot.id
        }
        MathTemplate.CUBE_ROOT -> {
            val degSlot = MathSlot(nodes = listOf(SymbolNode(value = "3")))
            val radSlot = MathSlot()
            RootNode(degree = degSlot, radicand = radSlot) to radSlot.id
        }
        MathTemplate.NTH_ROOT -> {
            val degSlot = MathSlot()
            val radSlot = MathSlot()
            RootNode(degree = degSlot, radicand = radSlot) to degSlot.id
        }
        MathTemplate.FRACTION -> {
            val numSlot = MathSlot()
            val denSlot = MathSlot()
            FractionNode(numerator = numSlot, denominator = denSlot) to numSlot.id
        }
    }
}
