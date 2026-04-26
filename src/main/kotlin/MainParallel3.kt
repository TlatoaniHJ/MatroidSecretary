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

    val prevProgress = "" // File("progress.txt").readText()

    val withAutomorphisms = mutableListOf<Pair<Int, Int>>()
    for ((index, matroid) in matroids.withIndex()) {
        if (matroid.isDecomposable()) {
            println("matroid #$index is decomposable")
        } else if ("matroid #$index" in prevProgress) {
            println("matroid #$index already handled")
        } else {
            //val numAutomorphisms = bijections(matroid.elements()).count { isAutomorphism(matroid, it) }
            val numAutomorphisms = matroid.automorphisms().size
            println("#$index has $numAutomorphisms automorphisms")
            withAutomorphisms.add(Pair(index, numAutomorphisms))
        }
    }
    withAutomorphisms.sortByDescending { it.second }

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()
    val currWorking = mutableMapOf<Int, InProgressMatroid>()
    var remMatroids = withAutomorphisms.size
    // ANSI escape codes
    val green = "\u001B[32m"
    val magenta = "\u001B[35m"
    // val red = "\u001B[31m"
    val reset = "\u001B[0m"
    fun printCurrWorking(add: Int? = null, remove: Int? = null) {
        println("-".repeat(20))
        for ((index, inProgress) in currWorking) {
            if (index == add) {
                print(green)
            } else if (index == remove) {
                print(magenta)
            }
            println("#$index (rank = ${inProgress.matroid.rank(inProgress.matroid.elements())}) | since ${formatTimestamp(inProgress.startTime)} | ${inProgress.variables} variables, ${inProgress.constraints} constraints [${inProgress.automorphisms} automorphisms]")
            if (index == add || index == remove) {
                print(reset)
            }
        }
        println("-".repeat(20))
        println("$remMatroids matroids remaining [ ${currWorking.size} matroids in progress ]")
        println("-".repeat(20))
    }
    val concurrentSolves = Semaphore(12)

    val hits = withAutomorphisms.map { (index, numAutomorphisms) ->
        async {
            concurrentSolves.withPermit {
                val matroid = matroids[index]
                val (variables, constraints) = assessMatroidQuietly(matroid)

                // 2. Create a local string builder for this specific execution
                val log = java.lang.StringBuilder()

                log.appendLine("matroid #$index = $matroid")

                val startTime = System.currentTimeMillis()
                printMutex.withLock {
                    currWorking[index] = InProgressMatroid(matroid, variables, constraints, startTime, numAutomorphisms)
                    printCurrWorking(add = index)
                }

                log.appendLine("solving with more symmetry")
                val z = solveMatroidWithLog(matroid, 2, log)

                var hitResult: Double? = null
                if (z < 0.4099) {
                    log.appendLine("HIT")
                    hitResult = z
                }

                // The equivalent of repeat(5) { println() }
                log.append("\n\n\n\n\n")

                // 3. Acquire the lock and print everything at once
                printMutex.withLock {
                    remMatroids--
                    printCurrWorking(remove = index)
                    currWorking.remove(index)
                    print(log.toString()) // Use print, not println, since we appended lines
                }

                hitResult // Return the actual computation result to the list
            }
        }
    }.awaitAll().filterNotNull()

    println("Total hits: ${hits.size}")
    for (matroid in hits) {
        println(matroid)
    }
}