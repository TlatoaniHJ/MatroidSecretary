package org.example.core

import com.gurobi.gurobi.GRBVar
import org.example.*
import kotlin.math.max

fun <E> buildDistributionComparisonLP(matroid: Matroid<E>, lpBuilder: GurobiLinearProgramBuilder): Pair<Pair<GRBVar, GRBVar>, List<Pair<List<E>, GRBVar>>> {
    val automorphisms = matroid.automorphisms()
    val orderIdentity = mutableMapOf<List<E>, List<E>>()
    for (order in permutations(matroid.elements())) {
        if (order !in orderIdentity) {
            for (automorphism in automorphisms) {
                val image = order.map(automorphism::getValue)
                orderIdentity[image] = order
            }
        }
    }
    val probabilities = mutableMapOf<Pair<List<E>, Int>, Expression<GRBVar>>()
    val probabilityVars = mutableListOf<Pair<List<E>, GRBVar>>()
    for (order in orderIdentity.values.toSet()) {
        for (rank in 1..matroid.rank()) {
            probabilities[Pair(order, rank)] = if (rank == matroid.rank()) lpBuilder.newVariable(VariableType.NONNEGATIVE) else Expression.zero()
            if (rank == matroid.rank()) {
                probabilityVars.add(Pair(order, lpBuilder.extractVariable(probabilities[Pair(order, rank)]!!)))
            }
        }
    }
    lpBuilder.newConstraint(probabilities.values.reduce(Expression<GRBVar>::plus), ConstraintType.EQUAL, 1.0 / automorphisms.size.toDouble())
    val baselineRatio = solveDistributionInLP(matroid, lpBuilder, matroid.rank()) { order ->
        val order = orderIdentity[order]!!
        val result = MutableList(order.size) { Expression.zero<GRBVar>() }
        var prevRank = 0
        for (j in order.indices) {
            for (rank in prevRank + 1..matroid.rank()) {
                result[j] += probabilities[Pair(order, rank)]!! / rank.toDouble()
            }
            prevRank = max(prevRank, matroid.rank(order.subList(0, j + 1).toSet()))
        }
        result
    }
    val truncatedRatio = solveDistributionInLP(matroid, lpBuilder, matroid.rank() - 1) { order ->
        val order = orderIdentity[order]!!
        val result = MutableList(order.size) { Expression.zero<GRBVar>() }
        var prevRank = 0
        for (j in order.indices) {
            for (rank in prevRank + 1 until matroid.rank()) {
                result[j] += probabilities[Pair(order, rank)]!! / rank.toDouble()
                if (rank + 1 == matroid.rank()) {
                    result[j] += probabilities[Pair(order, rank + 1)]!! / rank.toDouble()
                }
            }
            prevRank = max(prevRank, matroid.rank(order.subList(0, j + 1).toSet()))
            if (prevRank == matroid.rank() - 1) {
                break
            }
        }
        result
    }
    lpBuilder.optimize(OptimizationMode.MAXIMIZE, truncatedRatio - baselineRatio)
    return Pair(Pair(lpBuilder.extractVariable(baselineRatio), lpBuilder.extractVariable(truncatedRatio)), probabilityVars)
}

