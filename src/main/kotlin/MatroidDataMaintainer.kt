package org.example

import java.io.File
import java.text.DecimalFormat
import kotlin.math.max


import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.StringTokenizer
import kotlin.math.roundToInt

fun extractDataRaw(contents: String, valueIdentifier: String): Map<Int, Double?> {
    val matroidIdentifier = "matroid #"
    val decomposableIdentifier = "is decomposable"
    var index = 0
    val result = mutableMapOf<Int, Double?>()
    while (true) {
        index = contents.indexOf(matroidIdentifier, startIndex = index)
        if (index == -1) {
            break
        }
        index += matroidIdentifier.length
        val spaceIndex = contents.indexOfAny(" \n".toCharArray(), startIndex = index)
        val matroid = contents.substring(index, spaceIndex).toInt()
        index = spaceIndex + 1
        if (contents.startsWith(decomposableIdentifier, index)) {
            result[matroid] = null
        } else {
            index = contents.indexOf(valueIdentifier, startIndex = index)
            index += valueIdentifier.length
            val spaceIndex = contents.indexOfAny(" \n".toCharArray(), startIndex = index)
            result[matroid] = contents.substring(index, spaceIndex).toDouble()
            index = spaceIndex
        }
    }
    return result
}

fun extractDataStructured(file: File): Map<Int, Double?> {
    if (!file.exists()) {
        return mapOf()
    }
    val result = mutableMapOf<Int, Double?>()
    for (line in file.readLines()) {
        val split = line.split(" ")
        val index = split[0].toInt()
        val value = split[1].toDoubleOrNull()
        result[index] = value
    }
    return result
}

fun writeDataStructured(file: File, data: Map<Int, Double?>) {
    file.writeText(data.entries.sortedBy { it.key }.map { (matroid, value) -> "$matroid $value\n" }.joinToString(""))
}

fun addData(file: File, valueIdentifier: String) {
    val newDataUnparsed = StringBuilder()
    while (true) {
        val line = readln()
        if (line == "STOP") {
            break
        }
        newDataUnparsed.appendLine(line)
    }
    val newData = extractDataRaw(newDataUnparsed.toString(), valueIdentifier)
    val oldData = extractDataStructured(file)
    writeDataStructured(file, newData + oldData)
}

fun main() {
    //addData(File("matroids_8_competitive_ratios.txt"), "Objective value = ")
    //displayCurrentResults()
    //addData(File("matroids_8_greedy_filter.txt"), "greedy competitive ratio = ")
    //writeDataStructured(File("matroids_8_greedy_filter_3.txt"), extractDataRaw(File("matroids_8_optimized_3_greedy_filter_raw.txt").readText(), "greedy competitive ratio = "))
    //writeDataStructured(File("matroids_9_greedy_filter_3.txt"), extractDataRaw(File("matroids_9_3_greedy_filter_raw.txt").readText(), "greedy competitive ratio = "))
    //displayCurrentGreedyResults()
    //writeDataStructured(File("matroids_9_3_competitive_ratios.txt"), extractDataRaw(File("matroids_9_3_competitive_ratios_raw.txt").readText(), "Objective value = "))
    //displayCurrentResults()
    //rank2Information(9)
    //dualCheck()
    //truncationInformation()
    //lpStatistics()
    //evaluateMatroidRAM()
    //sortMatroidLPStats()
    tightConjectureInformation()
}

