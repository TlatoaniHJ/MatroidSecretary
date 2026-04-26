package org.example

import java.io.File
import java.text.DecimalFormat

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
    addData(File("matroids_8_greedy_filter.txt"), "greedy competitive ratio = ")
}

fun displayCurrentResults() {
    val ratioFormat = DecimalFormat("0.00000000")
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases.txt"), targetSize = n)
    val currentData = extractDataStructured(File("matroids_8_competitive_ratios.txt"))
    val currentGreedyData = extractDataStructured(File("matroids_8_greedy_filter.txt"))
    for ((index, ratio) in currentData.entries.filter { it.value != null }.sortedBy { it.value }) {
        val matroid = matroids[index]
        val numAutomorphisms = matroid.automorphisms().size
        val rank = matroid.rank()
        println("matroid #$index  \t\tcompetitive ratio = ${ratioFormat.format(ratio)}\t\trank = $rank\t\tnum automorphisms = ${padWithSpaces(numAutomorphisms, 5)}") //\t\tbases = ${matroid.bases()}")
    }
    println()
    for (rank in 0..n) {
        val numUncalculated = matroids.withIndex().filter { (_, matroid) -> matroid.rank() == rank }.count { (index, _) -> index !in currentData && (index !in currentGreedyData || currentGreedyData[index]!! < .4099) }
        println("there are $numUncalculated matroids of rank $rank for which the competitive ratio has not been calculated that have not been filtered out by greedy")
    }
}

fun padWithSpaces(x: Int, length: Int): String {
    var result = x.toString()
    return " ".repeat(length - result.length) + result
}