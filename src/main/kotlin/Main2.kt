package org.example

fun main() {
    val matroids = mutableListOf<Matroid<Int>>()
    val counterexamples = mutableListOf<Matroid<Int>>()

    val n = 6
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

                if (maxOf(x, y, z) - minOf(x, y, z) > 0.0001) {
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
    }
}