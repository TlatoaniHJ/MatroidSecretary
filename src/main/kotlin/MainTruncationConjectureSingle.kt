package org.example

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.StringTokenizer
import kotlin.io.path.exists
import kotlin.math.min
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val domIndex = args[0].toInt()
    val subIndex = args[1].toInt()
    val numThreads = args[2].toInt()
    val nnzLowerBound = args[3].toInt()
    val nnzUpperBound = args[4].toInt()

    println("Running on architecture: ${System.getProperty("os.arch")}")
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("num matroids = ${matroids.size}")

    val importantDataFile = File("matroids_${n}_truncation_conjecture.txt")
    val locker = ClusterLock("matroids_8")
    val prevProgress = locker.execute { extractDataRaw(importantDataFile.readText(), "original ratio = ") }
    println("remaining = ${matroids.size - 1 - prevProgress.size}")

    val preCalcNonZeros = mutableMapOf<Int, Int>()
    for (line in File("matroids_8_lp_stats.txt").readLines()) {
        val tokenizer = StringTokenizer(line)
        val index = tokenizer.nextToken().toInt()
        val nonZeros = tokenizer.nextToken().toInt()
        preCalcNonZeros[index] = nonZeros
    }

    val claimFail = File("matroids_8_claimed.txt")
    val progressFile = File("matroids_8_progress.txt")

    fun updateProgressFile(progress: InProgressMatroid5) {
        locker.execute {
            val lines = progressFile.readLines().toMutableList()
            val truncationFlag = if (progress.truncation) "TRUNCATION" else "ORIGINAL  "
            val prefix = "$truncationFlag #${progress.index}"
            var lineIndex = lines.indexOfFirst { prefix in it }
            if (lineIndex == -1) {
                lines.add("")
                lineIndex = lines.lastIndex
            }

            // ANSI escape codes
            val green = "\u001B[32m"
            val greenHighIntensity = "\u001B[0;92m"
            val blue = "\u001B[34m"
            val blueHighIntensity = "\u001B[0;94m"
            val vibrantBlue = "\u001B[38;5;39m"
            val magenta = "\u001B[35m"
            val purpleHighIntensity = "\u001B[0;95m"
            val red = "\u001B[31m"
            val redHighIntensity = "\u001B[0;91m"
            val yellowHighIntensity = "\u001B[0;93m"
            val reset = "\u001B[0m"
            val color = when (progress.status) {
                ProgressStatus.NEW -> green
                ProgressStatus.CONSTRUCTED -> vibrantBlue
                ProgressStatus.FINISHED -> magenta
                ProgressStatus.ALT_FINISHED -> yellowHighIntensity
            }
            val lpStats =
                if (progress.status == ProgressStatus.NEW) "constructing LP" else "${commas(progress.variables)} variables, ${commas(progress.constraints)} constraints, ${commas(progress.nonzeros)} nonzeros"
            lines[lineIndex] =
                "$color$prefix \t(original rank = ${progress.matroid.rank()}) | since ${formatTimestamp(progress.startTime)} (job ${domIndex}_$subIndex)\t| $lpStats [${progress.automorphisms} automorphisms]$reset"
            progressFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    fun findMatroid(): Int {
        return locker.execute {
            val alreadyClaimed = mutableSetOf<Int>()
            for (line in claimFail.readLines()) {
                alreadyClaimed.add(line.toInt())
            }
            val choice =
                matroids.indices.find { it != 0 && it !in prevProgress && preCalcNonZeros[it]!! / 1_000_000 in nnzLowerBound until nnzUpperBound && it !in alreadyClaimed }
            if (choice == null) {
                println("exhausted matroids with nnz between $nnzLowerBound and $nnzUpperBound")
                exitProcess(0)
            }
            claimFail.appendText("$choice\n")
            choice
        }
    }


    while (true) {
        val index = findMatroid()
        val startTime = System.currentTimeMillis()
        var matroid = matroids[index]
        val logger = Logger()
        val log = logger::log
        log("matroid #$index = $matroid")

        if (matroid.loops().isNotEmpty()) {
            log("has loops ${matroid.loops()}, removing")
            matroid = matroid.withoutLoops()
            log(matroid)
        }
        val truncated = TruncatedMatroid(matroid, matroid.rank() - 1)
        val ratios = mutableListOf<Double>()
        for (truncation in listOf(false, true)) {
            if (truncation && matroid.rank() <= 1) {
                ratios.add(.0)
                break
            }
            val numAutomorphisms = if (truncation) truncated.automorphisms().size else matroid.automorphisms().size
            updateProgressFile(InProgressMatroid5(index, matroid, 0, 0, 0, startTime, numAutomorphisms, truncation,
                ProgressStatus.NEW
            ))

            if (!truncation) {
                val logFile = File("logs/matroid_$index.txt")
                if (logFile.exists()) {
                    val logFileContents = logFile.readText()

                    val objectiveValueLabel = "Optimal objective "
                    if (objectiveValueLabel in logFileContents) {
                        val startIndex = logFileContents.lastIndexOf(objectiveValueLabel) + objectiveValueLabel.length
                        val endIndex = logFileContents.indexOfAny(charArrayOf(' ', '\n'), startIndex = startIndex)
                        val ratio = logFileContents.substring(startIndex, endIndex).toDouble()
                        log("salvaged ratio = $ratio")
                        ratios.add(ratio)
                        updateProgressFile(
                            InProgressMatroid5(
                                index, matroid, 0, 0, 0, startTime, numAutomorphisms, truncation,
                                ProgressStatus.ALT_FINISHED
                            )
                        )
                        continue
                    }
                }

                val decomposition = matroid.decompose()
                if (decomposition != null) {
                    val (matroid1, matroid2) = decomposition
                    val index1 = matroids.indexOfFirst { isomorphic(it.withoutLoops(), matroid1) }
                    val index2 = matroids.indexOfFirst { isomorphic(it.withoutLoops(), matroid2) }

                    val ratio1 = prevProgress[index1]!!
                    val ratio2 = prevProgress[index2]!!
                    log("identified matroid as sum of #$index1 (ratio = $ratio1) and #$index2 (ratio = $ratio2)")
                    val ratio = min(ratio1, ratio2)
                    log("concluding that ratio = $ratio")
                    ratios.add(ratio)
                    updateProgressFile(InProgressMatroid5(index, matroid, 0, 0, 0, startTime, numAutomorphisms, truncation,
                        ProgressStatus.ALT_FINISHED
                    ))
                    continue
                }
            }

            if (truncation && truncated.automorphisms().size <= 20) {
                log("truncated matroid has too few automorphisms, searching for previous calculation")
                val truncatedIndex = matroids.indexOfFirst { isomorphic(it.withoutLoops(), truncated) }
                log("identified as #$truncatedIndex")
                ratios.add(prevProgress[truncatedIndex] ?: throw Exception("uncalculated matroid #$truncatedIndex"))
                updateProgressFile(InProgressMatroid5(index, matroid, 0, 0, 0, startTime, numAutomorphisms, truncation,
                    ProgressStatus.ALT_FINISHED
                ))
                continue
            }

            val lp = solveMatroidStep1Gurobi(if (truncation) truncated else matroid, 42, log, logFileName = if (truncation) "truncated_$index" else "matroid_$index", threads = numThreads)
            val variables = lp.getNumVariables()
            val constraints = lp.getNumConstraints()
            val nonzeros = lp.getNumNonZeros()

            updateProgressFile(InProgressMatroid5(index, matroid, variables, constraints, nonzeros, startTime, numAutomorphisms, truncation,
                ProgressStatus.CONSTRUCTED
            ))

            if (!truncation || nonzeros <= 300000) {
                log("solving with more symmetry")
                ratios.add(solveMatroidStep2Gurobi(lp, log))
                updateProgressFile(InProgressMatroid5(index, matroid, variables, constraints, nonzeros, startTime, numAutomorphisms, truncation,
                    ProgressStatus.FINISHED
                ))
            } else {
                log("truncated matroid LP too large, searching for previous calculation")
                val truncatedIndex = matroids.indexOfFirst { isomorphic(it.withoutLoops(), truncated) }
                log("identified as #$truncatedIndex")
                ratios.add(prevProgress[truncatedIndex]!!)
                updateProgressFile(InProgressMatroid5(index, matroid, variables, constraints, nonzeros, startTime, numAutomorphisms, truncation,
                    ProgressStatus.ALT_FINISHED
                ))
            }
        }

        log("\n")
        log("original ratio = ${ratios[0]}")
        log("truncated ratio = ${ratios[1]}")
        if (ratios[0] < ratios[1] - .000001) {
            log("COUNTEREXAMPLE")
        }
        log("\n\n\n\n")
        locker.execute {
            importantDataFile.appendText(logger.toString())
        }
        if (ratios[0] < ratios[1] - .000001) {
            println("COUNTEREXAMPLE")
            exitProcess(0)
        }
    }
}

data class InProgressMatroid5(val index: Int, val matroid: Matroid<Int>, val variables: Int, val constraints: Int, val nonzeros: Int, val startTime: Long, val automorphisms: Int, val truncation: Boolean, val status: ProgressStatus)

enum class ProgressStatus {
    NEW, CONSTRUCTED, FINISHED, ALT_FINISHED
}

class ClusterLock(private val lockName: String) {
    private val lockDir = File("$lockName.lock")

    fun <T> execute(block: () -> T): T {
        // Try to create the directory until successful (Spin-lock)
        while (!lockDir.mkdir()) {
            Thread.sleep(100) // Wait 100ms before trying again
        }

        return try {
            block()
        } finally {
            // 2. The Release: Forceful and Loud Cleanup
            val lockPath: Path = lockDir.toPath()

            if (lockPath.exists()) {
                // First, delete anything that might have snuck inside the directory (like .nfs files)
                lockDir.listFiles()?.forEach { it.delete() }

                // Now delete the directory itself using NIO so it throws an explicit error if it fails
                try {
                    Files.delete(lockPath)
                } catch (e: Exception) {
                    println("\u001B[38;5;196m[CRITICAL] Failed to release lock! Reason: ${e.message}\u001B[0m")
                    // Optional: If you want to force the job to die so it doesn't leave a permanent lock,
                    // throw e here. Otherwise, it will print the error and continue.
                }
            }
        }
    }
}

fun commas(x: Int) = "%,d".format(x)

class Logger {
    val builder = StringBuilder()

    fun log(line: Any) {
        println(line)
        builder.appendLine(line)
    }

    override fun toString() = builder.toString()
}