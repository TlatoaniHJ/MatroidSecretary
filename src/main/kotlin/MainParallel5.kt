package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.example.core.graphicMatroidOf

fun main() = runBlocking(Dispatchers.Default) {
    println("Running on architecture: ${System.getProperty("os.arch")}")

    /*val matroids = listOf(
        //rank2MatroidFrom(8, 1, 1),
        //rank2MatroidFrom(7, 2, 1),
        rank2MatroidFrom(7, 1, 1, 1),
        //rank2MatroidFrom(6, 2, 2),
        rank2MatroidFrom(6, 3, 1),
        //rank2MatroidFrom(5, 4, 1),
        rank2MatroidFrom(5, 3, 2),
        rank2MatroidFrom(4, 4, 2),
        //rank2MatroidFrom(4, 3, 3),
        rank2MatroidFrom(2, 2, 2, 2, 2),
    )*/
    /*val matroids = listOf(
        //rank2MatroidFrom(2),
        rank2MatroidFrom(2, 2),
        //rank2MatroidFrom(2, 2, 2),
        //rank2MatroidFrom(2, 2, 2, 2),
        rank2MatroidFrom(2, 2, 1),
        rank2MatroidFrom(2, 2, 1, 1),
        rank2MatroidFrom(2, 2, 1, 1, 1),
        rank2MatroidFrom(2, 2, 1, 1, 1, 1),
        rank2MatroidFrom(2, 2, 1, 1, 1, 1, 1),
        rank2MatroidFrom(2, 2, 1, 1, 1, 1, 1, 1),
        rank2MatroidFrom(2, 2, 1, 1, 1, 1, 1, 1, 1),
        rank2MatroidFrom(2, 2, 1, 1, 1, 1, 1, 1, 1, 1),
        //rank2MatroidFrom(2, 2, 2, 2, 2),
    )*/
    val A = -1
    val B = 0
    val hat3 = graphicMatroidOf(A to B, A to 1, 1 to B, A to 2, 2 to B, A to 3, 3 to B, A to 4, 4 to B)
    val matroids = listOf(TruncatedMatroid(hat3, 3), TruncatedMatroid(hat3, 2))

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()
    val currWorking = mutableMapOf<Int, InProgressMatroid>()
    var remMatroids = matroids.size
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
    val concurrentSolves = Semaphore(4)

    //val file = File("matroids_9_2_competitive_ratios_raw.txt")

    val hits = matroids.withIndex().map { (index, matroid) ->
        async {
            concurrentSolves.withPermit {

                // 2. Create a local streing builder for this specific execution
                val log = java.lang.StringBuilder()

                val lp = solveMatroidStep1Gurobi(matroid, 32, log)
                val variables = lp.getNumVariables()
                val constraints = lp.getNumConstraints()
                val numAutomorphisms = matroid.automorphisms().size

                log.appendLine("matroid #$index = $matroid")

                val startTime = System.currentTimeMillis()
                printMutex.withLock {
                    print(log.toString())
                    currWorking[index] = InProgressMatroid(uniformMatroid((1..8).toSet(), 3), variables, constraints, startTime, numAutomorphisms)
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
                //file.appendText(log.toString())

                hitResult // Return the actual computation result to the list
            }
        }
    }.awaitAll().filterNotNull()

    println("Total hits: ${hits.size}")
    for (matroid in hits) {
        println(matroid)
    }
}

class Rank2Matroid<E>(val classes: List<Set<E>>): Matroid<E> {
    override fun elements() = classes.flatten().toSet()
    override fun contains(set: Set<E>) = set.size <= 2 && classes.all { it.intersect(set).size <= 1 }
    override fun toString(): String {
        val sizes = classes.map { it.size }
        return "Rank2Matroid(${sizes.joinToString(", ")})"
    }
}

fun rank2MatroidFrom(vararg classSizes: Int): Matroid<Int> {
    val classes = mutableListOf<Set<Int>>()
    var element = 0
    for (classSize in classSizes) {
        classes.add((element + 1..element + classSize).toSet())
        element += classSize
    }
    return Rank2Matroid(classes)
}