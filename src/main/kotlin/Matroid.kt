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
}

class MatroidByBases<E>(val elements: Set<E>, val bases: List<Set<E>>): Matroid<E> {
    override fun elements() = elements

    override fun contains(set: Set<E>) = bases.any { it.containsAll(set) }
}