package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import kotlin.random.Random

fun main() = runBlocking(Dispatchers.Default) {
    println("Running on architecture: ${System.getProperty("os.arch")}")
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("num matroids = ${matroids.size}")

    val random = Random(23423)
    val considered = matroids.indices.shuffled(random).subList(0, 6)
    /*for ((index, matroid) in matroids.withIndex()) {
        if (matroid.isDecomposable()) {
            println("matroid #$index is decomposable")
            if (matroid.rank() != 0) {
                considered.add(index)
            }
        } else {
            considered.add(index)
        }
    }*/

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()
    val currWorking = mutableMapOf<Int, InProgressMatroid>()
    var remMatroids = considered.size
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

    val hits = considered.map { index ->
        async {
            concurrentSolves.withPermit {
                val matroid = matroids[index]

                // 2. Create a local string builder for this specific execution
                val log = java.lang.StringBuilder()

                log.appendLine("solving with stopgap")
                val lpBaseline = solveMatroidStep1Gurobi(matroid, 32, log, logFileName = "matroid_${index}_method_32")
                val variables = lpBaseline.getNumVariables()
                val constraints = lpBaseline.getNumConstraints()
                val numAutomorphisms = matroid.automorphisms().size

                log.appendLine("matroid #$index = $matroid")

                printMutex.withLock {
                    print(log.toString())
                    currWorking[index] = InProgressMatroid(matroid, variables, constraints, System.currentTimeMillis(), numAutomorphisms)
                    printCurrWorking(add = index)
                }
                val x = solveMatroidStep2Gurobi(lpBaseline, log)
                log.append("\n\n\n\n\n")
                log.appendLine("solving with prefix sums")
                val lpSymmetry = solveMatroidStep1Gurobi(matroid, 42, log, logFileName = "matroid_${index}_method_42")
                val y = solveMatroidStep2Gurobi(lpSymmetry, log)
                log.append("\n\n\n\n\n")
                log.appendLine("solving with subset sums")
                val lpSparse = solveMatroidStep1Gurobi(matroid, 52, log, logFileName = "matroid_${index}_method_52")
                val z = solveMatroidStep2Gurobi(lpSparse, log)
                // The equivalent of repeat(5) { println() }
                log.append("\n\n\n\n\n")
                log.appendLine("competitive ratios = $x, $y, $z")
                if (maxOf(x, y, z) - minOf(x, y, z) > .0000001) {
                    log.appendLine("FAIL")
                    return@withPermit Pair(index, listOf(x, y, z))
                }

                // 3. Acquire the lock and print everything at once
                printMutex.withLock {
                    remMatroids--
                    printCurrWorking(remove = index)
                    currWorking.remove(index)
                    print(log.toString()) // Use print, not println, since we appended lines
                }
                //file.appendText(log.toString())

                null
            }
        }
    }.awaitAll().filterNotNull()

    println("Total failures: ${hits.size}")
    for ((matroid, ratios) in hits) {
        println("#$matroid: ratios = $ratios")
    }
}