fun displayCurrentResults() {
    val ratioFormat = DecimalFormat("0.00000000")
    val n = 9
    val k = 3
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n, targetRank = k)
    val currentData = extractDataStructured(File("matroids_9_3_competitive_ratios.txt"))
    val currentGreedyData = extractDataStructured(File("matroids_9_greedy_filter_3.txt"))
    var position = 0
    for ((index, ratio) in currentData.entries.filter { it.value != null }.sortedBy { it.value }) {
        position++
        val matroid = matroids[index]
        val numAutomorphisms = matroid.automorphisms().size
        val rank = matroid.rank()
        val greedyRatio = currentGreedyData[index]
        val greedyRatioFormat = if (greedyRatio == null) "null      " else ratioFormat.format(greedyRatio)
        println("${position}.  \tmatroid #$index  \t\tcompetitive ratio = ${ratioFormat.format(ratio)}\t\tgreedy competitive ratio = $greedyRatioFormat\t\trank = $rank\t\tnum automorphisms = ${padWithSpaces(numAutomorphisms, 6)}") //\t\tbases = ${matroid.bases()}")
        if (greedyRatio != null && greedyRatio > ratio!!) {
            println("CONTRADICTION")
            return
        }
    }
    println()
    for (rank in 0..n) {
        val numUncalculated = matroids.withIndex().filter { (_, matroid) -> matroid.rank() == rank }.count { (index, _) -> index !in currentData && (index !in currentGreedyData || currentGreedyData[index]!! < .406) }
        println("there are $numUncalculated matroids of rank $rank for which the competitive ratio has not been calculated that have not been filtered out by greedy")
    }
}

fun displayCurrentGreedyResults() {
    val ratioFormat = DecimalFormat("0.00000000")
    val n = 9
    val k = 3
    val matroids = parseMatroidsFile(File("matroids09_bases.txt"), targetSize = n, targetRank = k)
    val currentGreedyData = extractDataStructured(File("matroids_9_greedy_filter_3.txt"))
    var switch = false
    var position = 0
    for ((index, ratio) in currentGreedyData.entries.filter { it.value != null }.sortedBy { it.value }) {
        position++
        if (!switch && ratio!! > .406) {
            switch = true
            println("-".repeat(32))
        }
        val matroid = matroids[index]
        val automorphismText = if (false) {
            val numAutomorphisms = matroid.automorphisms().size
            "\t\tnum automorphisms = ${padWithSpaces(numAutomorphisms, 6)}"
        } else {
            ""
        }
        val rank = matroid.rank()
        println("$position.\tmatroid #$index  \t\tgreedy competitive ratio = ${ratioFormat.format(ratio)}\t\trank = $rank$automorphismText") //\t\tbases = ${matroid.bases()}")
    }
    println()
    for (rank in k..k) {
        val numUncalculated = matroids.withIndex().filter { (_, matroid) -> matroid.rank() == rank }.count { (index, _) -> index !in currentGreedyData }
        println("there are $numUncalculated matroids of rank $rank for which the greedy competitive ratio has not been calculated")
    }
}

fun padWithSpaces(x: Int, length: Int): String {
    var result = x.toString()
    return " ".repeat(length - result.length) + result
}

fun rank2Information(n: Int) {
    val ratioFormat = DecimalFormat("0.00000000")
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n, targetRank = 2)
    val currentData = extractDataRaw(File("matroids_9_2_competitive_ratios_raw.txt").readText(), "Objective value = ")
    for ((index, ratio) in currentData.entries.filter { it.value != null }.sortedBy { it.value }) {
        val matroid = matroids[index]
        val numAutomorphisms = matroid.automorphisms().size
        val rank = matroid.rank()
        val classes = mutableListOf<MutableList<Int>>()
        for (elem in matroid.elements()) {
            var c = classes.find { setOf(elem, it.first()) !in matroid }
            if (c == null) {
                c = mutableListOf()
                classes.add(c)
            }
            c.add(elem)
        }
        val classSizes = classes.map { it.size }.sortedDescending()

        println("matroid #$index  \t\tcompetitive ratio = ${ratioFormat.format(ratio)}\t\trank = $rank\t\tnum automorphisms = ${padWithSpaces(numAutomorphisms, 6)}\t\tclass sizes = $classSizes") //\t\tbases = ${matroid.bases()}")
    }
    println()
    for (rank in 2..2) {
        val numUncalculated = matroids.withIndex().filter { (_, matroid) -> matroid.rank() == rank && !matroid.isDecomposable() }.count { (index, _) -> index !in currentData }
        println("there are $numUncalculated nondecomposable matroids of rank $rank for which the competitive ratio has not been calculated")
    }
}

