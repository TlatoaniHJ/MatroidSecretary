package org.example

import kotlin.math.max
import kotlin.math.min

//import org.example.*

fun <E> solveForDistributionSymmetric(matroid: Matroid<E>, distribution: (List<E>) -> List<Double>): Double {
    val n = matroid.elements().size

    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val automorphisms = matroid.automorphisms()

    val orderedSubsetIdentity = mutableMapOf<List<E>, List<E>>()
    val conditionalWeights = mutableMapOf<List<E>, List<Double>>()
    for (orderedSubset in allOrderedSubsets) {
        if (orderedSubset !in orderedSubsetIdentity) {
            for (automorphism in automorphisms) {
                val image = orderedSubset.map(automorphism::getValue)
                orderedSubsetIdentity[image] = orderedSubset
            }
            conditionalWeights[orderedSubset] = List(orderedSubset.size) { .0 }
        }
    }

    for (order in orderedSubsetIdentity.values.toSet().filter { it.size == n }) {
        var distributionHere = List(n) { .0 }
        for (automorphism in automorphisms) {
            val image = order.map(automorphism::getValue)
            distributionHere = distributionHere.zip(distribution(image)) { x, y -> x + y }
        }
        distributionHere = distributionHere.map { it / automorphisms.size.toDouble() }
        //println("order = $order, distributionHere = $distributionHere")

        for (automorphism in automorphisms) {
            val image = order.map(automorphism::getValue)
            for (indexSubset in subsets((0 until n).toSet())) {
                val orderedSubset =indexSubset.sorted().map(image::get)
                val weightSubset = indexSubset.sorted().map(distributionHere::get)
                if (orderedSubset in conditionalWeights) {
                    conditionalWeights[orderedSubset] =
                        conditionalWeights[orderedSubset]!!.zip(weightSubset, Double::plus)
                }
            }
        }
        /*for (indexSubset in subsets((0 until n).toSet())) {
            val orderedSubset = orderedSubsetIdentity[indexSubset.sorted().map(order::get)]!!
            val weightSubset = indexSubset.sorted().map(distributionHere::get)
            conditionalWeights[orderedSubset] = conditionalWeights[orderedSubset]!!.zip(weightSubset, Double::plus)
        }*/
    }
    //println(conditionalWeights)

    // //println("computed ordered subset identity for matroid = $matroid [${timer.lapSeconds()} seconds]")

    val spanIdentity = mutableMapOf<SpanKey<E>, Set<E>>()
    for (seen in allSubsets) {
        val notSeen = matroid.elements() - seen
        val minors = mutableListOf<Pair<Set<E>, Matroid<E>>>()
        for (subset in subsets(seen)) {
            val minor = matroid.minor(subset, notSeen)
            val existing = minors.find { minor.same(it.second) }?.first
            if (existing == null) {
                minors.add(Pair(subset, minor))
            }
            spanIdentity[SpanKey(seen, subset)] = existing ?: subset
        }
    }

    val dp = mutableMapOf<SummaryKey<E>, Double>()
    for (orderedSubset in orderedSubsetIdentity.values.toSet().sortedByDescending { it.size }) {
        val asSet = orderedSubset.toSet()
        for (span in subsets(asSet).map { spanIdentity[SpanKey<E>(asSet, it)]!! }.toSet()) {
            val key = SummaryKey(orderedSubset, span)
            if (orderedSubset.size == n) {
                dp[key] = .0
            } else {
                var here = .0
                for (element in matroid.elements() - orderedSubset) {
                    for (position in 0..orderedSubset.size) {
                        val newOrderedSubsetRaw = orderedSubset.prefix(position) + element + orderedSubset.suffix(position)
                        val covered = newOrderedSubsetRaw.indices.filter { matroid.spans(span, newOrderedSubsetRaw[it]) }
                        val newOrderedSubset = orderedSubsetIdentity[newOrderedSubsetRaw]!!
                        val spanMapped = covered.map(newOrderedSubset::get).toSet()
                        val elementMapped = newOrderedSubset[position]
                        val noKey = SummaryKey(newOrderedSubset, spanIdentity[SpanKey(newOrderedSubset.toSet(), spanMapped)]!!)
                        val no = dp[noKey]!!
                        if (matroid.spans(span, element)) { // equivalent to matroid.spans(spanMapped, elementMapped)
                            here += no
                        } else {
                            ////println("ordered subset = $orderedSubset, element = $element, new ordered subset = $newOrderedSubset, span = $span, position = $position")
                            ////println("original span key = ${SpanKey(newOrderedSubset.toSet(), span + element)}")
                            val yesKey = SummaryKey(newOrderedSubset, spanIdentity[SpanKey(newOrderedSubset.toSet(), spanMapped + elementMapped)]!!)
                            val elementWeight = conditionalWeights[newOrderedSubset]!![position]
                            //println("key = $key, element = $element, position = $position | element weight = $elementWeight")
                            val yes = dp[yesKey]!! + elementWeight
                            here += max(yes, no)
                        }
                    }
                }
                dp[key] = here / (n - orderedSubset.size).toDouble()
                //println("dp[$key] = ${dp[key]}")
            }
        }
    }
    return dp[SummaryKey(listOf(), setOf())]!!
}

