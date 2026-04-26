package org.example

import com.google.ortools.linearsolver.MPSolver
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

fun main() {
    println("Hello World!")
    println(listOf(1, 2, 3, 4) - 3)

    val matroid = MatroidByBases((1..4).toSet(), listOf(setOf(1, 2), setOf(3, 2), setOf(1, 4), setOf(3, 4)))
    assessMatroid(matroid)

    /*for (k in 1..7) {
        println("uniform matroid of rank $k")
        val matroid = uniformMatroid((1..7).toSet(), k)
        assessMatroid(matroid)
    }*/

    /*val file = File("rank_7_matroids.txt")
    for (line in file.readLines()) {
        val matroid = parseMatroid(line)
        println("matroid = ${matroid.bases}")
        assessMatroid(matroid)
    }*/

    //val matroid = uniformMatroid((1..5).toSet(), 2)

    /*val matroids = mutableListOf<Matroid<Int>>()
    val counterexamples = mutableListOf<Matroid<Int>>()

    val n = 5
    val elements = (1..n).toSet()
    for (k in 1..n) {
        val subsetsOfSize = subsets(elements).filter { it.size == k }
        for (bases in subsets(subsetsOfSize.toSet())) {
            val matroid = MatroidByBases(elements, bases.toList())
            if (isMatroid(matroid) && !matroids.any { isomorphic(matroid, it) }) {
                matroids.add(matroid)
                println("matroid with bases $bases")
                println()
                println("solving without symmetry")
                val x = solveMatroid(matroid, 0)
                repeat(2) { println() }
                println("solving with symmetry")
                val y = solveMatroid(matroid, 1)
                repeat(2) { println() }
                println("solving with more symmetry")
                val z = solveMatroid(matroid, 2)
                repeat(2) { println() }
                println("solving with even more symmetry")
                val w = solveMatroid(matroid, 3)

                if (listOf(w, x, y, z).max() - listOf(w, x, y, z).min() > 0.0001) {
                    println("competitive ratios different")
                    counterexamples.add(matroid)
                    return
                }
                repeat(5) { println() }
            }
        }
    }
    println("all complete")
    println("total num matroids = ${matroids.size}")
    println("num counterexamples = ${counterexamples.size}")
    for (matroid in counterexamples) {
        println(matroid)
    }*/
}

fun <E> assessMatroid(matroid: Matroid<E>) {
    var dummy = DummyLPBuilder()

    buildMatroidSecretaryLPOld(matroid, dummy)
    println()

    println("old num variables = ${dummy.numVariables}")
    println("old num constraints = ${dummy.numConstraints}")
    println()

    dummy = DummyLPBuilder()
    buildMatroidSecretaryLP(matroid, dummy)
    println("new num variables = ${dummy.numVariables}")
    println("new num constraints = ${dummy.numConstraints}")
    println()

    dummy = DummyLPBuilder()
    buildMatroidSecretaryLP3(matroid, dummy)
    println("new 3 num variables = ${dummy.numVariables}")
    println("new 3 num constraints = ${dummy.numConstraints}")

    println()
    println("-".repeat(32))
    println()
}

fun <E> assessMatroidQuietly(matroid: Matroid<E>): Pair<Int, Int> {
    val dummy = DummyLPBuilder()
    buildMatroidSecretaryLP(matroid, dummy)
    return Pair(dummy.numVariables, dummy.numConstraints)
}

fun <E> solveMatroid(matroid: Matroid<E>, mode: Int): Double {
    var builder = OrToolsLinearProgramBuilder()

    when (mode) {
        0 -> buildMatroidSecretaryLPNoSymmetry(matroid, builder)
        1 -> buildMatroidSecretaryLPOld(matroid, builder)
        2 -> buildMatroidSecretaryLP(matroid, builder)
        3 -> buildMatroidSecretaryLP3(matroid, builder)
        else -> throw IllegalArgumentException("mode = $mode, should be 0, 1, 2")
    }

    println("num variables = ${builder.getNumVariables()}")
    println("num constraints = ${builder.getNumConstraints()}")

    // Solve the model
    val startTime = System.currentTimeMillis()
    val status = builder.solve()
    val endTime = System.currentTimeMillis()
    println("time taken = ${(endTime - startTime).toDouble() / 1000.0} seconds")

    if (status == MPSolver.ResultStatus.OPTIMAL) {
        println("Solution found!")
        println("Objective value = ${builder.getObjectiveValue()}")
        return builder.getObjectiveValue()
    } else {
        println("The problem does not have an optimal solution.")
        exitProcess(0)
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