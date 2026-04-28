package org.example

import java.io.File

/**
 * Parses the Mayhew-Royle matroids bases text file.
 * * @param file The text file to parse.
 * @param targetSize If provided, only matroids with exactly this many elements are returned.
 * @param targetRank If provided, only matroids with exactly this rank are returned.
 */
fun parseMatroidsFile(
    folder: File,
    targetSize: Int? = null,
    targetRank: Int? = null
): List<Matroid<Int>> {
    val matroids = mutableListOf<Matroid<Int>>()

    var fileIndex = 0
    while (true) {
        fileIndex++
        val file = File("$folder/$fileIndex.txt")
        if (!file.exists()) {
            break
        }
        // useLines reads the file lazily, line by line, keeping memory footprint low
        file.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                // Split by any amount of whitespace
                val tokens = trimmed.split("\\s+".toRegex())
                if (tokens.size < 4) continue // Skip malformed lines

                val n = tokens[1].toInt()
                val rank = tokens[2].toInt()
                // val numBases = tokens[3].toInt() // Available, but not strictly needed for instantiation

                // Filter on the fly to save memory
                if (targetSize != null && n != targetSize) continue
                if (targetRank != null && rank != targetRank) continue

                val elements = (0 until n).toSet()
                val bases = mutableListOf<Set<Int>>()

                // The bases start at index 4
                for (i in 4 until tokens.size) {
                    // Strip the single quotes
                    val baseString = tokens[i].trim('\'')

                    // Convert the string of characters (e.g., "01") to a Set of Ints (e.g., {0, 1})
                    val baseSet = baseString.map { it.digitToInt() }.toSet()
                    bases.add(baseSet)
                }

                matroids.add(MatroidByBases(elements, bases))
            }
        }
    }

    return matroids
}