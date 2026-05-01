package org.example.core

import org.example.Matroid

// Replace the current automorphisms() function inside interface Matroid<E>
fun <E> automorphismsOptimized(matroid: Matroid<E>): List<Map<E, E>> {
    val elementsList = matroid.elements().toList()
    if (elementsList.isEmpty()) return listOf(emptyMap())

    val basesSet = matroid.bases()

    // 1. Invariant: Base Frequencies (Element -> Number of bases it appears in)
    val freq = mutableMapOf<E, Int>()
    for (base in basesSet) {
        for (e in base) {
            freq[e] = freq.getOrDefault(e, 0) + 1
        }
    }
    for (e in elementsList) freq.putIfAbsent(e, 0)

    // 2. Invariant: Pairwise Co-occurrence (How often elements appear together)
    val coOccur = mutableMapOf<Pair<E, E>, Int>()
    for (base in basesSet) {
        val list = base.toList()
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val e1 = list[i]
                val e2 = list[j]
                coOccur[e1 to e2] = coOccur.getOrDefault(e1 to e2, 0) + 1
                coOccur[e2 to e1] = coOccur.getOrDefault(e2 to e1, 0) + 1
            }
        }
    }

    // Partition elements purely by frequency
    val partitions = elementsList.groupBy { freq[it]!! }
    val results = mutableListOf<Map<E, E>>()

    // 3. DFS Backtracking
    fun dfs(index: Int, currentMap: MutableMap<E, E>, used: MutableSet<E>) {
        if (index == elementsList.size) {
            // Final verification: Ensure the entire base set maps perfectly to itself
            val mappedBases = basesSet.map { base -> base.map { currentMap[it]!! }.toSet() }.toSet()
            if (mappedBases == basesSet) {
                results.add(currentMap.toMap())
            }
            return
        }

        val source = elementsList[index]
        // An element can ONLY map to an element that appears in the exact same number of bases
        val allowedTargets = partitions[freq[source]!!] ?: emptyList()

        for (target in allowedTargets) {
            if (target !in used) {
                // Early Rejection: Verify Pairwise Co-occurrence with already mapped elements
                var valid = true
                for (i in 0 until index) {
                    val pastSource = elementsList[i]
                    val pastTarget = currentMap[pastSource]!!

                    val sourceCo = coOccur.getOrDefault(source to pastSource, 0)
                    val targetCo = coOccur.getOrDefault(target to pastTarget, 0)

                    if (sourceCo != targetCo) {
                        valid = false
                        break // Prune this entire branch of the permutation tree!
                    }
                }

                if (valid) {
                    currentMap[source] = target
                    used.add(target)
                    dfs(index + 1, currentMap, used)
                    used.remove(target)
                    currentMap.remove(source)
                }
            }
        }
    }

    dfs(0, mutableMapOf(), mutableSetOf())
    return results
}