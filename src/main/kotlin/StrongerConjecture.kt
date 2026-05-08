package org.example

import java.io.File

fun main() {
    testConjecture1()
}

fun testConjecture1() {
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = 8)
    val ratios = extractDataRaw(File("matroids_8_truncation_conjecture.txt").readText(), "original ratio = ")
    val counterexamples = mutableListOf<Pair<Int, Int>>()
    for ((index1, matroid1) in matroids.withIndex()) {
        if (matroid1.rank() >= 2) {
            println("matroid #$index1 = $matroid1")
            val bases = matroid1.bases()
            val ratio = ratios[index1] ?: continue
            for ((index2, matroid2) in matroids.withIndex()) {
                val ratio2 = ratios[index2] ?: continue
                if (matroid1.rank() == matroid2.rank() && matroid2.loops().size == matroid1.loops().size && matroid1.bases().size > matroid2.bases().size && matroid2.loops().isEmpty()) {
                    val refinement = bijections(matroid1.elements()).any { p ->
                        matroid2.bases().map { it.map(p::getValue).toSet() }.all { it in bases }
                    }
                    if (refinement) {
                        println("\trefinement: matroid #$index2 = $matroid2")
                        println("\t$ratio vs $ratio2")
                        if (ratio2 > ratio + .000001) {
                            println("COUNTEREXAMPLE")
                            counterexamples.add(Pair(index1, index2))
                        }
                    }
                }
            }
            println()
        }
    }
    //println("No counterexamples")
    println("Number of counterexamples = ${counterexamples.size}")
    for ((index1, index2) in counterexamples) {
        println()
        val matroid1 = matroids[index1]
        val ratio1 = ratios[index1]!!
        val matroid2 = matroids[index2]
        val ratio2 = ratios[index2]!!
        println("matroid #$index1 [rank ${matroid1.rank()}, nonloops = ${matroid1.elements().size - matroid1.loops().size}] = $matroid1")
        println("matroid #$index2 [rank ${matroid2.rank()}, nonloops = ${matroid2.elements().size - matroid2.loops().size}] = $matroid2")
        println("matroid #$index2 refines matroid #$index1")
        println("matroid #$index1 has ratio $ratio1, matroid #$index2 has ratio $ratio2, diff = ${ratio2 - ratio1}")
    }
}

fun testConjecture2() {
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = 4)
    //val ratios = extractDataRaw(File("matroids_7_truncation.txt").readText(), "ratio of original = ")
    val ratios = mutableMapOf<Int, Double>()
    val counterexamples = mutableListOf<Pair<Int, Int>>()
    for ((index, matroid) in matroids.withIndex()) {
        println("matroid #$index [rank ${matroid.rank()}] = $matroid")
        if (index == 0) {
            continue
        } else {
            val lp = solveMatroidStep1Gurobi(matroid, 42, StringBuilder(), threads = 1)
            ratios[index] = solveMatroidStep2Gurobi(lp, StringBuilder())
        }
        val ratio = ratios[index]!!
        println("\tratio = $ratio")
        if (matroid.rank() <= 1) {
            continue
        }

        val truncated = with(matroid) { TruncatedMatroid(this, rank() - 1) }
        val truncatedIndex = matroids.indexOfFirst { isomorphic(it, truncated) }
        val truncatedRatio = ratios[truncatedIndex]!!
        println("\ttruncated = #$truncatedIndex with ratio $truncatedRatio")
        for (element in matroid.elements() - matroid.loops()) {
            val contracted = matroid.contract(setOf(element))
            val contractedIndex = matroids.indexOfFirst { isomorphic(it.withoutLoops(), contracted.withoutLoops()) }
            val contractedRatio = ratios[contractedIndex]!!
            println("\tcontracting $element => #$contractedIndex with ratio $contractedRatio")
            if (contractedRatio < truncatedRatio - .000001) {
                println("COUNTEREXAMPLE")
                counterexamples.add(Pair(index, element))
            }
        }

        println()
    }
    //println("No counterexamples")
    println("Number of counterexamples = ${counterexamples.size}")
    for ((index, element) in counterexamples) {
        val matroid = matroids[index]
        val ratio = ratios[index]!!
        val truncated = with(matroid) { TruncatedMatroid(this, rank() - 1) }
        val truncatedIndex = matroids.indexOfFirst { isomorphic(it, truncated) }
        val truncatedRatio = ratios[truncatedIndex]!!
        val contracted = matroid.contract(setOf(element))
        val contractedIndex = matroids.indexOfFirst { isomorphic(it.withoutLoops(), contracted.withoutLoops()) }
        val contractedRatio = ratios[contractedIndex]!!
        println("original: #$index with ratio $ratio | truncated: #$truncatedIndex with ratio $truncatedRatio | contracted: #$contractedIndex with ratio $contractedRatio")
    }
}