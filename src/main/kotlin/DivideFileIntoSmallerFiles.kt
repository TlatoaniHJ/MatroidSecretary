package org.example

import java.io.File

fun divideFile(file: File, folder: File, maxPerFile: Int) {
    folder.mkdirs()
    for ((index, line) in file.readLines().withIndex()) {
        File("$folder/${(index / maxPerFile) + 1}.txt").appendText(line + "\n")
    }
}

fun main() {
    divideFile(File("matroids09_bases.txt"), File("matroids09_bases"), 65536)
}