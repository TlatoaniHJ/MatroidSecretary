/*package org.example

@JvmInline
value class Small(val bit: Int)

fun small(x: Int) = Small(1 shl x)

@JvmInline
value class SmallSet(private val mask: Int): Set<Small> {
    override val size: Int
        get() = Integer.bitCount(mask)

    override fun contains(element: Small) = mask and element.bit != 0

    override fun containsAll(elements: Collection<Small>) = elements.map { it.bit }.red

    : Boolean {
        TODO("Not yet implemented")
    }

    override fun isEmpty() = mask == 0

    override fun iterator(): Iterator<Small> {
        TODO("Not yet implemented")
    }

}*/