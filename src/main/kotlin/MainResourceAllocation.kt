package org.example

import java.io.File
import kotlin.math.max

fun main() {
    lpStatistics()
}

/*fun lpStatistics() {
    val prefixes = listOf("", "archive_8_truncation_conjecture/", "archive_8_truncation_conjecture_2/", "archive_8_truncation_conjecture_3/", "archive_8_truncation_conjecture_4/")
    val lps = mutableListOf<LP>()

    val nonzeroLabel = " nonzeros (Max)"
    val ramLabel = "roughly "
    for (index in 1..1725) {
        for (prefix in prefixes) {
            val fileName = "${prefix}matroid_$index.txt"
            println(fileName)
            try {
                val text = File(fileName).readText()
                var index = text.lastIndexOf(nonzeroLabel)
                var nonzerosString = ""
                index--
                while (text[index].isDigit()) {
                    nonzerosString = text[index] + nonzerosString
                    index--
                }
                val nonzeros = nonzerosString.toInt()

                index = text.indexOf(ramLabel) + ramLabel.length
                var ramString = ""
                while (text[index].isDigit() || text[index] == '.') {
                    ramString += text[index]
                }
                val ram = ramString.toDouble()
                println("\tsucceeded: nonzeros = $nonzeros, ram estimate = $ram")
                lps.add(LP(nonzeros, ram, fileName))
            } catch (e: Exception) {
                println("\tfailed: $e")
            }
        }
    }
    lps.sortBy { it.nonzeros }
    var mexico = .0
    for ((nonzeros, ramEstimate, source) in lps) {
        mexico = max(mexico, ramEstimate)
        println("nonzeros = $nonzeros\tram estimate = $ramEstimate\tprefix max ram estimate = $mexico\tsource = $source")
    }
}

data class LP(val nonzeros: Int, val ramEstimate: Double, val source: String)*/