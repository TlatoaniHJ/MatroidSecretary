package org.example

fun <E, V> buildMatroidSecretaryLPOld(matroid: Matroid<E>, lpBuilder: LinearProgramBuilder<V>) {
    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    // map from S to some T such that span(S) = span(T)
    val spanIdentity = mutableMapOf<Set<E>, Set<E>>()
    for (subset in allSubsets) {
        val span = matroid.span(subset)
        if (span !in spanIdentity) {
            spanIdentity[span] = subset
        }
        spanIdentity[subset] = spanIdentity[span]!!
    }

    val automorphisms = bijections(matroid.elements()).filter { isAutomorphism(matroid, it) }
    val orderedSubsetIdentity = mutableMapOf<List<E>, List<E>>()
    for (orderedSubset in allOrderedSubsets) {
        if (orderedSubset !in orderedSubsetIdentity) {
            for (automorphism in automorphisms) {
                val image = orderedSubset.map(automorphism::getValue)
                orderedSubsetIdentity[image] = orderedSubset
            }
        }
    }

    var trueNumVariables = 1

    val yes = mutableMapOf<VariableKey<E>, Expression<V>>()
    val no = mutableMapOf<VariableKey<E>, Expression<V>>()
    for (orderedSubset in orderedSubsetIdentity.values.toSet()) {
        for ((index, elem) in orderedSubset.withIndex()) {
            val prev = orderedSubset - elem
            for (prevSpan in subsets(prev.toSet()).map(spanIdentity::getValue).toSet()) {
                val key = VariableKey(orderedSubset, index, prevSpan)
                yes[key] = if (matroid.spans(prevSpan, elem)) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
                no[key] = lpBuilder.newVariable(VariableType.NONNEGATIVE)
                if (!matroid.spans(prevSpan, elem)) {
                    trueNumVariables++
                }
            }
        }
    }

    val summary = mutableMapOf<SummaryKey<E>, Expression<V>>()
    for ((key, expression) in yes) {
        val span = spanIdentity[key.prevSpan + key.orderedSubset[key.index]]!!
        val summaryKey = SummaryKey(key.orderedSubset, span)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + expression
    }
    for ((key, expression) in no) {
        val summaryKey = SummaryKey(key.orderedSubset, key.prevSpan)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + expression
    }
    summary[SummaryKey(listOf(), setOf())] = Expression.fromConstant(1.0)

    for ((key, yesExpr) in yes) {
        val noExpr = no[key]!!
        val prevOrderedSubset = key.orderedSubset - key.orderedSubset[key.index]
        val covered = prevOrderedSubset.indices.filter { matroid.spans(key.prevSpan, prevOrderedSubset[it]) }
        val prevIdentity = orderedSubsetIdentity[prevOrderedSubset]!!
        val coveredImage = covered.map(prevIdentity::get).toSet()
        val prevSpanIdentity = spanIdentity[coveredImage]!!

        val prevKey = SummaryKey(prevIdentity, prevSpanIdentity)
        val prevSummary = summary[prevKey]!!
        lpBuilder.newConstraint(yesExpr + noExpr, ConstraintType.EQUAL, prevSummary / (matroid.elements().size - prevOrderedSubset.size).toDouble())
    }

    val yesTotals = mutableMapOf<PartialVariableKey<E>, Expression<V>>()
    for ((key, yesExpr) in yes) {
        val partialKey = PartialVariableKey(key.orderedSubset, key.index)
        yesTotals[partialKey] = (yesTotals[partialKey] ?: Expression.zero()) + yesExpr
    }

    val competitiveRatio = lpBuilder.newVariable(VariableType.UNBOUNDED)
    lpBuilder.optimize(OptimizationMode.MAXIMIZE, competitiveRatio)

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
            for (threshold in 1..order.size) {
                val rank = matroid.rank(order.subList(0, threshold).toSet())
                val prevRank = matroid.rank(order.subList(0, threshold - 1).toSet())
                if (rank > prevRank) {
                    val contributions = probabilities.subList(0, threshold).reduce(Expression<V>::plus)
                    lpBuilder.newConstraint(
                        contributions,
                        ConstraintType.GREATER_OR_EQUAL,
                        rank.toDouble() * competitiveRatio
                    )
                }
            }
        }
    }

    //println("true num variables = $trueNumVariables")
}

data class VariableKey<E>(val orderedSubset: List<E>, val index: Int, val prevSpan: Set<E>)
data class PartialVariableKey<E>(val orderedSubset: List<E>, val index: Int)
data class SummaryKey<E>(val orderedSubset: List<E>, val span: Set<E>)
data class SpanKey<E>(val seen: Set<E>, val span: Set<E>)

fun <E> isAutomorphism(matroid: Matroid<E>, bijection: Map<E, E>): Boolean {
    for (subset in subsets(matroid.elements())) {
        val image = subset.map(bijection::getValue).toSet()
        if (subset in matroid != image in matroid) {
            return false
        }
    }
    return true
}

