package org.example

import com.gurobi.gurobi.GRB
import org.example.core.buildDistributionComparisonLP
import org.example.core.buildMatroidSecretaryLPTight
import org.example.core.buildSymmetricDistributionLP
import org.example.core.buildTightInstanceLP
import java.io.File
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random
import kotlin.system.exitProcess

fun main0() {
    val n = 7
    val k = 5
    val matroid = uniformMatroid((1..n).toSet(), k)
    val scaling = (1..n).map { 1.0 / it.toDouble() }.reduce(Double::times) / k.toDouble()
    val result = solveForDistributionSymmetric(matroid) { List(n) { if (it < k) scaling else .0 } }
    println(result)
    val result2 = solveForDistribution(matroid) { List(n) { if (it < k) scaling else .0 } }
    println(result2)
}

fun main1() {
    val n = 7
    val k = 5
    val matroid = uniformMatroid((1..n).toSet(), k)
    val distribution = mapOf(Instance((1..n).toList(), k) to 1.0)
    compare(matroid, distribution)
}

fun main2() {
    val n = 7
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    for ((index, matroid) in matroids.withIndex()) {
        if (matroid.rank() <= 1) {
            continue
        }
        println("matroid #$index = $matroid")
        val automorphisms = matroid.automorphisms()
        val distinctOrders = mutableListOf<List<Int>>()
        val seen = mutableSetOf<List<Int>>()
        for (order in permutations(matroid.elements())) {
            if (order !in seen) {
                distinctOrders.add(order)
                for (auto in automorphisms) {
                    seen.add(order.map(auto::getValue))
                }
            }
        }
        repeat(10) {
            val random = Random(23423 + it)
            println("\ttrial ${it + 1}")
            val probabilities = LinkedList(List(distinctOrders.size - 1) { random.nextDouble() }.sorted() + listOf(1.0))
            var prev = .0
            val distribution = mutableMapOf<Instance<Int>, Double>()
            for (order in distinctOrders) {
                for (rank in matroid.rank()..matroid.rank()) {
                    val next = probabilities.removeFirst()
                    val instance = Instance(order, rank)
                    distribution[instance] = next - prev
                    //println("probability of $instance = ${next - prev}")
                    prev = next
                }
            }
            compare(matroid, distribution)
        }
    }
}

