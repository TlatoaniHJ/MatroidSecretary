package org.example

import com.gurobi.gurobi.GRB
import org.example.core.buildMatroidSecretaryLPKnownOrder
import java.io.File
import kotlin.math.min
import kotlin.system.exitProcess

fun main() {
    val n = 7
    val log: (Any) -> Unit = ::println//fileLog(File("matroids_5_known_order_no_sample.txt"))
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    log("num matroids = ${matroids.size}")
    val withAutomorphisms = matroids.withIndex().map { (index, matroid) -> Pair(index, matroid.automorphisms().size) }
    log("computed automorphisms")
    var numMatroidsConsiderd = 0

    var minRatio = 1.0
    for ((index, numAutomorphisms) in withAutomorphisms) {
        numMatroidsConsiderd++
        val matroid = matroids[index]
        if (matroid.rank() <= 1) {
            continue
        }

        log("\n".repeat(5))
        log("matroid #$index = $matroid")
        log("num automorphisms = $numAutomorphisms")
        val truncated = TruncatedMatroid(matroid, matroid.rank() - 1)

        val automorphisms = matroid.automorphisms()
        val seenOrders = mutableListOf<List<Int>>()
        var minRatioHere = 1.0

        for (order in permutations(matroid.elements())) {
            if (order !in seenOrders) {
                for (auto in automorphisms) {
                    seenOrders.add(order.map(auto::getValue))
                }

                log("\n")
                log("\torder = $order")

                val ratios = mutableListOf<Double>()
                for (isTruncation in listOf(false, true)) {
                    log("\t${if (isTruncation) "TRUNCATION" else "ORIGINAL"}")

                    val timer = Timer()
                    var builder = GurobiLinearProgramBuilder(logToConsole = false, threads = 1)
                    buildMatroidSecretaryLPKnownOrder(0.0, if (isTruncation) truncated else matroid, order, builder)
                    builder.updateModel()

                    log("\tnum variables = ${builder.getNumVariables()}")
                    log("\tnum constraints = ${builder.getNumConstraints()}")
                    log("\tnum nonzeros = ${builder.getNumNonZeros()}")
                    log("\tconstructed LP [${timer.lapSeconds()}] seconds")


                    val startTime = System.currentTimeMillis()

                    // Trigger the native Gurobi optimization process
                    // (Assuming you haven't already called builder.optimize() which does this automatically)
                    builder.model.optimize()

                    // Query the status attribute from the underlying Gurobi model
                    val status = builder.model.get(GRB.IntAttr.Status)

                    val endTime = System.currentTimeMillis()
                    log("\ttime taken = ${(endTime - startTime).toDouble() / 1000.0} seconds")

                    // Compare against Gurobi's OPTIMAL status code
                    if (status == GRB.Status.OPTIMAL) {
                        log("\tSolution found!")
                        log("\tObjective value = ${builder.getObjectiveValue()}")
                        if (!isTruncation) {
                            minRatioHere = min(minRatioHere, builder.getObjectiveValue())
                        }
                        ratios.add(builder.getObjectiveValue())
                    } else {
                        // It is highly recommended to print the actual status code for debugging
                        log("\tThe problem does not have an optimal solution. Gurobi status code: $status")

                        if (status == GRB.Status.INFEASIBLE) {
                            log("\tThe model is mathematically infeasible.")
                        } else if (status == GRB.Status.UNBOUNDED) {
                            log("\tThe model is unbounded.")
                        }
                        exitProcess(1)
                    }
                }
                val (originalRatio, truncatedRatio) = ratios
                log("")
                log("original ratio = $originalRatio")
                log("truncated ratio = $truncatedRatio")
                if (truncatedRatio > originalRatio + .000001) {
                    log("COUNTEREXAMPLE")
                    exitProcess(0)
                }
            }
        }

        log("min ratio for matroid = $minRatioHere")
        minRatio = min(minRatio, minRatioHere)
        log("min ratio overall = $minRatio")
    }
}