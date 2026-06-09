package org.example

import java.io.File
import java.util.StringTokenizer
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val domIndex = args[0].toInt()
    val subIndex = args[1].toInt()
    val numThreads = args[2].toInt()
    val nnzLowerBound = args[3].toInt()
    val nnzUpperBound = args[4].toInt()

    Thread.sleep(subIndex.toLong() * 30000L)

    val n = 8
    println("Tight Conjecture Calculation for n = $n")
    println("Running on architecture: ${System.getProperty("os.arch")}")
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    println("num matroids = ${matroids.size}")

    val importantDataFile = File("matroids_${n}_tight_conjecture.txt")
    val loggingFile = File("matroids_${n}_tight_conjecture_log.txt")
    val locker = ClusterLock("matroids_8")

    val realRatios = extractDataRaw(File("matroids_${n}_truncation_conjecture.txt").readText(), "original ratio = ")

    val preCalcNonZeros = mutableMapOf<Int, Int>()
    for (line in File("matroids_8_lp_stats.txt").readLines()) {
        val tokenizer = StringTokenizer(line)
        val index = tokenizer.nextToken().toInt()
        val nonZeros = tokenizer.nextToken().toInt()
        preCalcNonZeros[index] = nonZeros
    }

    val claimFail = File("matroids_8_claimed.txt")
    claimFail.createNewFile()
    val progressFile = File("matroids_8_tight_progress.txt")

    fun updateProgressFile(progress: InProgressMatroid5) {
        locker.execute {
            val lines = progressFile.readLines().toMutableList()
            val prefix = "#${progress.index}"
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

            val covered = mutableSetOf<Int>()
            for (line in importantDataFile.readLines()) {
                val tokenizer = StringTokenizer(line)
                covered.add(tokenizer.nextToken().toInt())
            }

            val alreadyClaimed = mutableSetOf<Int>()
            for (line in claimFail.readLines()) {
                alreadyClaimed.add(line.toInt())
            }

            val remaining = matroids.indices.filter {
                it != 0 && !matroids[it].withoutLoops().isDecomposable() && it !in covered && (preCalcNonZeros[it] ?: 0) / 1_000_000 in nnzLowerBound until nnzUpperBound && it !in alreadyClaimed
            }
            println("remaining = ${remaining.size}")
            val choice = remaining.firstOrNull()

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
        val trueRatio = realRatios[index]!!
        val numAutomorphisms = matroid.automorphisms().size


        updateProgressFile(InProgressMatroid5(index, matroid, 0, 0, 0, startTime, numAutomorphisms, true,
            ProgressStatus.NEW
        ))

        val lp = solveMatroidStep1Gurobi(matroid, 301, log, logFileName = "tight_$index", threads = numThreads)
        val variables = lp.getNumVariables()
        val constraints = lp.getNumConstraints()
        val nonzeros = lp.getNumNonZeros()

        updateProgressFile(InProgressMatroid5(index, matroid, variables, constraints, nonzeros, startTime, numAutomorphisms, true,
            ProgressStatus.CONSTRUCTED
        ))

        log("solving with more symmetry")
        val tightRatio = solveMatroidStep2Gurobi(lp, log)
        updateProgressFile(InProgressMatroid5(index, matroid, variables, constraints, nonzeros, startTime, numAutomorphisms, true,
            ProgressStatus.FINISHED
        ))

        log("\n")
        log("true ratio = $trueRatio")
        log("tight ratio = $tightRatio")
        if (trueRatio < tightRatio - .000001) {
            log("COUNTEREXAMPLE")
        }
        log("\n\n\n\n")
        locker.execute {
            loggingFile.appendText(logger.toString())
            importantDataFile.appendText("$index $tightRatio $trueRatio ${tightRatio - trueRatio}\n")
        }
        if (trueRatio < tightRatio - .000001) {
            println("COUNTEREXAMPLE")
            exitProcess(0)
        }
    }
}
