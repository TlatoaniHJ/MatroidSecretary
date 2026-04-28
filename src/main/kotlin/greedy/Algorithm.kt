package org.example

import kotlin.math.min

interface Algorithm<E> {
    fun present(matroid: Matroid<E>, weightOrder: List<E>, newIndex: Int, taken: Set<E>): Boolean
}

class GreedyAlgorithm1<E>(val threshold: Int) : Algorithm<E> {
    override fun present(
        matroid: Matroid<E>,
        weightOrder: List<E>,
        newIndex: Int,
        taken: Set<E>
    ): Boolean {
        if (weightOrder.size <= threshold) {
            return false
        }
        return !matroid.spans(weightOrder.subList(0, newIndex).toSet(), weightOrder[newIndex]) && !matroid.spans(taken, weightOrder[newIndex])
    }
}

class GreedyAlgorithm2<E>(val threshold: Int) : Algorithm<E> {
    override fun present(
        matroid: Matroid<E>,
        weightOrder: List<E>,
        newIndex: Int,
        taken: Set<E>
    ): Boolean {
        if (weightOrder.size <= threshold) {
            return false
        }
        return !matroid.spans(weightOrder.subList(0, newIndex).toSet() + taken, weightOrder[newIndex])
    }
}

class CachedAlgorithm<E>(matroid: Matroid<E>, algorithm: Algorithm<E>) : Algorithm<E> {
    val cache = mutableMapOf<VariableKey<E>, Boolean>()

    init {
        val allOrderedSubsets = orderedSubsets(matroid.elements())
        for (orderedSubset in allOrderedSubsets) {
            for ((index, elem) in orderedSubset.withIndex()) {
                val prev = (orderedSubset - elem).toSet()
                for (taken in subsets(prev)) {
                    if (taken in matroid) {
                        cache[VariableKey(orderedSubset, index, taken)] = algorithm.present(matroid, orderedSubset, index, taken)
                    }
                }
            }
        }
    }

    override fun present(
        matroid: Matroid<E>,
        weightOrder: List<E>,
        newIndex: Int,
        taken: Set<E>
    ) = cache[VariableKey(weightOrder, newIndex, taken)]!!
}

fun <E> executeAlgorithm(matroid: Matroid<E>, weightOrder: List<E>, presentationOrder: List<E>, algorithm: Algorithm<E>): Set<E> {
    val seen = mutableSetOf<E>()
    val taken = mutableSetOf<E>()
    for (element in presentationOrder) {
        seen.add(element)
        val subOrder = weightOrder.filter { it in seen }
        val newIndex = subOrder.indexOf(element)
        val takes = algorithm.present(matroid, subOrder, newIndex, taken)
        if (takes) {
            taken.add(element)
        }
    }
    return taken
}

fun <E> evaluateAlgorithmGivenWeights(matroid: Matroid<E>, weightOrder: List<E>, algorithmFactory: () -> Algorithm<E>): Double {
    val selectionProbabilities = MutableList(weightOrder.size) { .0 }
    var factorial = .0
    for (presentationOrder in permutations(matroid.elements())) {
        factorial++
        val algorithm = algorithmFactory()
        val taken = executeAlgorithm(matroid, weightOrder, presentationOrder, algorithm)
        for ((j, element) in weightOrder.withIndex()) {
            if (element in taken) {
                selectionProbabilities[j]++
            }
        }
    }
    var competitiveRatio = 1.0
    for (j in 1..matroid.size()) {
        val rank = matroid.rank(weightOrder.subList(0, j).toSet())
        if (rank > 0) {
            val totalValue = selectionProbabilities.subList(0, j).sum() / factorial
            competitiveRatio = min(competitiveRatio, totalValue / rank.toDouble())
        }
    }
    return competitiveRatio
}

fun <E> evaluateAlgorithm(matroid: Matroid<E>, target: Double, log: (String) -> Unit, algorithmFactory: () -> Algorithm<E>): Double {
    var worstCaseCompetitiveRatio = 1.0
    val automorphisms = bijections(matroid.elements()).filter { isAutomorphism(matroid, it) }
    val seen = mutableSetOf<List<E>>()
    var numOrdersTested = 0
    for (weightOrder in permutations(matroid.elements())) {
        if (weightOrder !in seen) {
            numOrdersTested++
            for (automorphism in automorphisms) {
                seen.add(weightOrder.map(automorphism::getValue))
            }
            worstCaseCompetitiveRatio = min(worstCaseCompetitiveRatio, evaluateAlgorithmGivenWeights(matroid, weightOrder, algorithmFactory))
            if (worstCaseCompetitiveRatio < target) {
                log("tested $numOrdersTested orders")
                return worstCaseCompetitiveRatio
            }
        }
    }
    log("tested all $numOrdersTested orders")
    return worstCaseCompetitiveRatio
}

fun <E> evaluateMatroidOnGreedy(matroid: Matroid<E>, thresholds: List<Int>, target: Double, log: (String) -> Unit): Double {
    var bestCompetitiveRatio = .0
    val timer = Timer()
    val matroid = CachedMatroid(matroid)
    log("cached matroid [${timer.lapSeconds()} seconds]")
    for (threshold in thresholds) {
       // val greedyAlgorithm1 = CachedAlgorithm(matroid, GreedyAlgorithm1(threshold))
       // println("cached greedy algorithm 1 [${timer.lapSeconds()} seconds]")
        val greedy1 = evaluateAlgorithm(matroid, target, log) { GreedyAlgorithm1(threshold) }
        log("greedy algorithm 1 with threshold $threshold -> competitive ratio = $greedy1 [${timer.lapSeconds()} seconds]")
        if (greedy1 > target) {
            return greedy1
        }
        //val greedyAlgorithm2 = CachedAlgorithm(matroid, GreedyAlgorithm2(threshold))
        //println("cached greedy algorithm 2 [${timer.lapSeconds()} seconds]")
        val greedy2 = evaluateAlgorithm(matroid, target, log) { GreedyAlgorithm2(threshold) }
        log("greedy algorithm 2 with threshold $threshold -> competitive ratio = $greedy2 [${timer.lapSeconds()} seconds]")
        if (greedy2 > target) {
            return greedy2
        }
        bestCompetitiveRatio = maxOf(bestCompetitiveRatio, greedy1, greedy2)
    }
    return bestCompetitiveRatio
}