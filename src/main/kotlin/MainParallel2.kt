package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun main() = runBlocking(Dispatchers.Default) {
    val n = 7
    val matroids = parseMatroidsFile(File("matroids09_bases.txt"), targetSize = n)
    println("num matroids = ${matroids.size}")

    val prevProgress = "" // File("progress.txt").readText()

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()
    val currWorking = mutableMapOf<Int, InProgressMatroid>()
    var remMatroids = matroids.size
    fun printCurrWorking() {
        println("-".repeat(20))
        for ((index, inProgress) in currWorking) {

            println("#$index | since ${formatTimestamp(inProgress.startTime)} | ${inProgress.variables} variables, ${inProgress.constraints} constraints [${inProgress.automorphisms} automorphisms]")
        }
        println("-".repeat(20))
        println("$remMatroids matroids remaining")
        println("-".repeat(20))
    }

    val hits = matroids.withIndex().map { (index, matroid) ->
        async {
            if (matroid.isDecomposable()) {
                printMutex.withLock {
                    remMatroids--
                    println("matroid #$index is decomposable")
                }
                return@async null
            }

            if ("matroid #$index" in prevProgress) {
                printMutex.withLock {
                    remMatroids--
                    println("matroid #$index already handled")
                }
                return@async null
            }

            val (variables, constraints) = assessMatroidQuietly(matroid)
            val numAutomorphisms = bijections(matroid.elements()).count { isAutomorphism(matroid, it) }

            // 2. Create a local string builder for this specific execution
            val log = java.lang.StringBuilder()

            log.appendLine("matroid #$index = $matroid")

            val startTime = System.currentTimeMillis()
            printMutex.withLock {
                currWorking[index] = InProgressMatroid(matroid, variables, constraints, startTime, numAutomorphisms)
                printCurrWorking()
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
                currWorking.remove(index)
                print(log.toString()) // Use print, not println, since we appended lines
                printCurrWorking()
            }

            hitResult // Return the actual computation result to the list
        }
    }.awaitAll().filterNotNull()

    println("Total hits: ${hits.size}")
    for (matroid in hits) {
        println(matroid)
    }
}

data class InProgressMatroid(val matroid: Matroid<Int>, val variables: Int, val constraints: Int, val startTime: Long, val automorphisms: Int)

fun formatTimestamp(millis: Long): String {
    // 1. Convert the raw milliseconds into an Instant
    val instant = Instant.ofEpochMilli(millis)

    // 2. Apply a time zone (using the system's default time zone here)
    val dateTime = instant.atZone(ZoneId.systemDefault())

    // 3. Define your desired output pattern
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm:ss a z")

    // 4. Format and return
    return formatter.format(dateTime)
}