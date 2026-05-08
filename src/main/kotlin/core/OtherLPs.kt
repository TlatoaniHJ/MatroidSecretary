package org.example.core

import org.example.*

/*fun <V> singleElementLP(n: Int, k: Int, lpBuilder: LinearProgramBuilder<V>) {
    val probabilityRemain = mutableListOf<Expression<V>>(Expression.fromConstant(1.0))
    val probabilitySelect = mutableListOf<List<Expression<V>>>()
    for (t in 0 until n) {
        for (position in 0..t) {
            val prob = lpBuilder.newVariable(VariableType.NONNEGATIVE)
            lpBuilder.newConstraint(prob, ConstraintType.LESS_OR_EQUAL, probabilityRemain[t] / (n - ))
        }
    }
}*/

fun <E, V> buildMatroidSecretaryLPOnlyFullRank(matroid: Matroid<E>, lpBuilder: LinearProgramBuilder<V>, target: Double? = null) {
    println("buildMatroidSecretaryLPOnlyFullRank")
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
                if (rank > prevRank && rank == matroid.rank()) {
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