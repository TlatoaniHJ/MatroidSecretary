package org.example

import kotlin.math.max
import kotlin.math.min

/**
 * A highly optimized, flattened representation of a Matroid using bitmasks.
 * Elements are mapped to indices 0..n-1.
 */
class BitmaskMatroid<E>(val original: Matroid<E>) {
    val elements: List<E> = original.elements().toList()
    val n: Int = elements.size

    // rank[mask] gives the rank of the subset represented by the mask
    val rank = IntArray(1 shl n)

    // spans[mask] contains a bitmask of all elements spanned by the subset
    val spans = IntArray(1 shl n)

    init {
        for (mask in 0 until (1 shl n)) {
            val subset = maskToSet(mask)
            rank[mask] = original.rank(subset)

            var spanMask = 0
            for (e in 0 until n) {
                if (original.spans(subset, elements[e])) {
                    spanMask = spanMask or (1 shl e)
                }
            }
            spans[mask] = spanMask
        }
    }

    private fun maskToSet(mask: Int): Set<E> {
        val set = mutableSetOf<E>()
        for (i in 0 until n) {
            if ((mask and (1 shl i)) != 0) {
                set.add(elements[i])
            }
        }
        return set
    }
}

/**
 * Core DP function evaluating the exact competitive ratio of a greedy algorithm
 * on a specific canonical weight order.
 */
fun evaluateDP(bMatroid: BitmaskMatroid<*>, weightOrderInds: IntArray, threshold: Int, algorithmType: Int): Double {
    val n = bMatroid.n
    val better = IntArray(n)
    var currentBetterMask = 0

    // Precompute 'better' mask: elements with higher weight than 'e'
    for (i in 0 until n) {
        val e = weightOrderInds[i]
        better[e] = currentBetterMask
        currentBetterMask = currentBetterMask or (1 shl e)
    }

    // DP State: (seenMask << n) | takenMask
    // Value: Number of permutations reaching this exact state
    val dp = LongArray(1 shl (2 * n))
    dp[0] = 1L

    // Traverse topologically
    for (seenMask in 0 until (1 shl n)) {
        val k = seenMask.countOneBits()
        if (k == n) continue // Complete permutation reached

        // Iterate takenMask strictly as submasks of seenMask
        var takenMask = seenMask
        while (true) {
            val stateIdx = (seenMask shl n) or takenMask
            val count = dp[stateIdx]

            if (count > 0L) {
                for (e in 0 until n) {
                    if ((seenMask and (1 shl e)) == 0) { // e is newly arrived
                        val heavierSeen = seenMask and better[e]

                        var takes = false

                        // 0: Standard Greedy1
                        // 1: Standard Greedy2
                        // 2: Rank-Based Threshold (Uses Greedy1 logic after threshold)
                        // 3: Panic Greedy (Uses Greedy1 logic, but overrides if running out of time)

                        val timeThresholdMet = (k + 1 > threshold)
                        val rankThresholdMet = (bMatroid.rank[seenMask] >= threshold)

                        // Determine if we are past the observation phase based on the algorithm type
                        val pastThreshold = if (algorithmType == 2) rankThresholdMet else timeThresholdMet

                        if (pastThreshold) {
                            val spannedByTaken = (bMatroid.spans[takenMask] and (1 shl e)) != 0
                            val spannedByHeavier = (bMatroid.spans[heavierSeen] and (1 shl e)) != 0

                            when (algorithmType) {
                                0 -> { // Greedy 1
                                    takes = !spannedByHeavier && !spannedByTaken
                                }
                                1 -> { // Greedy 2
                                    val spannedByUnion = (bMatroid.spans[heavierSeen or takenMask] and (1 shl e)) != 0
                                    takes = !spannedByUnion
                                }
                                2 -> { // Rank-Based Threshold (Acts like Greedy1 once triggered)
                                    takes = !spannedByHeavier && !spannedByTaken
                                }
                                3 -> { // Panic Greedy
                                    val maxRank = bMatroid.rank[(1 shl n) - 1] // Rank of the entire matroid
                                    val currentRank = bMatroid.rank[takenMask]
                                    val elementsLeft = n - k // Total elements remaining (including 'e')

                                    val panicMode = elementsLeft <= (maxRank - currentRank)

                                    if (panicMode) {
                                        takes = !spannedByTaken // Ignore heavier elements, just grab it if independent
                                    } else {
                                        takes = !spannedByHeavier && !spannedByTaken // Standard Greedy1
                                    }
                                }
                            }
                        }

                        val nextSeen = seenMask or (1 shl e)
                        val nextTaken = takenMask or (if (takes) (1 shl e) else 0)
                        val nextStateIdx = (nextSeen shl n) or nextTaken

                        dp[nextStateIdx] += count
                    }
                }
            }
            if (takenMask == 0) break
            takenMask = (takenMask - 1) and seenMask // Gosper's submask iteration
        }
    }

    val fullSeenMask = (1 shl n) - 1
    val selectionProbabilities = DoubleArray(n)
    val factorial = (1..n).fold(1.0) { acc, i -> acc * i } // exact n!

    // Accumulate final selection probabilities
    for (takenMask in 0 until (1 shl n)) {
        val count = dp[(fullSeenMask shl n) or takenMask]
        if (count > 0L) {
            for (e in 0 until n) {
                if ((takenMask and (1 shl e)) != 0) {
                    selectionProbabilities[e] += count.toDouble() / factorial
                }
            }
        }
    }

    // Compute Competitive Ratio
    var competitiveRatio = 1.0
    var prefixMask = 0
    for (j in 1..n) {
        val e = weightOrderInds[j - 1]
        prefixMask = prefixMask or (1 shl e)
        val rank = bMatroid.rank[prefixMask]

        if (rank > 0) {
            var totalValue = 0.0
            for (i in 0 until j) {
                totalValue += selectionProbabilities[weightOrderInds[i]]
            }
            competitiveRatio = min(competitiveRatio, totalValue / rank.toDouble())
        }
    }

    return competitiveRatio
}

