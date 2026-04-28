package org.example

fun <T> subsets(set: Set<T>): Collection<Set<T>> {
    if (set.isEmpty()) {
        return listOf(setOf())
    }
    val result = mutableListOf<Set<T>>()
    val elem = set.find { true }!!
    for (subset in subsets(set - elem)) {
        result.add(subset)
        result.add(subset + elem)
    }
    return result
}

fun <T> permutations(set: Set<T>): Collection<List<T>> {
    if (set.isEmpty()) {
        return listOf(listOf())
    }
    return set.flatMap { x -> permutations(set - x).map { it + x } }
}

fun <T> orderedSubsets(set: Set<T>): Collection<List<T>> {
    return subsets(set).flatMap(::permutations)
}

fun <T> bijections(set: Set<T>): Collection<Map<T, T>> {
    val order = set.toList()
    return permutations(set).map { order.zip(it).toMap() }
}