fun <E> solveForDistribution(matroid: Matroid<E>, distribution: (List<E>) -> List<Double>): Double {
    val n = matroid.elements().size

    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val conditionalWeights = mutableMapOf<List<E>, List<Double>>()
    for (orderedSubset in allOrderedSubsets) {
        conditionalWeights[orderedSubset] = List(orderedSubset.size) { .0 }
    }
    for (order in permutations(matroid.elements())) {
        val distributionHere = distribution(order)
        for (indexSubset in subsets((0 until n).toSet())) {
            val orderedSubset =indexSubset.sorted().map(order::get)
            val weightSubset = indexSubset.sorted().map(distributionHere::get)
            conditionalWeights[orderedSubset] = conditionalWeights[orderedSubset]!!.zip(weightSubset, Double::plus)
        }
    }

    val dp = mutableMapOf<SummaryKey<E>, Double>()
    val dpMeasurements = mutableMapOf<Pair<Int, Int>, Double>()
    for (orderedSubset in allOrderedSubsets.sortedByDescending { it.size }) {
        val asSet = orderedSubset.toSet()
        for (span in subsets(asSet).filter { it in matroid }) {
            val key = SummaryKey(orderedSubset, span)
            if (orderedSubset.size == n) {
                dp[key] = .0
            } else {
                var here = .0
                for (element in matroid.elements() - orderedSubset) {
                    for (position in 0..orderedSubset.size) {
                        val newOrderedSubset = orderedSubset.prefix(position) + element + orderedSubset.suffix(position)
                        val noKey = SummaryKey(newOrderedSubset, span)
                        val no = dp[noKey]!!
                        if (matroid.spans(span, element)) { // equivalent to matroid.spans(spanMapped, elementMapped)
                            here += no
                        } else {
                            ////println("ordered subset = $orderedSubset, element = $element, new ordered subset = $newOrderedSubset, span = $span, position = $position")
                            ////println("original span key = ${SpanKey(newOrderedSubset.toSet(), span + element)}")
                            val yesKey = SummaryKey(newOrderedSubset, span + element)
                            val elementWeight = conditionalWeights[newOrderedSubset]!![position]
                            val yes = dp[yesKey]!! + elementWeight
                            here += max(yes, no)
                        }
                    }
                }
                dp[key] = here / (n - orderedSubset.size).toDouble()
            }
            dpMeasurements[Pair(key.orderedSubset.size, key.span.size)] = dp[key]!!
            ////println("dp[$key] = ${dp[key]}")
        }
    }
    for (a in n downTo 0) {
        for (b in 0..min(a, matroid.rank())) {
            //println("dp[$a, $b] = ${dpMeasurements[Pair(a, b)]}")
        }
    }
    return dp[SummaryKey(listOf(), setOf())]!!
}