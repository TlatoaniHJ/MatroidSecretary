package org.example

import java.io.File

fun main() {
    val n = 7
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("num matroids = ${matroids.size}")
    
    for ((index, matroid) in matroids.withIndex()) {
        if (index == 0) {
            continue
        }
        println("\n".repeat(4))
        println("matroid #$index = $matroid")


        val log = StringBuilder()
        log.appendLine("solving with stopgap")
        val lpBaseline = solveMatroidStep1Gurobi(matroid, 32, log)
        val x = solveMatroidStep2Gurobi(lpBaseline, log)
        log.append("\n\n\n\n\n")
        log.appendLine("solving with prefix sums")
        val lpSymmetry = solveMatroidStep1Gurobi(matroid, 42, log)
        val y = solveMatroidStep2Gurobi(lpSymmetry, log)
        log.append("\n\n\n\n\n")
        log.appendLine("solving with subset sums")
        val lpSparse = solveMatroidStep1Gurobi(matroid, 52, log)
        val z = solveMatroidStep2Gurobi(lpSparse, log)
        // The equivalent of repeat(5) { println() }
        log.append("\n\n\n\n\n")
        log.appendLine("competitive ratios = $x, $y, $z")
        println(log)
        if (maxOf(x, y, z) - minOf(x, y, z) > .0000001) {
            println("FAIL")
            return
        }
    }
    println("success")
}