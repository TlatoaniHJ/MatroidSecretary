package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.min
import kotlin.system.exitProcess

fun main() = runBlocking(Dispatchers.Default) {
    val numWorkers = 13
    val numThreads = 1

    println("Running on architecture: ${System.getProperty("os.arch")}")
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("num matroids = ${matroids.size}")

    val salvage = mapOf(
        815 to 7.10169310e-01,
        804 to 7.09788146e-01,
        800 to 7.08672682e-01,
        778 to 6.79426270e-01,
        777 to 6.79426270e-01,
        810 to 6.81081730e-01,
        806 to 7.07689678e-01,
        816 to 7.09436352e-01,
        801 to 7.09804597e-01,
        817 to 6.81081731e-01,
        822 to 7.10354937e-01,
        839 to 7.10527787e-01,
        841 to 6.81081729e-01,
    )

    val prevProgress = extractDataRaw(File("matroids_${n}_truncation_conjecture.txt").readText(), "original ratio = ")

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()
    val file = File("matroids_8_truncation_salvage.txt")
    file.createNewFile()

    val currWorking = mutableMapOf<Int, InProgressMatroid3>()
    var remMatroids = 0
    var skipped = 0//prevSkipped.size
    // ANSI escape codes
    val green = "\u001B[32m"
    val greenHighIntensity = "\u001B[0;92m"
    val blue = "\u001B[34m"
    val blueHighIntensity = "\u001B[0;94m"
    val magenta = "\u001B[35m"
    val purpleHighIntensity = "\u001B[0;95m"
    val red = "\u001B[31m"
    val redHighIntensity = "\u001B[0;91m"
    val reset = "\u001B[0m"
    fun printCurrWorking(add: Int? = null, constructed: Int? = null, remove: Int? = null, skip: Int? = null) {
        println("-".repeat(20))
        var numInProgress = currWorking.size
        for ((index, inProgress) in currWorking) {
            if (index == add) {
                print(greenHighIntensity)
            } else if (index == remove) {
                numInProgress--
                print(purpleHighIntensity)
            } else if (index == constructed) {
                print(blueHighIntensity)
            } else if (index == skip) {
                numInProgress--
                print(redHighIntensity)
            }
            val lpStats = if (inProgress.variables == 0) "constructing LP" else "${inProgress.variables} variables, ${inProgress.constraints} constraints, ${inProgress.nonzeros} nonzeros"
            val truncationFlag = if (inProgress.truncation) "TRUNCATION" else "ORIGINAL  "
            println("$truncationFlag #$index (rank = ${inProgress.matroid.rank(inProgress.matroid.elements())}) | since ${formatTimestamp(inProgress.startTime)} | $lpStats [${inProgress.automorphisms} automorphisms]")
            if (index == add || index == constructed || index == remove || index == skip) {
                print(reset)
            }
        }
        println("-".repeat(20))
        println("$remMatroids matroids remaining [ $numInProgress matroids in progress ]")
        println("$skipped matroids skipped, ${remMatroids - skipped} nonskipped matroids remaining")
        println("-".repeat(20))
    }

    println("sorting by automorphisms...")
    val timer = Timer()
    var sorted = matroids.withIndex().map { (index, matroid) -> Pair(index, matroid.automorphisms().size) }.sortedByDescending { it.second }.sortedBy { matroids[it.first].rank() }
    //println("putting decomposable ones first...")
    //sorted = sorted.partition { (index, _) -> matroids[index].isDecomposable() }.let { (first, second) -> first.sortedBy { matroids[it.first].rank() } + second }
    println("sorted [${timer.lapSeconds()} seconds]")

// ... (keep everything above this exactly the same, down to the printMutex declaration)

    // 1. Create a Channel and load it in your strict, sorted order
    val workChannel = Channel<Pair<Int, Int>>(Channel.UNLIMITED)
    for (index in salvage.keys) {
        workChannel.trySend(Pair(index, matroids[index].automorphisms().size))
    }
    workChannel.close() // Signals to the workers that no more items are coming

    // 2. Spawn exactly 12 long-living worker coroutines
    (1..numWorkers).map {
        async {
            val localHits = mutableListOf<Double>()

            // 3. Workers pull from the queue strictly in FIFO order
            outer@for ((index, numAutomorphisms) in workChannel) {
                val startTime = System.currentTimeMillis()
                var matroid = matroids[index]
                val log = java.lang.StringBuilder()
                log.appendLine("matroid #$index = $matroid")
                if (matroid.loops().isNotEmpty()) {
                    log.appendLine("has loops ${matroid.loops()}, removing")
                    matroid = matroid.withoutLoops()
                    log.appendLine(matroid)
                }
                val truncated = TruncatedMatroid(matroid, matroid.rank() - 1)

                val ratios = mutableListOf<Double>()


                for (truncation in listOf(false, true)) {
                    if (!truncation) {
                        log.appendLine("salvaging existing ratio")
                        ratios.add(salvage[index]!!)
                        continue
                    }
                    if (truncation && matroid.rank() <= 1) {
                        ratios.add(.0)
                        break
                    }
                    val numAutomorphisms = if (truncation) truncated.automorphisms().size else numAutomorphisms
                    printMutex.withLock {
                        currWorking[index] = InProgressMatroid3(matroid, 0, 0, 0, startTime, numAutomorphisms, truncation)
                        printCurrWorking(add = index)
                    }

                    if (!truncation) {
                        val decomposition = matroid.decompose()
                        if (decomposition != null) {
                            val (matroid1, matroid2) = decomposition
                            val index1 = matroids.indexOfFirst { isomorphic(it.withoutLoops(), matroid1) }
                            val index2 = matroids.indexOfFirst { isomorphic(it.withoutLoops(), matroid2) }
                            /*println(matroid)
                            println(matroid.automorphisms())
                            println("identified matroid as sum of #$index1 and #$index2")
                            println(matroids[index1])*/
                            //println(matroids[index1].automorphisms())
                            //println(matroids[index2])
                            //println(matroids[index2].automorphisms())
                            //println("identified matroid as sum of #$index1 and #$index2")
                            val ratio1 = prevProgress[index1]!!
                            val ratio2 = prevProgress[index2]!!
                            log.appendLine("identified matroid as sum of #$index1 (ratio = $ratio1) and #$index2 (ratio = $ratio2)")
                            val ratio = min(ratio1, ratio2)
                            log.appendLine("concluding that ratio = $ratio")
                            ratios.add(ratio)
                            continue
                        }
                    }

                    if (truncation && truncated.automorphisms().size <= 20) {
                        log.appendLine("truncated matroid has too few automorphisms, searching for previous calculation")
                        val truncatedIndex = matroids.indexOfFirst { isomorphic(it.withoutLoops(), truncated) }
                        log.appendLine("identified as #$truncatedIndex")
                        ratios.add(prevProgress[truncatedIndex] ?: throw Exception("uncalculated matroid #$truncatedIndex"))
                        continue
                    }

                    val lp = solveMatroidStep1Gurobi(if (truncation) truncated else matroid, 42, log, logFileName = if (truncation) "truncated_$index" else "matroid_$index", threads = numThreads)
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
                        val truncatedIndex = matroids.indexOfFirst { isomorphic(it.withoutLoops(), truncated) }
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