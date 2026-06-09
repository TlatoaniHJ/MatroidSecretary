package org.example.core
import org.example.*

fun <E, V> buildMatroidSecretaryLPKnownOrder(sampleProbability: Double, matroid: Matroid<E>, presentationOrder: List<E>, lpBuilder: LinearProgramBuilder<V>) {
    //println("building lp for matroid = $matroid")
    val timer = Timer()

    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val spanIdentity = mutableMapOf<SpanKey<E>, Set<E>>()
    for (seen in allSubsets) {
        val firstNotSeenIndex = if (seen.size == matroid.elements().size) matroid.elements().size else presentationOrder.indexOfFirst { it !in seen }
        val disp = presentationOrder.prefix(firstNotSeenIndex).toSet()
        val notSeen = matroid.elements() - seen
        val minors = mutableListOf<Pair<Set<E>, Matroid<E>>>()
        for (subset in subsets(disp)) {
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
    for (orderedSubset in allOrderedSubsets) {
        for ((index, elem) in orderedSubset.withIndex()) {
            val prev = (orderedSubset - elem).toSet()
            val firstNotSeen = presentationOrder.first { it !in prev }
            if (elem == firstNotSeen) {
                for (prevSpan in subsets(presentationOrder.prefix(presentationOrder.indexOf(elem)).toSet()).map { spanIdentity[SpanKey<E>(prev, it)]!! }.toSet()) {
                    val key = VariableKey(orderedSubset, index, prevSpan)
                    val yesZero =
                        matroid.spans(prevSpan, elem) || matroid.rank(orderedSubset.subList(0, index).toSet()) == rank
                    yes[key] = if (yesZero) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
                    no[key] =
                        if (!yesZero && orderedSubset.size == matroid.size()) Expression.zero() else lpBuilder.newVariable(
                            VariableType.NONNEGATIVE
                        )
                }
            }
        }
    }
    /*println("yes =")
    printMap(yes)
    println("no =")
    printMap(no)*/


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
    for (sampled in allOrderedSubsets) {
        val probability = matroid.elements().map { if (it in sampled) sampleProbability else { 1.0 - sampleProbability } }.reduce(Double::times)
        val span = spanIdentity[SpanKey(sampled.toSet(), setOf())]!!
        val summaryKey = SummaryKey(sampled, span)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + probability
    }
    //println("summary (before) =")
    //printMap(summary)
    for (key in summary.keys.toSet()) {
        val expression = summary[key]!!
        if (!expression.isConstant()) {
            val variable = lpBuilder.newVariable(VariableType.UNBOUNDED)
            lpBuilder.newConstraint(variable, ConstraintType.EQUAL, expression)
            summary[key] = variable
        }
    }
    //println("summary (after) =")
    //printMap(summary)

    for ((key, expression) in summary) {
        if (key.orderedSubset.size != matroid.elements().size) {
            val firstNotSeen = presentationOrder.first { it !in key.orderedSubset }
            for (index in 0..key.orderedSubset.size) {
                val newOrderedSubset: List<E> = key.orderedSubset.prefix(index) + firstNotSeen + key.orderedSubset.suffix(index)
                val span = spanIdentity[SpanKey(key.orderedSubset.toSet(), key.span)]!!
                val variableKey = VariableKey(newOrderedSubset, index, span)
                val yesExpr = yes[variableKey]!!
                val noExpr = no[variableKey]!!
                lpBuilder.newConstraint(yesExpr + noExpr, ConstraintType.EQUAL, expression)
                //println("constraint: ${yesExpr + noExpr} = $expression")
            }
        }
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

    val competitiveRatio = lpBuilder.newVariable(VariableType.UNBOUNDED)
    lpBuilder.optimize(OptimizationMode.MAXIMIZE, competitiveRatio)

    for (order in permutations(matroid.elements())) {
        val probabilities = MutableList(order.size) { Expression.zero<V>() }
        for (j in order.indices) {
            val require = presentationOrder.prefix(presentationOrder.indexOf(order[j]) + 1).map { order.indexOf(it) }
            for (partialOrder in subsets(order.indices.toSet())) {
                if (require.all { it in partialOrder }) {
                    val partialOrder = partialOrder.sorted()
                    val orderedSubset = partialOrder.map(order::get)
                    val index = partialOrder.indexOf(j)
                    val key = PartialVariableKey(orderedSubset, index)
                    probabilities[j] += yesTotals[key]!!
                }
            }
        }
        var prefixSum = Expression.zero<V>()
        for (threshold in 1..order.size) {
            prefixSum += probabilities[threshold - 1]
            val rank = matroid.rank(order.subList(0, threshold).toSet())
            val prevRank = matroid.rank(order.subList(0, threshold - 1).toSet())
            if (rank > prevRank) {
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

fun <K, V> printMap(map: Map<K, V>) {
    for ((k, v) in map) {
        println("\t$k -> $v")
    }
}

fun <E, V> buildMatroidSecretaryLPKnownOrderTight(sampleProbability: Double, matroid: Matroid<E>, presentationOrder: List<E>, lpBuilder: LinearProgramBuilder<V>) {
    //println("building lp for matroid = $matroid")
    val timer = Timer()

    val allSubsets = subsets(matroid.elements())
    val allOrderedSubsets = orderedSubsets(matroid.elements())

    val spanIdentity = mutableMapOf<SpanKey<E>, Set<E>>()
    for (seen in allSubsets) {
        val firstNotSeenIndex = if (seen.size == matroid.elements().size) matroid.elements().size else presentationOrder.indexOfFirst { it !in seen }
        val disp = presentationOrder.prefix(firstNotSeenIndex).toSet()
        val notSeen = matroid.elements() - seen
        val minors = mutableListOf<Pair<Set<E>, Matroid<E>>>()
        for (subset in subsets(disp)) {
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
    for (orderedSubset in allOrderedSubsets) {
        for ((index, elem) in orderedSubset.withIndex()) {
            val prev = (orderedSubset - elem).toSet()
            val firstNotSeen = presentationOrder.first { it !in prev }
            if (elem == firstNotSeen) {
                for (prevSpan in subsets(presentationOrder.prefix(presentationOrder.indexOf(elem)).toSet()).map { spanIdentity[SpanKey<E>(prev, it)]!! }.toSet()) {
                    val key = VariableKey(orderedSubset, index, prevSpan)
                    val yesZero =
                        matroid.spans(prevSpan, elem) || matroid.rank(orderedSubset.subList(0, index).toSet()) == rank
                    yes[key] = if (yesZero) Expression.zero() else lpBuilder.newVariable(VariableType.NONNEGATIVE)
                    no[key] =
                        if (!yesZero && orderedSubset.size == matroid.size()) Expression.zero() else lpBuilder.newVariable(
                            VariableType.NONNEGATIVE
                        )
                }
            }
        }
    }
    /*println("yes =")
    printMap(yes)
    println("no =")
    printMap(no)*/


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
    for (sampled in allOrderedSubsets) {
        val probability = matroid.elements().map { if (it in sampled) sampleProbability else { 1.0 - sampleProbability } }.reduce(Double::times)
        val span = spanIdentity[SpanKey(sampled.toSet(), setOf())]!!
        val summaryKey = SummaryKey(sampled, span)
        summary[summaryKey] = (summary[summaryKey] ?: Expression.zero()) + probability
    }
    //println("summary (before) =")
    //printMap(summary)
    for (key in summary.keys.toSet()) {
        val expression = summary[key]!!
        if (!expression.isConstant()) {
            val variable = lpBuilder.newVariable(VariableType.UNBOUNDED)
            lpBuilder.newConstraint(variable, ConstraintType.EQUAL, expression)
            summary[key] = variable
        }
    }
    //println("summary (after) =")
    //printMap(summary)

    for ((key, expression) in summary) {
        if (key.orderedSubset.size != matroid.elements().size) {
            val firstNotSeen = presentationOrder.first { it !in key.orderedSubset }
            for (index in 0..key.orderedSubset.size) {
                val newOrderedSubset: List<E> = key.orderedSubset.prefix(index) + firstNotSeen + key.orderedSubset.suffix(index)
                val span = spanIdentity[SpanKey(key.orderedSubset.toSet(), key.span)]!!
                val variableKey = VariableKey(newOrderedSubset, index, span)
                val yesExpr = yes[variableKey]!!
                val noExpr = no[variableKey]!!
                lpBuilder.newConstraint(yesExpr + noExpr, ConstraintType.EQUAL, expression)
                //println("constraint: ${yesExpr + noExpr} = $expression")
            }
        }
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

    val competitiveRatio = lpBuilder.newVariable(VariableType.UNBOUNDED)
    lpBuilder.optimize(OptimizationMode.MAXIMIZE, competitiveRatio)

    for (order in permutations(matroid.elements())) {
        val probabilities = MutableList(order.size) { Expression.zero<V>() }
        for (j in order.indices) {
            val require = presentationOrder.prefix(presentationOrder.indexOf(order[j]) + 1).map { order.indexOf(it) }
            for (partialOrder in subsets(order.indices.toSet())) {
                if (require.all { it in partialOrder }) {
                    val partialOrder = partialOrder.sorted()
                    val orderedSubset = partialOrder.map(order::get)
                    val index = partialOrder.indexOf(j)
                    val key = PartialVariableKey(orderedSubset, index)
                    probabilities[j] += yesTotals[key]!!
                }
            }
        }
        var prefixSum = Expression.zero<V>()
        for (threshold in 1..order.size) {
            prefixSum += probabilities[threshold - 1]
            val rank = matroid.rank(order.subList(0, threshold).toSet())
            val prevRank = matroid.rank(order.subList(0, threshold - 1).toSet())
            if (rank > prevRank && rank == threshold) {
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