fun <E> solveDistributionInLP(matroid: Matroid<E>, lpBuilder: GurobiLinearProgramBuilder, truncateTo: Int, distribution: (List<E>) -> List<Expression<GRBVar>>): Expression<GRBVar> {
    val n = matroid.elements().size

    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val automorphisms = matroid.automorphisms()

    val orderedSubsetIdentity = mutableMapOf<List<E>, List<E>>()
    val conditionalWeights = mutableMapOf<List<E>, List<Expression<GRBVar>>>()
    for (orderedSubset in allOrderedSubsets) {
        if (orderedSubset !in orderedSubsetIdentity) {
            for (automorphism in automorphisms) {
                val image = orderedSubset.map(automorphism::getValue)
                orderedSubsetIdentity[image] = orderedSubset
            }
            conditionalWeights[orderedSubset] = List(orderedSubset.size) { Expression.zero() }
        }
    }

    for (order in orderedSubsetIdentity.values.toSet().filter { it.size == n }) {
        var distributionHere = List(n) { Expression.zero<GRBVar>() }
        for (automorphism in automorphisms) {
            val image = order.map(automorphism::getValue)
            distributionHere = distributionHere.zip(distribution(image)) { x, y -> x + y }
        }
        distributionHere = distributionHere.map { it / automorphisms.size.toDouble() }
        //println("order = $order, distributionHere = $distributionHere")

        for (automorphism in automorphisms) {
            val image = order.map(automorphism::getValue)
            for (indexSubset in subsets((0 until n).toSet())) {
                val orderedSubset = indexSubset.sorted().map(image::get)
                val weightSubset = indexSubset.sorted().map(distributionHere::get)
                if (orderedSubset in conditionalWeights) {
                    conditionalWeights[orderedSubset] =
                        conditionalWeights[orderedSubset]!!.zip(weightSubset, Expression<GRBVar>::plus)
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

    val dp = mutableMapOf<SummaryKey<E>, Expression<GRBVar>>()
    for (orderedSubset in orderedSubsetIdentity.values.toSet().sortedByDescending { it.size }) {
        val asSet = orderedSubset.toSet()
        for (span in subsets(asSet).map { spanIdentity[SpanKey<E>(asSet, it)]!! }.toSet()) {
            val key = SummaryKey(orderedSubset, span)
            if (orderedSubset.size == n) {
                dp[key] = Expression.zero()
            } else {
                var here = Expression.zero<GRBVar>()
                for (element in matroid.elements() - orderedSubset) {
                    for (position in 0..orderedSubset.size) {
                        val newOrderedSubsetRaw = orderedSubset.prefix(position) + element + orderedSubset.suffix(position)
                        val covered = newOrderedSubsetRaw.indices.filter { matroid.spans(span, newOrderedSubsetRaw[it]) }
                        val newOrderedSubset = orderedSubsetIdentity[newOrderedSubsetRaw]!!
                        val spanMapped = covered.map(newOrderedSubset::get).toSet()
                        val elementMapped = newOrderedSubset[position]
                        val noKey = SummaryKey(newOrderedSubset, spanIdentity[SpanKey(newOrderedSubset.toSet(), spanMapped)]!!)
                        val no = dp[noKey]!!
                        if (matroid.spans(span, element) || matroid.rank(span) >= truncateTo) {
                            here += no
                        } else {
                            ////println("ordered subset = $orderedSubset, element = $element, new ordered subset = $newOrderedSubset, span = $span, position = $position")
                            ////println("original span key = ${SpanKey(newOrderedSubset.toSet(), span + element)}")
                            val yesKey = SummaryKey(newOrderedSubset, spanIdentity[SpanKey(newOrderedSubset.toSet(), spanMapped + elementMapped)]!!)
                            val elementWeight = conditionalWeights[newOrderedSubset]!![position]
                            //println("key = $key, element = $element, position = $position | element weight = $elementWeight")
                            val yes = dp[yesKey]!! + elementWeight
                            here += lpBuilder.maxOfZeroToOne(yes, no)
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

/*fun <V> LinearProgramBuilder<V>.max(left: Expression<V>, right: Expression<V>): Expression<V> {
    val result = newVariable(VariableType.UNBOUNDED)
    newConstraint(result, ConstraintType.GREATER_OR_EQUAL, left)
    newConstraint(result, ConstraintType.GREATER_OR_EQUAL, right)
    val leftWeight = newVariable(VariableType.NONNEGATIVE)
    newConstraint(leftWeight, ConstraintType.LESS_OR_EQUAL, 1.0)

}*/

fun factorial(n: Int): Double = if (n == 0) 1.0 else { n.toDouble() * factorial(n - 1) }

fun <E> buildSymmetricDistributionLP(matroid: Matroid<E>, lpBuilder: GurobiLinearProgramBuilder) {
    val probabilities = mutableMapOf<Set<E>, Expression<GRBVar>>()
    for (subset in subsets(matroid.elements())) {
        if (matroid.rank(subset) > 0) {
            probabilities[subset] = lpBuilder.newVariable(VariableType.NONNEGATIVE)
        }
    }

    lpBuilder.newConstraint(probabilities.values.reduce(Expression<GRBVar>::plus), ConstraintType.EQUAL, 1.0)
    val ratio = solveDistributionInLPWeak(matroid, lpBuilder, matroid.rank()) { order ->
        val result = MutableList(order.size) { Expression.zero<GRBVar>() }
        for (limit in 1..order.size) {
            val here = (probabilities[order.prefix(limit).toSet()] ?: Expression.zero()) / (matroid.rank(order.prefix(limit).toSet()).toDouble() * factorial(limit) * factorial(order.size - limit))
            for (j in 0 until limit) {
                result[j] += here
            }
        }
        result
    }
    lpBuilder.optimize(OptimizationMode.MINIMIZE, ratio)
}

fun <E> solveDistributionInLPWeak(matroid: Matroid<E>, lpBuilder: GurobiLinearProgramBuilder, truncateTo: Int, distribution: (List<E>) -> List<Expression<GRBVar>>): Expression<GRBVar> {
    val n = matroid.elements().size

    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val automorphisms = matroid.automorphisms()

    val orderedSubsetIdentity = mutableMapOf<List<E>, List<E>>()
    val conditionalWeights = mutableMapOf<List<E>, List<Expression<GRBVar>>>()
    for (orderedSubset in allOrderedSubsets) {
        if (orderedSubset !in orderedSubsetIdentity) {
            for (automorphism in automorphisms) {
                val image = orderedSubset.map(automorphism::getValue)
                orderedSubsetIdentity[image] = orderedSubset
            }
            conditionalWeights[orderedSubset] = List(orderedSubset.size) { Expression.zero() }
        }
    }

    for (order in orderedSubsetIdentity.values.toSet().filter { it.size == n }) {
        var distributionHere = List(n) { Expression.zero<GRBVar>() }
        for (automorphism in automorphisms) {
            val image = order.map(automorphism::getValue)
            distributionHere = distributionHere.zip(distribution(image)) { x, y -> x + y }
        }
        distributionHere = distributionHere.map { it / automorphisms.size.toDouble() }
        //println("order = $order, distributionHere = $distributionHere")

        for (automorphism in automorphisms) {
            val image = order.map(automorphism::getValue)
            for (indexSubset in subsets((0 until n).toSet())) {
                val orderedSubset = indexSubset.sorted().map(image::get)
                val weightSubset = indexSubset.sorted().map(distributionHere::get)
                if (orderedSubset in conditionalWeights) {
                    conditionalWeights[orderedSubset] =
                        conditionalWeights[orderedSubset]!!.zip(weightSubset, Expression<GRBVar>::plus)
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

    val dp = mutableMapOf<SummaryKey<E>, Expression<GRBVar>>()
    for (orderedSubset in orderedSubsetIdentity.values.toSet().sortedByDescending { it.size }) {
        val asSet = orderedSubset.toSet()
        for (span in subsets(asSet).map { spanIdentity[SpanKey<E>(asSet, it)]!! }.toSet()) {
            val key = SummaryKey(orderedSubset, span)
            if (orderedSubset.size == n) {
                dp[key] = Expression.zero()
            } else {
                var here = Expression.zero<GRBVar>()
                for (element in matroid.elements() - orderedSubset) {
                    for (position in 0..orderedSubset.size) {
                        val newOrderedSubsetRaw = orderedSubset.prefix(position) + element + orderedSubset.suffix(position)
                        val covered = newOrderedSubsetRaw.indices.filter { matroid.spans(span, newOrderedSubsetRaw[it]) }
                        val newOrderedSubset = orderedSubsetIdentity[newOrderedSubsetRaw]!!
                        val spanMapped = covered.map(newOrderedSubset::get).toSet()
                        val elementMapped = newOrderedSubset[position]
                        val noKey = SummaryKey(newOrderedSubset, spanIdentity[SpanKey(newOrderedSubset.toSet(), spanMapped)]!!)
                        val no = dp[noKey]!!
                        if (matroid.spans(span, element) || matroid.rank(span) >= truncateTo) {
                            here += no
                        } else {
                            ////println("ordered subset = $orderedSubset, element = $element, new ordered subset = $newOrderedSubset, span = $span, position = $position")
                            ////println("original span key = ${SpanKey(newOrderedSubset.toSet(), span + element)}")
                            val yesKey = SummaryKey(newOrderedSubset, spanIdentity[SpanKey(newOrderedSubset.toSet(), spanMapped + elementMapped)]!!)
                            val elementWeight = conditionalWeights[newOrderedSubset]!![position]
                            //println("key = $key, element = $element, position = $position | element weight = $elementWeight")
                            val yes = dp[yesKey]!! + elementWeight
                            val weakMax = lpBuilder.newVariable(VariableType.UNBOUNDED)
                            lpBuilder.newConstraint(weakMax, ConstraintType.GREATER_OR_EQUAL, yes)
                            lpBuilder.newConstraint(weakMax, ConstraintType.GREATER_OR_EQUAL, no)
                            here += weakMax
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

fun <E> buildTightInstanceLP(matroid: Matroid<E>, lpBuilder: GurobiLinearProgramBuilder) {
    val automorphisms = matroid.automorphisms()
    val orderIdentity = mutableMapOf<List<E>, List<E>>()
    for (order in permutations(matroid.elements())) {
        if (order !in orderIdentity) {
            for (automorphism in automorphisms) {
                val image = order.map(automorphism::getValue)
                orderIdentity[image] = order
            }
        }
    }
    val probabilities = mutableMapOf<Pair<List<E>, Int>, Expression<GRBVar>>()
    for (order in orderIdentity.values.toSet()) {
        for (rank in 1..matroid.rank()) {
            probabilities[Pair(order, rank)] = if (order.prefix(rank).toSet() in matroid) lpBuilder.newVariable(VariableType.NONNEGATIVE) else Expression.zero()
        }
    }
    lpBuilder.newConstraint(probabilities.values.reduce(Expression<GRBVar>::plus), ConstraintType.EQUAL, 1.0 / automorphisms.size.toDouble())
    val ratio = solveDistributionInLPWeak(matroid, lpBuilder, matroid.rank()) { order ->
        val order = orderIdentity[order]!!
        val result = MutableList(order.size) { Expression.zero<GRBVar>() }
        var prevRank = 0
        for (j in order.indices) {
            for (rank in prevRank + 1..matroid.rank()) {
                result[j] += probabilities[Pair(order, rank)]!! / rank.toDouble()
            }
            prevRank = max(prevRank, matroid.rank(order.subList(0, j + 1).toSet()))
        }
        result
    }
    lpBuilder.optimize(OptimizationMode.MINIMIZE, ratio)
}


fun <E, V> buildMatroidSecretaryLPTight(matroid: Matroid<E>, lpBuilder: LinearProgramBuilder<V>, target: Double? = null) {
    //println("building lp for matroid = $matroid")
    val timer = Timer()

    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val automorphisms = matroid.automorphisms()
    val orderedSubsetIdentity = mutableMapOf<List<E>, List<E>>()
    for (orderedSubset in allOrderedSubsets) {
        if (orderedSubset !in orderedSubsetIdentity) {
            for (automorphism in automorphisms) {
                val image = orderedSubset.map(automorphism::getValue)
                orderedSubsetIdentity[image] = orderedSubset
            }
        }
    }

    // println("computed ordered subset identity for matroid = $matroid [${timer.lapSeconds()} seconds]")

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

    //println("computed span identity for matroid = $matroid [${timer.lapSeconds()} seconds]")

    val rank = matroid.rank()

    val yes = mutableMapOf<VariableKey<E>, Expression<V>>()
    val no = mutableMapOf<VariableKey<E>, Expression<V>>()
    for (orderedSubset in orderedSubsetIdentity.values.toSet()) {
        for ((index, elem) in orderedSubset.withIndex()) {
            val prev = (orderedSubset - elem).toSet()
            for (prevSpan in subsets(prev).map { spanIdentity[SpanKey<E>(prev, it)]!! }.toSet()) {
                val key = VariableKey(orderedSubset, index, prevSpan)
                val yesZero = matroid.spans(prevSpan, elem) || matroid.rank(orderedSubset.subList(0, index).toSet()) == rank
                yes[key] = if (yesZero) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
                no[key] = if (!yesZero && orderedSubset.size == matroid.size()) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
            }
        }
    }

    val summary = mutableMapOf<SummaryKey<E>, Expression<V>>()
    for ((key, expression) in yes) {
        val span = spanIdentity[SpanKey(key.orderedSubset.toSet(), key.prevSpan + key.orderedSubset[key.index])]!!
        val summaryKey = SummaryKey(key.orderedSubset, span)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + expression
    }
    for ((key, expression) in no) {
        val span = spanIdentity[SpanKey(key.orderedSubset.toSet(), key.prevSpan)]!!
        val summaryKey = SummaryKey(key.orderedSubset, span)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + expression
    }
    for (key in summary.keys.toSet()) {
        val variable = lpBuilder.newVariable(VariableType.UNBOUNDED)
        lpBuilder.newConstraint(variable, ConstraintType.EQUAL, summary[key]!!)
        summary[key] = variable
    }
    summary[SummaryKey(listOf(), setOf())] = Expression.fromConstant(1.0)

    for ((key, yesExpr) in yes) {
        val noExpr = no[key]!!
        val prevOrderedSubset = key.orderedSubset - key.orderedSubset[key.index]
        val covered = prevOrderedSubset.indices.filter { matroid.spans(key.prevSpan, prevOrderedSubset[it]) }
        val prevIdentity = orderedSubsetIdentity[prevOrderedSubset]!!
        val coveredImage = covered.map(prevIdentity::get).toSet()
        val prevSpanIdentity = spanIdentity[SpanKey(prevIdentity.toSet(), coveredImage)]!!

        val prevKey = SummaryKey(prevIdentity, prevSpanIdentity)
        //println("prevKey = $prevKey")
        val prevSummary = summary[prevKey]!!
        lpBuilder.newConstraint(yesExpr + noExpr, ConstraintType.EQUAL, prevSummary / (matroid.elements().size - prevOrderedSubset.size).toDouble())
    }

    val yesTotals = mutableMapOf<PartialVariableKey<E>, Expression<V>>()
    for ((key, yesExpr) in yes) {
        val partialKey = PartialVariableKey(key.orderedSubset, key.index)
        yesTotals[partialKey] = (yesTotals[partialKey] ?: Expression.zero()) + yesExpr
    }
    for (key in yesTotals.keys.toSet()) {
        val variable = lpBuilder.newVariable(VariableType.UNBOUNDED)
        lpBuilder.newConstraint(variable, ConstraintType.EQUAL, yesTotals[key]!!)
        yesTotals[key] = variable
    }

    val competitiveRatio = if (target == null) {
        val c = lpBuilder.newVariable(VariableType.UNBOUNDED)
        lpBuilder.optimize(OptimizationMode.MAXIMIZE, c)
        c
    } else {
        Expression.fromConstant(target)
    }

    val seenOrders = mutableSetOf<List<E>>()
    for (order in permutations(matroid.elements())) {
        if (order !in seenOrders) {
            for (automorphism in automorphisms) {
                seenOrders.add(order.map(automorphism::getValue))
            }
            val probabilities = MutableList(order.size) { Expression.zero<V>() }
            for (j in order.indices) {
                for (partialOrder in subsets(order.indices.toSet())) {
                    if (j in partialOrder) {
                        val partialOrder = partialOrder.sorted()
                        val orderedSubset = partialOrder.map(order::get)
                        val index = partialOrder.indexOf(j)
                        val key = PartialVariableKey(orderedSubsetIdentity[orderedSubset]!!, index)
                        probabilities[j] += yesTotals[key]!!
                    }
                }
            }
            var prefixSum = Expression.zero<V>()
            for (threshold in 1..order.size) {
                prefixSum += probabilities[threshold - 1]
                val rank = matroid.rank(order.subList(0, threshold).toSet())
                val prevRank = matroid.rank(order.subList(0, threshold - 1).toSet())
                if (rank == threshold) {
                    if (rank < matroid.rank()) {
                        val variable = lpBuilder.newVariable(VariableType.UNBOUNDED)
                        lpBuilder.newConstraint(variable, ConstraintType.EQUAL, prefixSum)
                        prefixSum = variable
                    }
                    lpBuilder.newConstraint(
                        prefixSum,
                        ConstraintType.GREATER_OR_EQUAL,
                        rank.toDouble() * competitiveRatio
                    )
                }
            }
        }
    }
}

fun <E, V> buildMatroidSecretaryLPTightOneRank(matroid: Matroid<E>, lpBuilder: LinearProgramBuilder<V>, importantRank: Int, target: Double? = null) {
    //println("building lp for matroid = $matroid")
    val timer = Timer()

    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val automorphisms = matroid.automorphisms()
    val orderedSubsetIdentity = mutableMapOf<List<E>, List<E>>()
    for (orderedSubset in allOrderedSubsets) {
        if (orderedSubset !in orderedSubsetIdentity) {
            for (automorphism in automorphisms) {
                val image = orderedSubset.map(automorphism::getValue)
                orderedSubsetIdentity[image] = orderedSubset
            }
        }
    }

    // println("computed ordered subset identity for matroid = $matroid [${timer.lapSeconds()} seconds]")

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

    //println("computed span identity for matroid = $matroid [${timer.lapSeconds()} seconds]")

    val rank = matroid.rank()

    val yes = mutableMapOf<VariableKey<E>, Expression<V>>()
    val no = mutableMapOf<VariableKey<E>, Expression<V>>()
    for (orderedSubset in orderedSubsetIdentity.values.toSet()) {
        for ((index, elem) in orderedSubset.withIndex()) {
            val prev = (orderedSubset - elem).toSet()
            for (prevSpan in subsets(prev).map { spanIdentity[SpanKey<E>(prev, it)]!! }.toSet()) {
                val key = VariableKey(orderedSubset, index, prevSpan)
                val yesZero = matroid.spans(prevSpan, elem) || matroid.rank(orderedSubset.subList(0, index).toSet()) == rank
                yes[key] = if (yesZero) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
                no[key] = if (!yesZero && orderedSubset.size == matroid.size()) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
            }
        }
    }

    val summary = mutableMapOf<SummaryKey<E>, Expression<V>>()
    for ((key, expression) in yes) {
        val span = spanIdentity[SpanKey(key.orderedSubset.toSet(), key.prevSpan + key.orderedSubset[key.index])]!!
        val summaryKey = SummaryKey(key.orderedSubset, span)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + expression
    }
    for ((key, expression) in no) {
        val span = spanIdentity[SpanKey(key.orderedSubset.toSet(), key.prevSpan)]!!
        val summaryKey = SummaryKey(key.orderedSubset, span)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + expression
    }
    for (key in summary.keys.toSet()) {
        val variable = lpBuilder.newVariable(VariableType.UNBOUNDED)
        lpBuilder.newConstraint(variable, ConstraintType.EQUAL, summary[key]!!)
        summary[key] = variable
    }
    summary[SummaryKey(listOf(), setOf())] = Expression.fromConstant(1.0)

    for ((key, yesExpr) in yes) {
        val noExpr = no[key]!!
        val prevOrderedSubset = key.orderedSubset - key.orderedSubset[key.index]
        val covered = prevOrderedSubset.indices.filter { matroid.spans(key.prevSpan, prevOrderedSubset[it]) }
        val prevIdentity = orderedSubsetIdentity[prevOrderedSubset]!!
        val coveredImage = covered.map(prevIdentity::get).toSet()
        val prevSpanIdentity = spanIdentity[SpanKey(prevIdentity.toSet(), coveredImage)]!!

        val prevKey = SummaryKey(prevIdentity, prevSpanIdentity)
        //println("prevKey = $prevKey")
        val prevSummary = summary[prevKey]!!
        lpBuilder.newConstraint(yesExpr + noExpr, ConstraintType.EQUAL, prevSummary / (matroid.elements().size - prevOrderedSubset.size).toDouble())
    }

    val yesTotals = mutableMapOf<PartialVariableKey<E>, Expression<V>>()
    for ((key, yesExpr) in yes) {
        val partialKey = PartialVariableKey(key.orderedSubset, key.index)
        yesTotals[partialKey] = (yesTotals[partialKey] ?: Expression.zero()) + yesExpr
    }
    for (key in yesTotals.keys.toSet()) {
        val variable = lpBuilder.newVariable(VariableType.UNBOUNDED)
        lpBuilder.newConstraint(variable, ConstraintType.EQUAL, yesTotals[key]!!)
        yesTotals[key] = variable
    }

    val competitiveRatio = if (target == null) {
        val c = lpBuilder.newVariable(VariableType.UNBOUNDED)
        lpBuilder.optimize(OptimizationMode.MAXIMIZE, c)
        c
    } else {
        Expression.fromConstant(target)
    }

    val seenOrders = mutableSetOf<List<E>>()
    for (order in permutations(matroid.elements())) {
        if (order !in seenOrders) {
            for (automorphism in automorphisms) {
                seenOrders.add(order.map(automorphism::getValue))
            }
            val probabilities = MutableList(order.size) { Expression.zero<V>() }
            for (j in order.indices) {
                for (partialOrder in subsets(order.indices.toSet())) {
                    if (j in partialOrder) {
                        val partialOrder = partialOrder.sorted()
                        val orderedSubset = partialOrder.map(order::get)
                        val index = partialOrder.indexOf(j)
                        val key = PartialVariableKey(orderedSubsetIdentity[orderedSubset]!!, index)
                        probabilities[j] += yesTotals[key]!!
                    }
                }
            }
            var prefixSum = Expression.zero<V>()
            for (threshold in 1..order.size) {
                prefixSum += probabilities[threshold - 1]
                val rank = matroid.rank(order.subList(0, threshold).toSet())
                val prevRank = matroid.rank(order.subList(0, threshold - 1).toSet())
                if (rank == threshold && rank == importantRank) {
                    if (false) {
                        val variable = lpBuilder.newVariable(VariableType.UNBOUNDED)
                        lpBuilder.newConstraint(variable, ConstraintType.EQUAL, prefixSum)
                        prefixSum = variable
                    }
                    lpBuilder.newConstraint(
                        prefixSum,
                        ConstraintType.GREATER_OR_EQUAL,
                        rank.toDouble() * competitiveRatio
                    )
                }
            }
        }
    }
}