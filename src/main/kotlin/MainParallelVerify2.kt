package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.random.Random

fun main() = runBlocking(Dispatchers.Default) {
    println("Running on architecture: ${System.getProperty("os.arch")}")
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("num matroids = ${matroids.size}")
    val random = Random(982342)
    val considered = matroids.indices.shuffled(random).subList(0, 6)

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()

    val currWorking = mutableMapOf<Pair<Int, Int>, InProgressMatroid4>()
    var remMatroids = 0
    // ANSI escape codes
    val green = "\u001B[32m"
    val blue = "\u001B[34m"
    val magenta = "\u001B[35m"
    // val red = "\u001B[31m"
    val reset = "\u001B[0m"
    fun printCurrWorking(add: Pair<Int, Int>? = null, constructed: Pair<Int, Int>? = null, remove: Pair<Int, Int>? = null) {
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
            println("<method = ${inProgress.method}> #${index.first} (rank = ${inProgress.matroid.rank(inProgress.matroid.elements())}) | since ${formatTimestamp(inProgress.startTime)} | $lpStats [${inProgress.automorphisms} automorphisms]")
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
    for (index in considered) {
        for (method in listOf(32, 42, 52)) {
            workChannel.send(Pair(index, method))
            remMatroids++
        }
    }
    workChannel.close() // Signals to the workers that no more items are coming

    val numWorkers = 6

    // 2. Spawn exactly 12 long-living worker coroutines
    (1..numWorkers).map {
        async {
            val localHits = mutableListOf<Double>()

            // 3. Workers pull from the queue strictly in FIFO order
            for ((index, method) in workChannel) {
                val matroid = matroids[index]
                val numAutomorphisms = matroid.automorphisms().size

                val startTime = System.currentTimeMillis()
                val log = java.lang.StringBuilder()

                log.appendLine("matroid #$index = $matroid")
                printMutex.withLock {
                    currWorking[Pair(index, method)] = InProgressMatroid4(matroid, 0, 0, 0, startTime, numAutomorphisms, method)
                    printCurrWorking(add = Pair(index, method))
                }

                val lp = solveMatroidStep1Gurobi(matroid, method, log, logFileName = "matroid_${index}_method_$method")
                val variables = lp.getNumVariables()
                val constraints = lp.getNumConstraints()
                val nonzeros = lp.getNumNonZeros()

                printMutex.withLock {
                    //print(log.toString())
                    currWorking[Pair(index, method)] =
                        InProgressMatroid4(matroid, variables, constraints, nonzeros, startTime, numAutomorphisms, method)
                    printCurrWorking(constructed = Pair(index, method))
                }

                log.appendLine("solving with method $method")
                solveMatroidStep2Gurobi(lp, log)
                log.append("\n\n\n\n\n")
                printMutex.withLock {
                    remMatroids--
                    printCurrWorking(remove = Pair(index, method))
                    currWorking.remove(Pair(index, method))
                    print(log.toString())

                    // Note: Consider wrapping this file append in a Mutex if you see interleaved text!
                }
            }

            localHits // Return this worker's collection of hits
        }
    }.awaitAll()

    Unit
}

data class InProgressMatroid4(val matroid: Matroid<Int>, val variables: Int, val constraints: Int, val nonzeros: Int, val startTime: Long, val automorphisms: Int, val method: Int)
