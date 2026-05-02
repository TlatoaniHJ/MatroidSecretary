package org.example

import com.google.ortools.linearsolver.MPSolver
import com.gurobi.gurobi.GRB
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.system.exitProcess

fun main() = runBlocking(Dispatchers.Default) {
    val n = 8
    val matroids = parseMatroidsFile(File("matroids09_bases.txt"), targetSize = n)
    println("num matroids = ${matroids.size}")

    // 1. Create a lock to synchronize console output
    val printMutex = Mutex()

    val hits = matroids.withIndex().map { (index, matroid) ->
        async {
            // 2. Create a local string builder for this specific execution
            val log = java.lang.StringBuilder()

            log.appendLine("matroid #$index = $matroid")

            if (matroid.rank(matroid.elements()) == 0) {
                return@async null
            }

            log.appendLine("solving with more symmetry")
            val z = solveMatroidWithLog(matroid, 2, log)

            var hitResult: Double? = null
            if (z < 0.4099) {
                log.appendLine("HIT")
                hitResult = z
            }

            // The equivalent of repeat(5) { println() }
            log.append("\n\n\n\n\n")

            // 3. Acquire the lock and print everything at once
            printMutex.withLock {
                print(log.toString()) // Use print, not println, since we appended lines
            }

            hitResult // Return the actual computation result to the list
        }
    }.awaitAll().filterNotNull()

    println("Total hits: ${hits.size}")
    for (matroid in hits) {
        println(matroid)
    }
}

fun <E> solveMatroidWithLog(matroid: Matroid<E>, mode: Int, log: StringBuilder): Double {
    var builder = OrToolsLinearProgramBuilder()

    when (mode) {
        0 -> buildMatroidSecretaryLPNoSymmetry(matroid, builder)
        1 -> buildMatroidSecretaryLPOld(matroid, builder)
        2 -> buildMatroidSecretaryLP(matroid, builder)
        else -> throw IllegalArgumentException("mode = $mode, should be 0, 1, 2")
    }

    log.appendLine("num variables = ${builder.getNumVariables()}")
    log.appendLine("num constraints = ${builder.getNumConstraints()}")

    // Solve the model
    val startTime = System.currentTimeMillis()
    val status = builder.solve()
    val endTime = System.currentTimeMillis()
    log.appendLine("time taken = ${(endTime - startTime).toDouble() / 1000.0} seconds")

    if (status == MPSolver.ResultStatus.OPTIMAL) {
        log.appendLine("Solution found!")
        log.appendLine("Objective value = ${builder.getObjectiveValue()}")
        return builder.getObjectiveValue()
    } else {
        //log.appendLine("The problem does not have an optimal solution.")
        //exitProcess(0)
        println("The problem does not have an optimal solution.")
        exitProcess(1)
    }
}

fun <E> solveMatroidStep1(matroid: Matroid<E>, mode: Int, log: StringBuilder, target: Double? = null): OrToolsLinearProgramBuilder {
    val timer = Timer()
    var builder = OrToolsLinearProgramBuilder()

    when (mode) {
        0 -> buildMatroidSecretaryLPNoSymmetry(matroid, builder)
        1 -> buildMatroidSecretaryLPOld(matroid, builder)
        2 -> buildMatroidSecretaryLP(matroid, builder, target)
        12 -> buildMatroidSecretaryLPSparser(matroid, builder, target)
        22 -> buildMatroidSecretaryLPSparser2(matroid, builder, target)
        else -> throw IllegalArgumentException("mode = $mode, should be 0, 1, 2")
    }

    log.appendLine("num variables = ${builder.getNumVariables()}")
    log.appendLine("num constraints = ${builder.getNumConstraints()}")
    log.appendLine("constructed LP [${timer.lapSeconds()} second]")
    return builder
}

fun <E> solveMatroidStep1Gurobi(matroid: Matroid<E>, mode: Int, log: StringBuilder, target: Double? = null, logFileName: String? = null, threads: Int = 1): GurobiLinearProgramBuilder {
    val timer = Timer()
    var builder = GurobiLinearProgramBuilder(logFileName = logFileName, threads = threads)

    when (mode) {
        0 -> buildMatroidSecretaryLPNoSymmetry(matroid, builder)
        1 -> buildMatroidSecretaryLPOld(matroid, builder)
        2 -> buildMatroidSecretaryLP(matroid, builder, target)
        12 -> buildMatroidSecretaryLPSparser(matroid, builder, target)
        22 -> buildMatroidSecretaryLPSparser2(matroid, builder, target)
        32 -> buildMatroidSecretaryLPStopgap(matroid, builder, target)
        42 -> buildMatroidSecretaryLPPrefixSum(matroid, builder, target)
        52 -> buildMatroidSecretaryLPSubsetSum(matroid, builder, target)
        else -> throw IllegalArgumentException("mode = $mode, should be 0, 1, 2")
    }
    builder.updateModel()


    log.appendLine("num variables = ${builder.getNumVariables()}")
    log.appendLine("num constraints = ${builder.getNumConstraints()}")
    log.appendLine("num nonzeros = ${builder.getNumNonZeros()}")
    log.appendLine("constructed LP [${timer.lapSeconds()}] seconds")
    return builder
}

fun solveMatroidStep2(builder: OrToolsLinearProgramBuilder, log: StringBuilder): Double {
    // Solve the model
    val startTime = System.currentTimeMillis()
    val status = builder.solve()
    val endTime = System.currentTimeMillis()
    log.appendLine("time taken = ${(endTime - startTime).toDouble() / 1000.0} seconds")

    if (status == MPSolver.ResultStatus.OPTIMAL) {
        log.appendLine("Solution found!")
        log.appendLine("Objective value = ${builder.getObjectiveValue()}")
        return builder.getObjectiveValue()
    } else {
        //log.appendLine("The problem does not have an optimal solution.")
        //exitProcess(0)
        println("The problem does not have an optimal solution.")
        exitProcess(1)
    }
}

fun solveMatroidStep2Gurobi(builder: GurobiLinearProgramBuilder, log: StringBuilder): Double {
    val startTime = System.currentTimeMillis()

    // Trigger the native Gurobi optimization process
    // (Assuming you haven't already called builder.optimize() which does this automatically)
    builder.model.optimize()

    // Query the status attribute from the underlying Gurobi model
    val status = builder.model.get(GRB.IntAttr.Status)

    val endTime = System.currentTimeMillis()
    log.appendLine("time taken = ${(endTime - startTime).toDouble() / 1000.0} seconds")

    // Compare against Gurobi's OPTIMAL status code
    if (status == GRB.Status.OPTIMAL) {
        log.appendLine("Solution found!")
        log.appendLine("Objective value = ${builder.getObjectiveValue()}")
        return builder.getObjectiveValue()
    } else {
        // It is highly recommended to print the actual status code for debugging
        println("The problem does not have an optimal solution. Gurobi status code: $status")

        if (status == GRB.Status.INFEASIBLE) {
            println("The model is mathematically infeasible.")
        } else if (status == GRB.Status.UNBOUNDED) {
            println("The model is unbounded.")
        }

        exitProcess(1)
    }
}