fun main3() {
    val n = 3
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    for ((index, matroid) in matroids.withIndex()) {
        if (matroid.rank() <= 1) {
            continue
        }
        println("\n".repeat(5))
        println("matroid #$index = $matroid")
        println("num automorphisms = ${matroid.automorphisms().size}")

        val timer = Timer()
        var builder = GurobiLinearProgramBuilder(threads = 12, logToConsole = true)
        val (ratios, probabilities) = buildDistributionComparisonLP(matroid, builder)
        val (baseline, truncated) = ratios

        println("num variables = ${builder.getNumVariables()}")
        println("num constraints = ${builder.getNumConstraints()}")
        println("num nonzeros = ${builder.getNumNonZeros()}")
        println("constructed LP [${timer.lapSeconds()}] seconds")


        val startTime = System.currentTimeMillis()

        // Trigger the native Gurobi optimization process
        // (Assuming you haven't already called builder.optimize() which does this automatically)
        builder.model.optimize()

        // Query the status attribute from the underlying Gurobi model
        val status = builder.model.get(GRB.IntAttr.Status)

        val endTime = System.currentTimeMillis()
        println("time taken = ${(endTime - startTime).toDouble() / 1000.0} seconds")

        // Compare against Gurobi's OPTIMAL status code
        val difference: Double
        if (status == GRB.Status.OPTIMAL) {
            println("Solution found!")
            println("Objective value = ${builder.getObjectiveValue()}")
            difference = builder.getObjectiveValue()
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
        println("baseline ratio = ${builder.getValue(baseline)}")
        println("truncated ratio = ${builder.getValue(truncated)}")
        for ((order, variable) in probabilities) {
            println("P$order = ${builder.getValue(variable)}")
        }
        if (difference > .01) {
            println("COUNTEREXAMPLE")
            return
        }
    }
}

fun main4() {
    val n = 3
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    for ((index, matroid) in matroids.withIndex()) {
        if (matroid.rank() == 0) {
            continue
        }
        println("\n".repeat(5))
        println("matroid #$index = $matroid")
        println("num automorphisms = ${matroid.automorphisms().size}")

        val timer = Timer()
        var builder = GurobiLinearProgramBuilder(threads = 12, logToConsole = true)
        buildSymmetricDistributionLP(matroid, builder)

        println("num variables = ${builder.getNumVariables()}")
        println("num constraints = ${builder.getNumConstraints()}")
        println("num nonzeros = ${builder.getNumNonZeros()}")
        println("constructed LP [${timer.lapSeconds()}] seconds")


        val startTime = System.currentTimeMillis()

        // Trigger the native Gurobi optimization process
        // (Assuming you haven't already called builder.optimize() which does this automatically)
        builder.model.optimize()

        // Query the status attribute from the underlying Gurobi model
        val status = builder.model.get(GRB.IntAttr.Status)

        val endTime = System.currentTimeMillis()
        println("time taken = ${(endTime - startTime).toDouble() / 1000.0} seconds")

        // Compare against Gurobi's OPTIMAL status code
        val ratio: Double
        if (status == GRB.Status.OPTIMAL) {
            println("Solution found!")
            println("Objective value = ${builder.getObjectiveValue()}")
            ratio = builder.getObjectiveValue()
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
        println("symmetric ratio = $ratio")
        val trueRatio = solveMatroidSimpleGurobi(matroid)
        println("true ratio = $trueRatio")
        if (abs(ratio - trueRatio) > .000001) {
            println("COUNTEREXAMPLE")
            return
        }
    }
}

fun main() {
    val n = 7
    val matroids = parseMatroidsFile(File("matroids09_bases"), targetSize = n)
    for ((index, matroid) in matroids.withIndex()) {
        if (matroid.rank() == 0) {
            continue
        }
        if (index < 68) {
            //continue
        }
        println("\n".repeat(5))
        println("matroid #$index = $matroid")
        println("num automorphisms = ${matroid.automorphisms().size}")

        val timer = Timer()
        var builder = GurobiLinearProgramBuilder(threads = 12, logToConsole = true)
        //buildTightInstanceLP(matroid, builder)
        buildMatroidSecretaryLPTight(matroid, builder)

        println("num variables = ${builder.getNumVariables()}")
        println("num constraints = ${builder.getNumConstraints()}")
        println("num nonzeros = ${builder.getNumNonZeros()}")
        println("constructed LP [${timer.lapSeconds()}] seconds")


        val startTime = System.currentTimeMillis()

        // Trigger the native Gurobi optimization process
        // (Assuming you haven't already called builder.optimize() which does this automatically)
        builder.model.optimize()

        // Query the status attribute from the underlying Gurobi model
        val status = builder.model.get(GRB.IntAttr.Status)

        val endTime = System.currentTimeMillis()
        println("time taken = ${(endTime - startTime).toDouble() / 1000.0} seconds")

        // Compare against Gurobi's OPTIMAL status code
        val ratio: Double
        if (status == GRB.Status.OPTIMAL) {
            println("Solution found!")
            println("Objective value = ${builder.getObjectiveValue()}")
            ratio = builder.getObjectiveValue()
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
        println("tight instance ratio = $ratio")
        val trueRatio = solveMatroidSimpleGurobi(matroid, threads = 12)
        println("true ratio = $trueRatio")
        if (abs(ratio - trueRatio) > .000001) {
            println("COUNTEREXAMPLE")
            return
        }
    }
}

fun <E> compare(matroid: Matroid<E>, distribution: Distribution<E>) {
    val original = solveForDistributionSymmetric(matroid, convertDistribution(matroid, distribution))
    val truncated = solveForDistributionSymmetric(TruncatedMatroid(matroid, matroid.rank() - 1), convertDistribution(matroid, truncateDistribution(distribution, matroid.rank() - 1)))
    println("\t\toriginal = $original, truncated = $truncated")
    if (truncated > original + 0.000001) {
        println("COUNTEREXAMPLE")
        exitProcess(0)
    }
}

data class Instance<E>(val order: List<E>, val rank: Int)
typealias Distribution<E> = Map<Instance<E>, Double>

fun <E> convertDistribution(matroid: Matroid<E>, given: Distribution<E>): (List<E>) -> List<Double> {
    return { order ->
        val result = MutableList(order.size) { .0 }
        for (rank in 1..matroid.rank()) {
            val probability = given[Instance(order, rank)] ?: .0
            for (j in order.indices) {
                result[j] += probability / rank.toDouble()
                if (matroid.rank(order.subList(0, j + 1).toSet()) == rank) {
                    break
                }
            }
        }
        result
    }
}

fun <E> truncateDistribution(given: Distribution<E>, truncateTo: Int): Distribution<E> {
    val result = mutableMapOf<Instance<E>, Double>()
    for ((instance, probability) in given) {
        val newInstance = Instance(instance.order, min(instance.rank, truncateTo))
        result[newInstance] = (result[newInstance] ?: .0) + probability
    }
    return result
}