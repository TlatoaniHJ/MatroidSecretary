package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

fun main() = runBlocking(Dispatchers.Default) {
    println("Running on architecture: ${System.getProperty("os.arch")}")
    val n = 9
    val k = 3
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n, targetRank = k)
    println("num matroids = ${matroids.size}")

    val prevProgress = extractDataStructured(File("matroids_9_3_competitive_ratios.txt"))//"" // File("progress.txt").readText()
    val greedyData = extractDataStructured(File("matroids_9_greedy_filter_3.txt"))

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()
    val concurrentSolves = Semaphore(12)
    val file = File("matroids_9_3_competitive_ratios_raw.txt")

    val withAutomorphisms = matroids.withIndex().map { (index, matroid) ->
        async {
            concurrentSolves.withPermit {
                val timer = Timer()
                if (index in prevProgress) {
                    println("matroid #$index already handled [${timer.lapSeconds()} seconds]")
                    null
                } else if (index in greedyData && greedyData[index]!! > .406) {
                    printMutex.withLock {
                        println("matroid #$index filtered out by greedy [${timer.lapSeconds()} seconds]")
                        //file.appendText("matroid #$index filtered out by greedy [${timer.lapSeconds()} seconds]")
                    }
                    null
                } else if (matroid.isDecomposable()) {
                    printMutex.withLock {
                        println("matroid #$index is decomposable [${timer.lapSeconds()} seconds]")
                        //file.appendText("matroid #$index is decomposable [${timer.lapSeconds()} seconds]\n")
                    }
                    null
                } else {
                    val numAutomorphisms = matroid.automorphisms().size
                    printMutex.withLock {
                        println("#$index has $numAutomorphisms automorphisms [${timer.lapSeconds()} seconds]")
                        //file.appendText("#$index has $numAutomorphisms automorphisms [${timer.lapSeconds()} seconds]\n")
                    }
                    Pair(index, numAutomorphisms)
                }
            }
        }
    }.awaitAll().filterNotNull().sortedByDescending { it.second }

    val currWorking = mutableMapOf<Int, InProgressMatroid2>()
    var remMatroids = withAutomorphisms.size
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
                print(blue)
            } else if (index == remove) {
                numInProgress--
                print(magenta)
            } else if (index == constructed) {
                print(green)
            }
            val lpStats = if (inProgress.variables == 0) "constructing LP" else "${inProgress.variables} variables, ${inProgress.constraints} constraints, ${inProgress.nonzeros} nonzeros"
            println("#$index (rank = ${inProgress.matroid.rank(inProgress.matroid.elements())}) | since ${formatTimestamp(inProgress.startTime)} | $lpStats [${inProgress.automorphisms} automorphisms]")
            if (index == add || index == constructed || index == remove) {
                print(reset)
            }
        }
        println("-".repeat(20))
        println("$remMatroids matroids remaining [ $numInProgress matroids in progress ]")
        println("-".repeat(20))
    }

// ... (keep everything above this exactly the same, down to the printMutex declaration)

    // 1. Create a Channel and load it in your strict, sorted order
    val workChannel = Channel<Pair<Int, Int>>(Channel.UNLIMITED)
    for (item in withAutomorphisms) {
        workChannel.trySend(item)
    }
    workChannel.close() // Signals to the workers that no more items are coming

    val numWorkers = 6

    // 2. Spawn exactly 12 long-living worker coroutines
    val hits = (1..numWorkers).map {
        async {
            val localHits = mutableListOf<Double>()

            // 3. Workers pull from the queue strictly in FIFO order
            for ((index, numAutomorphisms) in workChannel) {
                val matroid = matroids[index]

                val startTime = System.currentTimeMillis()
                printMutex.withLock {
                    currWorking[index] = InProgressMatroid2(matroid, 0, 0, 0, startTime, numAutomorphisms)
                    printCurrWorking(add = index)
                }

                val log = java.lang.StringBuilder()

                val lp = solveMatroidStep1Gurobi(matroid, 32, log)
                val variables = lp.getNumVariables()
                val constraints = lp.getNumConstraints()
                val nonzeros = lp.getNumNonZeros()

                log.appendLine("matroid #$index = $matroid")

                printMutex.withLock {
                    print(log.toString())
                    currWorking[index] = InProgressMatroid2(matroid, variables, constraints, nonzeros, startTime, numAutomorphisms)
                    printCurrWorking(constructed = index)
                }

                log.appendLine("solving with more symmetry")
                val z = solveMatroidStep2Gurobi(lp, log)

                if (z < 0.406) {
                    log.appendLine("HIT")
                    localHits.add(z) // Add hit to the worker's local list
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
            }

            localHits // Return this worker's collection of hits
        }
    }.awaitAll().flatten() // Combine all 12 worker lists into one master hit list

    println("Total hits: ${hits.size}")
    for (matroid in hits) {
        println(matroid)
    }
}

data class InProgressMatroid2(val matroid: Matroid<Int>, val variables: Int, val constraints: Int, val nonzeros: Int, val startTime: Long, val automorphisms: Int)
