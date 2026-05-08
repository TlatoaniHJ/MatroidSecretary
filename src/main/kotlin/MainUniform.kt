package org.example

import java.io.File
import java.text.DecimalFormat

fun main() {
    uniformData(7, 42)
    /*println()
    uniformData(7, 101)*/
    //checkFullRank()
}

fun uniformData(nLimit: Int, mode: Int) {
    val format = DecimalFormat("0.00000000")
    for (n in 1..nLimit) {
        val ratios = (1..n).map { k ->
            val matroid = uniformMatroid((1..n).toSet(), k)
            solveMatroidSimpleGurobi(matroid, mode = mode)
        }
        println(ratios.joinToString(separator = "\t") { format.format(it) })
    }
}

fun checkFullRank() {
    val n = 7
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("num matroids = ${matroids.size}")

    for ((index, matroid) in matroids.withIndex()) {
        if (index == 0) {
            continue
        }
        println()

        println("matroid #$index [rank ${matroid.rank()}] = $matroid")
        val ratio = solveMatroidSimpleGurobi(matroid)
        val fullRankRatio = solveMatroidSimpleGurobi(matroid, mode = 101)
        println("\tratio           = $ratio")
        println("\tfull rank ratio = $fullRankRatio")
        if (matroid.rank() > 1) {
            val truncated = TruncatedMatroid(matroid, matroid.rank() - 1)
            val truncatedRatio = solveMatroidSimpleGurobi(truncated)
            val truncatedFullRankRatio = solveMatroidSimpleGurobi(truncated, mode = 101)
            println("\ttruncated ratio = $truncatedRatio")
            println("\ttruncated full rank ratio = $truncatedFullRankRatio")
            if (truncatedFullRankRatio > fullRankRatio + .000001) {
                return
            }
        }
    }
}