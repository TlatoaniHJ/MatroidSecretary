package org.example

interface Matroid<E> {

    fun elements(): Set<E>

    operator fun contains(set: Set<E>): Boolean

    fun basis(set: Set<E>): Set<E> {
        val result = mutableSetOf<E>()
        for (x in set) {
            if (result + x in this) {
                result.add(x)
            }
        }
        return result
    }

    fun rank(set: Set<E>) = basis(set).size

    fun spans(set: Set<E>, x: E) = x in set || basis(set) + x !in this

    fun span(set: Set<E>) = elements().filter { spans(set, it) }.toSet()

    fun minor(contract: Set<E>, newElements: Set<E>): Matroid<E> = MatroidMinor(newElements, basis(contract), this)

    fun same(other: Matroid<E>): Boolean {
        assert(elements() == other.elements())
        for (set in subsets(elements())) {
            if (set in this != set in other) {
                return false
            }
        }
        return true
    }
}

data class MatroidByBases<E>(val elements: Set<E>, val bases: List<Set<E>>): Matroid<E> {
    override fun elements() = elements

    override fun contains(set: Set<E>) = bases.any { it.containsAll(set) }
}

class MatroidMinor<E>(val elements: Set<E>, val contract: Set<E>, val parent: Matroid<E>): Matroid<E> {
    override fun elements() = elements

    override fun contains(set: Set<E>) = contract + set in parent
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