fun dualCheck() {
    println("opening matroids file...")
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = 8)
    println("opening data file...")
    val data = extractDataRaw(File("matroids_8_truncation_conjecture.txt").readText(), "Objective value = ")
    println("sorting matroids...")
    for (index in matroids.indices.filter { it in data }.sortedBy { data[it]!! }) {
        val matroid = matroids[index]
        println("processing $matroid...")
        val dual = DualMatroid(matroid)
        val dualIndex = matroids.indexOfFirst { isomorphic(it, dual) }
        println("#$index | rank ${matroid.rank()} | competitive ratio = ${data[index]} | dual competitive ratio = ${data[dualIndex]}")
    }
}

fun truncationInformation() {
    val ratioFormat = DecimalFormat("0.00000000")
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = 8)
    val originalRatios = extractDataRaw(File("matroids_8_truncation_conjecture.txt").readText(), "original ratio = ")
    val truncatedRatios = extractDataRaw(File("matroids_8_truncation_conjecture.txt").readText(), "truncated ratio = ")
    var position = 0
    var line = false
    for (index in matroids.indices.filter { it in originalRatios }.sortedBy { originalRatios[it]!! - truncatedRatios[it]!! }) {
        position++
        val matroid = matroids[index]
        val ratio = originalRatios[index]!!
        val truncatedRatio = truncatedRatios[index]!!
        if (!line && ratio - truncatedRatio > .000001) {
            line = true
            println("-".repeat(200))
        }
        val numAutomorphisms = matroid.automorphisms().size
        val rank = matroid.rank()
        if (rank <= 1) {
            continue
        }
        println("${position}.  \tmatroid #$index  \t\tcompetitive ratio = ${ratioFormat.format(ratio)}\t\ttruncated competitive ratio = ${ratioFormat.format(truncatedRatio)}\t\tdifference = ${ratioFormat.format(ratio - truncatedRatio)} \t\trank = $rank\t\tnum automorphisms = ${padWithSpaces(numAutomorphisms, 6)}") //\t\tbases = ${matroid.bases()}")
    }
    println()
    for (rank in 2..8) {
        val numUncalculated = matroids.withIndex().filter { (_, matroid) -> matroid.rank() == rank }.count { (index, _) -> index !in originalRatios }
        println("there are $numUncalculated matroids of rank $rank for which the competitive ratio has not been calculated")
    }
    println()
    for (rank in 2..8) {
        val numUncalculated = matroids.withIndex().filter { (_, matroid) -> matroid.rank() == rank }.count { (index, _) -> index !in originalRatios && !matroids[index].withoutLoops().isDecomposable() }
        println("there are $numUncalculated nondecomposable matroids of rank $rank for which the competitive ratio has not been calculated")
    }
}

fun lpStatistics() {
    val prefixes = listOf("", "archive_8_truncation_conjecture/", "archive_8_truncation_conjecture_2/", "archive_8_truncation_conjecture_3/", "archive_8_truncation_conjecture_4/")
    val lps = mutableListOf<LP>()

    val nonzeroLabel = " nonzeros (Max)"
    val ramLabel = "roughly "
    for (index in 1..1725) {
        for (prefix in prefixes) {
            val fileName = "logs/${prefix}matroid_$index.txt"
            println(fileName)
            try {
                val text = File(fileName).readText()
                var index = text.lastIndexOf(nonzeroLabel)
                var nonzerosString = ""
                index--
                while (text[index].isDigit()) {
                    nonzerosString = text[index] + nonzerosString
                    index--
                }
                val nonzeros = nonzerosString.toInt()

                index = text.indexOf(ramLabel) + ramLabel.length
                var ramString = ""
                while (text[index].isDigit() || text[index] == '.') {
                    ramString += text[index]
                    index++
                }
                var ram = ramString.toDouble()
                if (text.startsWith(" MB", startIndex = index)) {
                    ram /= 1024.0
                }
                println("\tsucceeded: nonzeros = $nonzeros, ram estimate = $ram")
                lps.add(LP(nonzeros, ram, fileName))
            } catch (e: Exception) {
                println("\tfailed: $e")
            }
        }
    }
    lps.sortBy { it.nonzeros }
    var mexico = .0
    for ((nonzeros, ramEstimate, source) in lps) {
        mexico = max(mexico, ramEstimate)
        println("nonzeros = $nonzeros\tram estimate = $ramEstimate\tprefix max ram estimate = $mexico\tsource = $source")
    }
}

