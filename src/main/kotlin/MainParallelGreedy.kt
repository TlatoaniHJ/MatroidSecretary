package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

fun main() = runBlocking(Dispatchers.Default) {
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases.txt"), targetSize = n)
    println("num matroids = ${matroids.size}")

    //val prevProgress = extractDataStructured(File("matroids_8_greedy_filter.txt"))
    //val prevTrueProgress = extractDataStructured(File("matroids_8_competitive_ratios.txt"))

    val nondecomposable = mutableListOf<Int>()
    for ((index, matroid) in matroids.withIndex()) {
        /*if (index in prevProgress || index in prevTrueProgress) {
            println("matroid #$index already handled")
        }*/
        if (matroid.isDecomposable()) {
            println("matroid #$index is decomposable")
        } else {
            nondecomposable.add(index)
        }
    }
    //nondecomposable.sortBy { matroids[it].rank() }

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()
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
    val concurrentSolves = Semaphore(12)

    val file = File("matroids_8_optimized_greedy_filter_raw.txt")

    val hits = nondecomposable.map { index ->
        async {
            concurrentSolves.withPermit {
                val matroid = matroids[index]
                val (variables, constraints) = assessMatroidQuietly(matroid)
                val numAutomorphisms = matroid.automorphisms().size

                // 2. Create a local string builder for this specific execution
                val log = java.lang.StringBuilder()

                log.appendLine("matroid #$index = $matroid")
                log.appendLine("num automorphisms = $numAutomorphisms")

                val startTime = System.currentTimeMillis()
                printMutex.withLock {
                    currWorking[index] = InProgressMatroid(matroid, variables, constraints, startTime, numAutomorphisms)
                    printCurrWorking(add = index)
                }

                val greedyCompetitiveRatio: Double = evaluateMatroidOnGreedyOptimized(matroid, listOf(3, 2, 4), log::appendLine)
                log.appendLine("overall greedy competitive ratio = $greedyCompetitiveRatio")

                var hitResult: HitResult? = null
                if (greedyCompetitiveRatio < 0.4099) {
                    log.appendLine("HIT")
                    hitResult = HitResult(index, InProgressMatroid(matroid, variables, constraints, startTime, numAutomorphisms), greedyCompetitiveRatio)
                }

                // The equivalent of repeat(5) { println() }
                log.append("\n\n\n\n\n")

                // 3. Acquire the lock and print everything at once
                printMutex.withLock {
                    remMatroids--
                    printCurrWorking(remove = index)
                    currWorking.remove(index)
                    print(log.toString()) // Use print, not println, since we appended lines
                    file.appendText(log.toString())
                }

                hitResult // Return the actual computation result to the list
            }
        }
    }.awaitAll().filterNotNull()

    println("Total hits: ${hits.size}")
    for ((index, progress, ratio) in hits) {
        println("#$index [rank = ${progress.matroid.rank()}] (greedy comp. ratio = $ratio)")
    }
}