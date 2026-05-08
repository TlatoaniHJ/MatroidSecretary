package org.example

import java.io.File

fun main() {
    val n = 7
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("${matroids.size} matroids")
    for ((index, matroid) in matroids.withIndex()) {
        repeat(5) {
            println()
        }
        val k = matroid.rank()
        if (k != 1) {
            continue
        }
        println("matroid #$index = $matroid")
        val truncated = TruncatedMatroid(matroid, k - 1)
        val timer = Timer()
        val lp1 = solveMatroidStep1Gurobi(matroid, 32, StringBuilder())
        val ratio1 = solveMatroidStep2Gurobi(lp1, StringBuilder())
        println("ratio of original = $ratio1 [${timer.lapSeconds()}]")
        continue
        val lp2 = solveMatroidStep1Gurobi(truncated, 32, StringBuilder())
        val ratio2 = solveMatroidStep2Gurobi(lp2, StringBuilder())
        println("ratio of truncation = $ratio2 [${timer.lapSeconds()}]")
        if (ratio2 - ratio1 > .000001) {
            println("COUNTEREXAMPLE")
            return
        }
    }
}