data class LP(val nonzeros: Int, val ramEstimate: Double, val source: String)

/*fun evaluateMatroidRAM() {
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = 8)
    val originalRatios = extractDataRaw(File("matroids_8_truncation_conjecture.txt").readText(), "original ratio = ")
    val timer = Timer()
    for ((index, matroid) in matroids.withIndex()) {
        if (index !in originalRatios && index != 0) {
            val rank = matroid.rank()
            val automorphisms = matroid.automorphisms().size
            val lp = solveMatroidStep1Gurobi(matroid, mode = 42, log = StringBuilder())
            println("$index ${lp.getNumNonZeros()} | rank = $rank, num automorphisms = $automorphisms [${timer.lapSeconds()} seconds]")
        }
    }
}*/

fun evaluateMatroidRAM() = runBlocking {
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = 8)
    val originalRatios = extractDataRaw(File("matroids_8_truncation_conjecture.txt").readText(), "original ratio = ")
    val timer = Timer()

    // Protects the console from overlapping print statements
    val printMutex = Mutex()

    // Set this based on your RAM overhead for Step 1 LP generation
    val maxConcurrentWorkers = 8
    val dispatcher = Dispatchers.Default.limitedParallelism(maxConcurrentWorkers)

    matroids.withIndex()
        .filter { it.index !in originalRatios && it.index != 0 }
        .map { (index, matroid) ->
            launch(dispatcher) {
                val rank = matroid.rank()
                val automorphisms = matroid.automorphisms().size

                val lp = solveMatroidStep1Gurobi(matroid, mode = 42, log = StringBuilder())
                val nonZeros = lp.getNumNonZeros()

                // Lock the output stream so rows don't overlap in the console
                printMutex.withLock {
                    println("$index $nonZeros | rank = $rank, num automorphisms = $automorphisms [${timer.lapSeconds()} seconds]")
                }

                // CRITICAL: Free the C++ pointer before the coroutine dies
                lp.close()
            }
        }
        .joinAll()
}

data class MatroidLPStat(val index: Int, val rank: Int, val automorphisms: Int, val nonZeros: Int)

fun sortMatroidLPStats() {
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = 8)
    val stats = mutableListOf<MatroidLPStat>()
    for (line in File("matroids_8_lp_stats.txt").readLines()) {
        val tokenizer = StringTokenizer(line)
        val index = tokenizer.nextToken().toInt()
        val nonZeros = tokenizer.nextToken().toInt()
        val matroid = matroids[index]
        stats.add(MatroidLPStat(index, matroid.rank(), matroid.automorphisms().size, nonZeros))
    }
    stats.sortBy { it.nonZeros }
    val format = DecimalFormat("000,000")
    for ((j, stat) in stats.withIndex()) {
        val (index, rank, automorphisms, nonZeros) = stat
        println("${j + 1}.  \t #$index\t\tnonzeros = ${format.format(nonZeros)}\trank = $rank\tautomorphisms = $automorphisms")
    }
}

data class TightConjectureLine(val index: Int, val ratio: Double, val trueRatio: Double, val diff: Double)

fun tightConjectureInformation() {
    //val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = 8)
    val lines = File("matroids_8_tight_conjecture.txt").readLines().map { line ->
        val (index, ratio, trueRatio, diff) = line.split(" ").map(String::toDouble)
        TightConjectureLine(index.roundToInt(), ratio, trueRatio, diff)
    }.sortedByDescending { it.diff }
    for ((index, ratio, trueRatio, diff) in lines) {
        println("$index   \t\t$ratio\t$trueRatio\t$diff")
    }
}