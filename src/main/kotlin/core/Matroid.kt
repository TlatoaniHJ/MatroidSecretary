package org.example

import org.example.core.automorphismsOptimized

interface Matroid<E> {

    fun elements(): Set<E>

    operator fun contains(set: Set<E>): Boolean

    fun size() = elements().size

    fun basis(set: Set<E> = elements()): Set<E> {
        val result = mutableSetOf<E>()
        for (x in set) {
            if (result + x in this) {
                result.add(x)
            }
        }
        return result
    }

    fun rank(set: Set<E> = elements()) = basis(set).size

    fun spans(set: Set<E>, x: E) = x in set || basis(set) + x !in this

    fun span(set: Set<E>) = elements().filter { spans(set, it) }.toSet()

    fun minor(contract: Set<E>, newElements: Set<E>): Matroid<E> = MatroidMinor(newElements, basis(contract), this)

    fun restrict(newElements: Set<E>) = minor(setOf(), newElements)

    fun isDecomposable(): Boolean {
        for (subset in subsets(elements())) {
            if (subset.isNotEmpty() && subset.size < elements().size) {
                val matroid1 = restrict(subset)
                val matroid2 = restrict(elements() - subset)
                val sum = MatroidSum(matroid1, matroid2)
                if (same(sum)) {
                    return true
                }
            }
        }
        return false
    }

    fun same(other: Matroid<E>): Boolean {
        assert(elements() == other.elements())
        for (set in subsets(elements())) {
            if (set in this != set in other) {
                return false
            }
        }
        return true
    }

    fun bases(): Set<Set<E>> {
        val rank = rank(elements())
        return subsets(elements()).filter { it in this && it.size == rank }.toSet()
    }

    /*fun automorphisms(): List<Map<E, E>> {
        val bases = bases()
        return bijections(elements()).filter { bijection -> bases.map { it.map(bijection::getValue).toSet() }.toSet() == bases }
    }*/
    fun automorphisms(): List<Map<E, E>> = automorphismsOptimized(this)
}

data class MatroidByBases<E>(val elements: Set<E>, val bases: List<Set<E>>): Matroid<E> {
    override fun elements() = elements

    override fun contains(set: Set<E>) = bases.any { it.containsAll(set) }
}

class MatroidMinor<E>(val elements: Set<E>, val contract: Set<E>, val parent: Matroid<E>): Matroid<E> {
    override fun elements() = elements

    override fun contains(set: Set<E>) = contract + set in parent
}

class MatroidSum<E>(val matroid1: Matroid<E>, val matroid2: Matroid<E>): Matroid<E> {
    override fun elements() = matroid1.elements() + matroid2.elements()

    override fun contains(set: Set<E>) = set.intersect(matroid1.elements()) in matroid1 && set.intersect(matroid2.elements()) in matroid2
}

fun <E> isMatroid(matroid: Matroid<E>): Boolean {
    if (setOf() !in matroid) {
        return false
    }
    val independentSets = subsets(matroid.elements()).filter { it in matroid }
    for (set in independentSets) {
        if (subsets(set).any { it !in matroid }) {
            return false
        }
    }
    for (set1 in independentSets) {
        for (set2 in independentSets) {
            if (set2.size > set1.size) {
                if (!set2.any { it !in set1 && set1 + it in matroid }) {
                    return false
                }
            }
        }
    }
    return true
}

fun <E, F> isomorphic(matroid1: Matroid<E>, matroid2: Matroid<F>): Boolean {
    val elements1 = matroid1.elements().toList()
    val elements2 = matroid2.elements()
    if (elements1.size != elements2.size) {
        return false
    }
    if (matroid1.rank() != matroid2.rank()) {
        return false
    }
    if (matroid1.bases().size != matroid2.bases().size) {
        return false
    }
    for (permutation in permutations(elements2)) {
        var works = true
        for (set in subsets(elements1.zip(permutation).toSet())) {
            val set1 = set.map { it.first }.toSet()
            val set2 = set.map { it.second }.toSet()
            if (set1 in matroid1 != set2 in matroid2) {
                works = false
                break
            }
        }
        if (works) {
            return true
        }
    }
    return false
}

class CachedMatroid<E>(val matroid: Matroid<E>): Matroid<E> {
    val spans = mutableSetOf<Pair<Set<E>, E>>()

    init {
        for (subset in subsets(elements())) {
            for (element in elements()) {
                if (matroid.spans(subset, element)) {
                    spans.add(Pair(subset, element))
                }
            }
        }
    }

    override fun elements() = matroid.elements()

    override fun contains(set: Set<E>) = set in matroid

    override fun spans(set: Set<E>, x: E) = Pair(set, x) in spans
}

data class TruncatedMatroid<E>(val matroid: Matroid<E>, val truncation: Int): Matroid<E> {
    override fun elements() = matroid.elements()
    override fun contains(set: Set<E>) = set.size <= truncation && set in matroid
}

data class DualMatroid<E>(val matroid: Matroid<E>): Matroid<E> {
    override fun elements() = matroid.elements()
    override fun contains(set: Set<E>) = matroid.rank(elements() - set) == matroid.rank()
}