fun <E, V> buildMatroidSecretaryLP(matroid: Matroid<E>, lpBuilder: LinearProgramBuilder<V>) {
    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val automorphisms = bijections(matroid.elements()).filter { isAutomorphism(matroid, it) }
    val orderedSubsetIdentity = mutableMapOf<List<E>, List<E>>()
    for (orderedSubset in allOrderedSubsets) {
        if (orderedSubset !in orderedSubsetIdentity) {
            for (automorphism in automorphisms) {
                val image = orderedSubset.map(automorphism::getValue)
                orderedSubsetIdentity[image] = orderedSubset
            }
        }
    }

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

    val yes = mutableMapOf<VariableKey<E>, Expression<V>>()
    val no = mutableMapOf<VariableKey<E>, Expression<V>>()
    for (orderedSubset in orderedSubsetIdentity.values.toSet()) {
        for ((index, elem) in orderedSubset.withIndex()) {
            val prev = (orderedSubset - elem).toSet()
            for (prevSpan in subsets(prev).map { spanIdentity[SpanKey<E>(prev, it)]!! }.toSet()) {
                val key = VariableKey(orderedSubset, index, prevSpan)
                yes[key] = if (matroid.spans(prevSpan, elem)) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
                no[key] = lpBuilder.newVariable(VariableType.NONNEGATIVE)
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

    val competitiveRatio = lpBuilder.newVariable(VariableType.UNBOUNDED)
    lpBuilder.optimize(OptimizationMode.MAXIMIZE, competitiveRatio)

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
            for (threshold in 1..order.size) {
                val rank = matroid.rank(order.subList(0, threshold).toSet())
                val prevRank = matroid.rank(order.subList(0, threshold - 1).toSet())
                if (rank > prevRank) {
                    val contributions = probabilities.subList(0, threshold).reduce(Expression<V>::plus)
                    lpBuilder.newConstraint(
                        contributions,
                        ConstraintType.GREATER_OR_EQUAL,
                        rank.toDouble() * competitiveRatio
                    )
                }
            }
        }
    }
}

fun <E, V> buildMatroidSecretaryLPNoSymmetry(matroid: Matroid<E>, lpBuilder: LinearProgramBuilder<V>) {
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val yes = mutableMapOf<VariableKey<E>, Expression<V>>()
    val no = mutableMapOf<VariableKey<E>, Expression<V>>()
    for (orderedSubset in allOrderedSubsets) {
        for ((index, elem) in orderedSubset.withIndex()) {
            val prev = orderedSubset - elem
            for (prevSpan in subsets(prev.toSet())) {
                val key = VariableKey(orderedSubset, index, prevSpan)
                yes[key] = if (matroid.spans(prevSpan, elem)) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
                no[key] = lpBuilder.newVariable(VariableType.NONNEGATIVE)
            }
        }
    }

    val summary = mutableMapOf<SummaryKey<E>, Expression<V>>()
    for ((key, expression) in yes) {
        val span = key.prevSpan + key.orderedSubset[key.index]
        val summaryKey = SummaryKey(key.orderedSubset, span)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + expression
    }
    for ((key, expression) in no) {
        val summaryKey = SummaryKey(key.orderedSubset, key.prevSpan)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + expression
    }
    summary[SummaryKey(listOf(), setOf())] = Expression.fromConstant(1.0)

    for ((key, yesExpr) in yes) {
        val noExpr = no[key]!!
        val prevKey = SummaryKey(key.orderedSubset - key.orderedSubset[key.index], key.prevSpan)
        val prevSummary = summary[prevKey]!!
        lpBuilder.newConstraint(yesExpr + noExpr, ConstraintType.EQUAL, prevSummary / (matroid.elements().size - prevKey.orderedSubset.size).toDouble())
    }

    val yesTotals = mutableMapOf<PartialVariableKey<E>, Expression<V>>()
    for ((key, yesExpr) in yes) {
        val partialKey = PartialVariableKey(key.orderedSubset, key.index)
        yesTotals[partialKey] = (yesTotals[partialKey] ?: Expression.zero()) + yesExpr
    }

    val competitiveRatio = lpBuilder.newVariable(VariableType.UNBOUNDED)
    lpBuilder.optimize(OptimizationMode.MAXIMIZE, competitiveRatio)

    for (order in permutations(matroid.elements())) {
        val probabilities = MutableList(order.size) { Expression.zero<V>() }
        for (j in order.indices) {
            for (partialOrder in subsets(order.indices.toSet())) {
                if (j in partialOrder) {
                    val partialOrder = partialOrder.sorted()
                    val orderedSubset = partialOrder.map(order::get)
                    val index = partialOrder.indexOf(j)
                    val key = PartialVariableKey(orderedSubset, index)
                    probabilities[j] += yesTotals[key]!!
                }
            }
        }
        for (threshold in 1..order.size) {
            val rank = matroid.rank(order.subList(0, threshold).toSet())
            val prevRank = matroid.rank(order.subList(0, threshold - 1).toSet())
            if (rank > prevRank) {
                val contributions = probabilities.subList(0, threshold).reduce(Expression<V>::plus)
                lpBuilder.newConstraint(
                    contributions,
                    ConstraintType.GREATER_OR_EQUAL,
                    rank.toDouble() * competitiveRatio
                )
            }
        }
    }
}