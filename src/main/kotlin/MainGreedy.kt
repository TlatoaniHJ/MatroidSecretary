package org.example

import java.io.File

fun main() {
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases.txt"), targetSize = n)
    println("num matroids = ${matroids.size}")

    val prevProgress = "" // File("progress.txt").readText()

    val nondecomposable = mutableListOf<Int>()
    for ((index, matroid) in matroids.withIndex()) {
        if (matroid.isDecomposable()) {
            println("matroid #$index is decomposable")
        } else if ("matroid #$index" in prevProgress) {
            println("matroid #$index already handled")
        } else {
            nondecomposable.add(index)
        }
    }

    // 1. Create a lock to synchronize console output
    val currWorking = mutableMapOf<Int, InProgressMatroid>()
    var remMatroids = nondecomposable.size
    // ANSI escape codes
    val green = "\u001B[32m"
    val magenta = "\u001B[35m"
    // val red = "\u001B[31m"
    val reset = "\u001B[0m"
    fun printCurrWorking(add: Int? = null, remove: Int? = null) {
        println("-".repeat(20))
        var numInProgress = currWorking.size
        for ((index, inProgress) in currWorking) {
            if (index == add) {
                print(green)
            } else if (index == remove) {
                numInProgress--
                print(magenta)
            }
            println("#$index (rank = ${inProgress.matroid.rank(inProgress.matroid.elements())}) | since ${formatTimestamp(inProgress.startTime)} | ${inProgress.variables} variables, ${inProgress.constraints} constraints [${inProgress.automorphisms} automorphisms]")
            if (index == add || index == remove) {
                print(reset)
            }
        }
        println("-".repeat(20))
        println("$remMatroids matroids remaining [ $numInProgress matroids in progress ]")
        println("-".repeat(20))
    }

    var removed = -1

    val hits = nondecomposable.map { index ->
                val matroid = matroids[index]
                val (variables, constraints) = assessMatroidQuietly(matroid)
                val numAutomorphisms = matroid.automorphisms().size

                val startTime = System.currentTimeMillis()
                currWorking[index] = InProgressMatroid(matroid, variables, constraints, startTime, numAutomorphisms)
                printCurrWorking(add = index, remove = removed)
                currWorking.remove(removed)


                println("matroid #$index = $matroid")
                println("num automorphisms = $numAutomorphisms")
                val greedyCompetitiveRatio = evaluateMatroidOnGreedy(matroid, listOf(3, 2, 4), 0.4099, ::println)
                println("greedy competitive ratio = $greedyCompetitiveRatio")

                var hitResult: HitResult? = null
                if (greedyCompetitiveRatio < 0.4099) {
                    println("HIT")
                    hitResult = HitResult(index, InProgressMatroid(matroid, variables, constraints, startTime, numAutomorphisms), greedyCompetitiveRatio)
                }

                repeat(5) { println() }

                // 3. Acquire the lock and print everything at once
                remMatroids--
                removed = index

                hitResult // Return the actual computation result to the list
    }.filterNotNull()

    println("Total hits: ${hits.size}")
    for ((index, progress, ratio) in hits) {
        println("#$index [rank = ${progress.matroid.rank()}] (greedy comp. ratio = $ratio)")
    }
}

data class HitResult(val index: Int, val inProgress: InProgressMatroid, val ratio: Double)