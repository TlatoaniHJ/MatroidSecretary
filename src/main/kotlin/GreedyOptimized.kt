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
fun evaluateDP(
    bMatroid: BitmaskMatroid<*>,
    weightOrderInds: IntArray,
    threshold: Int,
    isGreedy1: Boolean
): Double {
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
                        if (k + 1 > threshold) { // k+1 is size of weightOrder up to current element
                            if (isGreedy1) {
                                val spannedByHeavier = (bMatroid.spans[heavierSeen] and (1 shl e)) != 0
                                val spannedByTaken = (bMatroid.spans[takenMask] and (1 shl e)) != 0
                                takes = !spannedByHeavier && !spannedByTaken
                            } else {
                                val spannedByUnion = (bMatroid.spans[heavierSeen or takenMask] and (1 shl e)) != 0
                                takes = !spannedByUnion
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
        var worstRatio1 = 1.0
        var worstRatio2 = 1.0

        for (order in canonicalOrders) {
            val ratio1 = evaluateDP(bMatroid, order, threshold, true)
            if (ratio1 < worstRatio1) worstRatio1 = ratio1

            val ratio2 = evaluateDP(bMatroid, order, threshold, false)
            if (ratio2 < worstRatio2) worstRatio2 = ratio2
        }

        log("greedy algorithm 1 with threshold $threshold -> competitive ratio = $worstRatio1 [${timer.lapSeconds()} seconds]")
        log("greedy algorithm 2 with threshold $threshold -> competitive ratio = $worstRatio2 [${timer.lapSeconds()} seconds]")

        bestCompetitiveRatio = maxOf(bestCompetitiveRatio, worstRatio1, worstRatio2)
    }

    return bestCompetitiveRatio
}