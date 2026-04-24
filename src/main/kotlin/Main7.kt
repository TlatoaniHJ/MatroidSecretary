package org.example

import java.io.File
import kotlin.math.abs

fun main() {
    val file = File("rank_7_matroids.txt")
    val ratios = mutableListOf<Double>()
    var hits = mutableListOf<Double>()
    for (line in file.readLines()) {
        val matroid = parseMatroid(line)
        println("matroid = ${matroid.bases}")
        println("solving with symmetry")
        val y = solveMatroid(matroid, 1)
        repeat(2) { println() }
        println("solving with more symmetry")
        val z = solveMatroid(matroid, 2)

        if (abs(y - z) > 0.0001) {
            println("competitive ratios different")
            return
        }
        ratios.add(y)
        if (y < 0.415) {
            println("HIT")
            hits.add(y)
        }
        repeat(5) { println() }
    }
    println("ratios = $ratios")
    println("hits = $hits")
}