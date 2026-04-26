package org.example

class Timer {
    var time = System.currentTimeMillis()

    fun lapMilliseconds(): Long {
        val oldTime = time
        time = System.currentTimeMillis()
        return time - oldTime
    }

    fun lapSeconds(): Double = lapMilliseconds().toDouble() / 1000.0
}