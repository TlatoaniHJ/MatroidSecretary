package org.example

import java.io.File

fun main() {
    println("Hello World!")
    println(listOf(1, 2, 3, 4) - 3)

    //val matroid = uniformMatroid((1..7).toSet(), 7)

    val file = File("rank_7_matroids.txt")
    for (line in file.readLines()) {
        val matroid = parseMatroid(line)
        println("matroid = ${matroid.bases}")
        val dummy = DummyLPBuilder()

        buildMatroidSecretaryLP(matroid, dummy)

        println("num variables = ${dummy.numVariables}")
        println("num constraints = ${dummy.numConstraints}")
        println()
    }
}

class DummyLPBuilder: LinearProgramBuilder<Unit> {
    var numVariables = 0
    var numConstraints = 0

    override fun newVariableRaw(type: VariableType) {
        numVariables++
    }

    override fun newConstraint(
        left: Expression<Unit>,
        type: ConstraintType,
        right: Expression<Unit>
    ) {
        numConstraints++
    }

    override fun optimize(mode: OptimizationMode, expression: Expression<Unit>) {

    }
}

fun <E> uniformMatroid(elements: Collection<E>, rank: Int) = MatroidByBases(elements.toSet(), subsets(elements.toSet()).filter { it.size == rank })

fun parseMatroid(string: String): MatroidByBases<Int> {
    val elements = mutableSetOf<Int>()
    val bases = mutableListOf<Set<Int>>()
    for (basisString in string.substring(1, string.lastIndex).split("], [")) {
        val basis = basisString.split(", ").map(String::toInt).toSet()
        elements.addAll(basis)
        bases.add(basis)
    }
    return MatroidByBases<Int>(elements, bases)
}