fun <E> evaluateMatroidOnGreedyOptimized(
    matroid: Matroid<E>,
    thresholds: List<Int>,
    log: (String) -> Unit
): Double {
    val timer = Timer()
    val bMatroid = BitmaskMatroid(matroid)
    log("built bitmask matroid [${timer.lapSeconds()} seconds]")

    val n = bMatroid.n
    val elements = bMatroid.elements

    // Map automorphisms to numerical indices
    val indexAutomorphisms = mutableListOf<IntArray>()
    val automorphisms = bijections(elements.toSet()).filter { isAutomorphism(matroid, it) }
    for (bij in automorphisms) {
        val mapping = IntArray(n)
        for (i in 0 until n) {
            mapping[i] = elements.indexOf(bij[elements[i]])
        }
        indexAutomorphisms.add(mapping)
    }

    log("computed index automorphisms [${timer.lapSeconds()} seconds]")

    // Reduce permutations to Canonical Weight Orders
    val seenOrders = mutableSetOf<List<Int>>()
    val canonicalOrders = mutableListOf<IntArray>()

    val baseOrder = (0 until n).toList()
    for (order in permutations(baseOrder.toSet())) {
        if (order !in seenOrders) {
            canonicalOrders.add(order.toIntArray())
            for (auto in indexAutomorphisms) {
                val mappedOrder = order.map { auto[it] }
                seenOrders.add(mappedOrder)
            }
        }
    }
    log("reduced to ${canonicalOrders.size} canonical orders [${timer.lapSeconds()} seconds]")

    var bestCompetitiveRatio = 0.0

    for (threshold in thresholds) {
        // Track the worst-case (minimum) competitive ratio for each of the 4 algorithms
        val worstRatios = DoubleArray(4) { 1.0 }

        for (order in canonicalOrders) {
            for (algoType in 0..3) {
                val ratio = evaluateDP(bMatroid, order, threshold, algoType)
                if (ratio < worstRatios[algoType]) {
                    worstRatios[algoType] = ratio
                }
            }
        }

        val algorithmNames = arrayOf(
            "Standard Greedy 1",
            "Standard Greedy 2",
            "Rank-Based Threshold",
            "Panic Greedy"
        )

        for (algoType in 0..3) {
            log("${algorithmNames[algoType]} with threshold $threshold -> competitive ratio = ${worstRatios[algoType]} [${timer.lapSeconds()} seconds]")
        }

        bestCompetitiveRatio = maxOf(bestCompetitiveRatio, worstRatios.maxOrNull() ?: 0.0)
    }

    return bestCompetitiveRatio
}