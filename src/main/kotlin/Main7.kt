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
        val y = solveMatroid(matroid, 2)
        repeat(2) { println() }
        println("solving with more symmetry")
        val z = solveMatroid(matroid, 3)

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

fun main8() {
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases.txt"), targetSize = n)
    println("num matroids = ${matroids.size}")
    var hits = mutableListOf<Double>()
    for ((index, matroid) in matroids.withIndex()) {
        println("matroid #$index = $matroid")
        if (matroid.rank(matroid.elements()) == 0) {
            continue
        }

        /*val greedyCompetitiveRatio = evaluateMatroidOnGreedy(matroid, 2..4)
        repeat(2) { println() }
        println("overall greedy competitive ratio = $greedyCompetitiveRatio")
        if (greedyCompetitiveRatio < .4099) {
            println("GREEDY INSUFFICIENT")
            repeat(2) { println() }*/
            /*println("solving with symmetry")
            val y = solveMatroid(matroid, 1)
            repeat(2) { println() }*/
            println("solving with more symmetry")
            val z = solveMatroid(matroid, 2)

            /*if (abs(y - z) > 0.0001) {
                println("competitive ratios different")
                return
            }*/
            if (z < 0.4099) {
                println("HIT")
                hits.add(z)
            }
        //}

        repeat(5) { println() }
    }
}