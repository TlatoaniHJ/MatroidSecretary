package org.example

import kotlinx.coroutines.*
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

    //val prevProgress = extractDataStructured(File("matroids_9_3_competitive_ratios.txt"))//"" // File("progress.txt").readText()
    //val greedyData = extractDataStructured(File("matroids_8_greedy_filter_3.txt"))

    val withAutomorphisms = mutableListOf<Pair<Int, Int>>()
    for ((index, matroid) in matroids.withIndex()) {
        if (matroid.isDecomposable()) {
            println("matroid #$index is decomposable")
        }
        /*else if (index in prevProgress) {
            println("matroid #$index already computed")
        } /*else if (greedyData[index]!! > .406) {
            println("matroid #$index filtered by greedy")
        } */else*/
        /*if (index == 64)*/ else {
            //val numAutomorphisms = bijections(matroid.elements()).count { isAutomorphism(matroid, it) }
            val numAutomorphisms = matroid.automorphisms().size
            println("#$index has $numAutomorphisms automorphisms")
            withAutomorphisms.add(Pair(index, numAutomorphisms))
        }
    }
    withAutomorphisms.sortByDescending { it.second }
    withAutomorphisms.sortBy { matroids[it.first].rank() }

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

    val file = File("matroids_9_3_competitive_ratios_raw.txt")

    val hits = withAutomorphisms.map { (index, numAutomorphisms) ->
        async {
            concurrentSolves.withPermit {
                val matroid = matroids[index]

                // 2. Create a local string builder for this specific execution
                val log = java.lang.StringBuilder()

                val lp = solveMatroidStep1Gurobi(matroid, 32, log)
                val variables = lp.getNumVariables()
                val constraints = lp.getNumConstraints()

                log.appendLine("matroid #$index = $matroid")

                val startTime = System.currentTimeMillis()
                printMutex.withLock {
                    print(log.toString())
                    currWorking[index] = InProgressMatroid(matroid, variables, constraints, startTime, numAutomorphisms)
                    printCurrWorking(add = index)
                }

                log.appendLine("solving with more symmetry")
                val z = solveMatroidStep2Gurobi(lp, log)

                var hitResult: Double? = null
                if (z < 0.406) {
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
                file.appendText(log.toString())

                hitResult // Return the actual computation result to the list
            }
        }
    }.awaitAll().filterNotNull()

    println("Total hits: ${hits.size}")
    for (matroid in hits) {
        println(matroid)
    }
}