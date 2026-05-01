package org.example.core

import org.example.Matroid

data class Edge<V, I>(val from: V, val to: V, val id: I)

data class GraphicMatroid<V, I>(val edges: Set<Edge<V, I>>): Matroid<Edge<V, I>> {
    override fun elements() = edges

    override fun contains(set: Set<Edge<V, I>>): Boolean {
        val union = mutableMapOf<V, V>()
        fun find(v: V): V {
            val parent = union.computeIfAbsent(v) { v }
            if (parent != union[parent]) {
                union[v] = find(parent)
            }
            return union[v]!!
        }
        for ((a, b) in set) {
            val u = find(a)
            val v = find(b)
            if (u == v) {
                return false
            }
            union[u] = v
        }
        return true
    }
}

fun <V> graphicMatroidOf(vararg edges: Pair<V, V>) =
    GraphicMatroid(edges.withIndex().map { (index, pair) -> Edge(pair.first, pair.second, index) }.toSet())