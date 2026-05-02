package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.min
import kotlin.system.exitProcess

fun main() = runBlocking(Dispatchers.Default) {
    println("Running on architecture: ${System.getProperty("os.arch")}")
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("num matroids = ${matroids.size}")

    val prevProgress = extractDataRaw(File("matroids_8_truncation_conjecture.txt").readText(), "original ratio = ")
    //val prevProgress = extractDataStructured(File("matroids_9_3_competitive_ratios.txt"))//"" // File("progress.txt").readText()
    //val greedyData = extractDataStructured(File("matroids_9_greedy_filter_3.txt"))

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()
    val file = File("matroids_8_truncation_conjecture.txt")

    val currWorking = mutableMapOf<Int, InProgressMatroid3>()
    var remMatroids = 0
    // ANSI escape codes
    val green = "\u001B[32m"
    val blue = "\u001B[34m"
    val magenta = "\u001B[35m"
    // val red = "\u001B[31m"
    val reset = "\u001B[0m"
    fun printCurrWorking(add: Int? = null, constructed: Int? = null, remove: Int? = null) {
        println("-".repeat(20))
        var numInProgress = currWorking.size
        for ((index, inProgress) in currWorking) {
            if (index == add) {
                print(green)
            } else if (index == remove) {
                numInProgress--
                print(magenta)
            } else if (index == constructed) {
                print(blue)
            }
            val lpStats = if (inProgress.variables == 0) "constructing LP" else "${inProgress.variables} variables, ${inProgress.constraints} constraints, ${inProgress.nonzeros} nonzeros"
            val truncationFlag = if (inProgress.truncation) "TRUNCATION" else "ORIGINAL  "
            println("$truncationFlag #$index (rank = ${inProgress.matroid.rank(inProgress.matroid.elements())}) | since ${formatTimestamp(inProgress.startTime)} | $lpStats [${inProgress.automorphisms} automorphisms]")
            if (index == add || index == constructed || index == remove) {
                print(reset)
            }
        }
        println("-".repeat(20))
        println("$remMatroids matroids remaining [ $numInProgress matroids in progress ]")
        println("-".repeat(20))
    }

    println("sorting by automorphisms...")
    val timer = Timer()
    val sorted = matroids.withIndex().map { (index, matroid) -> Pair(index, matroid.automorphisms().size) }.sortedByDescending { it.second }.sortedBy { matroids[it.first].rank() }
    println("sorted [${timer.lapSeconds()} seconds]")

// ... (keep everything above this exactly the same, down to the printMutex declaration)

    // 1. Create a Channel and load it in your strict, sorted order
    val workChannel = Channel<Pair<Int, Int>>(Channel.UNLIMITED)
    for ((index, numAutomorphisms) in sorted) {
        if (index in prevProgress) {
            println("ignoring #$index as already handled")
        } else if (matroids[index].rank() <= 1) {
            println("ignoring #$index as its rank is too small")
        } else {
            remMatroids++
            workChannel.trySend(Pair(index, numAutomorphisms))
        }
    }
    workChannel.close() // Signals to the workers that no more items are coming

    val numWorkers = 1

    // 2. Spawn exactly 12 long-living worker coroutines
    (1..numWorkers).map {
        async {
            val localHits = mutableListOf<Double>()

            // 3. Workers pull from the queue strictly in FIFO order
            for ((index, numAutomorphisms) in workChannel) {
                val matroid = matroids[index]
                val truncated = TruncatedMatroid(matroid, matroid.rank() - 1)

                val ratios = mutableListOf<Double>()

                val startTime = System.currentTimeMillis()
                val log = java.lang.StringBuilder()

                log.appendLine("matroid #$index = $matroid")
                for (truncation in listOf(false, true)) {
                    val numAutomorphisms = if (truncation) truncated.automorphisms().size else numAutomorphisms
                    printMutex.withLock {
                        currWorking[index] = InProgressMatroid3(matroid, 0, 0, 0, startTime, numAutomorphisms, truncation)
                        printCurrWorking(add = index)
                    }

                    if (!truncation) {
                        val decomposition = matroid.withoutLoops().decompose()
                        if (decomposition != null) {
                            val (matroid1, matroid2) = decomposition
                            val index1 = matroids.indexOfFirst { isomorphic(it.withoutLoops(), matroid1) }
                            val index2 = matroids.indexOfFirst { isomorphic(it.withoutLoops(), matroid2) }
                            val ratio1 = prevProgress[index1]!!
                            val ratio2 = prevProgress[index2]!!
                            log.appendLine("identified matroid as sum of #$index1 (ratio = $ratio1) and #$index2 (ratio = $ratio2)")
                            val ratio = min(ratio1, ratio2)
                            log.appendLine("concluding that ratio = $ratio")
                            ratios.add(ratio)
                            continue
                        }
                    }

                    val lp = solveMatroidStep1Gurobi(if (truncation) truncated else matroid, 42, log, logFileName = if (truncation) "truncated_$index" else "matroid_$index", threads = 0)
                    val variables = lp.getNumVariables()
                    val constraints = lp.getNumConstraints()
                    val nonzeros = lp.getNumNonZeros()

                    printMutex.withLock {
                        //print(log.toString())
                        currWorking[index] =
                            InProgressMatroid3(matroid, variables, constraints, nonzeros, startTime, numAutomorphisms, truncation)
                        printCurrWorking(constructed = index)
                    }

                    if (!truncation || nonzeros <= 300000) {
                        log.appendLine("solving with more symmetry")
                        ratios.add(solveMatroidStep2Gurobi(lp, log))
                    } else {
                        log.appendLine("truncated matroid LP too large, searching for previous calculation")
                        val truncatedIndex = matroids.indexOfFirst { isomorphic(it, truncated) }
                        log.appendLine("identified as #$truncatedIndex")
                        ratios.add(prevProgress[truncatedIndex]!!)
                    }
                }

                log.append("\n\n")
                log.appendLine("original ratio = ${ratios[0]}")
                log.appendLine("truncated ratio = ${ratios[1]}")
                if (ratios[0] < ratios[1] - .000001) {
                    log.appendLine("COUNTEREXAMPLE")
                }
                log.append("\n\n\n\n\n")
                printMutex.withLock {
                    remMatroids--
                    printCurrWorking(remove = index)
                    currWorking.remove(index)
                    print(log.toString())

                    // Note: Consider wrapping this file append in a Mutex if you see interleaved text!
                    file.appendText(log.toString())
                }
                if (ratios[0] < ratios[1] - .000001) {
                    println("COUNTEREXAMPLE")
                    exitProcess(0)
                }
            }

            localHits // Return this worker's collection of hits
        }
    }.awaitAll()

    Unit
}

data class InProgressMatroid3(val matroid: Matroid<Int>, val variables: Int, val constraints: Int, val nonzeros: Int, val startTime: Long, val automorphisms: Int, val truncation: Boolean)
