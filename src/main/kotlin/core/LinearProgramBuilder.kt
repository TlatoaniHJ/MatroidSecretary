package org.example

import com.gurobi.gurobi.GRBVar
import kotlin.math.exp

enum class VariableType {
    NONNEGATIVE,
    NONPOSITIVE,
    UNBOUNDED,
}

enum class ConstraintType {
    EQUAL,
    GREATER_OR_EQUAL,
    LESS_OR_EQUAL,
}

enum class OptimizationMode {
    MAXIMIZE,
    MINIMIZE,
}

data class Term<V>(val coefficient: Double, val variable: V)

data class Expression<V>(val constant: Double, val terms: List<Term<V>>) {
    companion object {
        fun <V> fromConstant(constant: Double) = Expression<V>(constant, listOf())
        fun <V> fromVariable(variable: V) = Expression<V>(.0, listOf(Term(1.0, variable)))

        fun <V> zero() = fromConstant<V>(.0)
    }

    operator fun plus(other: Expression<V>) = Expression(constant + other.constant, terms + other.terms)
    operator fun minus(other: Expression<V>) = this + other.times(-1.0)
    operator fun times(coefficient: Double) = Expression(constant * coefficient, terms.map { (a, v) -> Term(a * coefficient, v) })
    operator fun div(coefficient: Double) = this * (1.0 / coefficient)

    operator fun plus(other: Double) = this + fromConstant(other)
    operator fun minus(other: Double) = this - fromConstant(other)
    fun isConstant() = terms.isEmpty()

    override fun toString(): String {
        var components = mutableListOf<String>()
        if (constant != .0 || terms.isEmpty()) {
            components.add(constant.toString())
        }
        for ((coefficient, variable) in terms) {
            var component = if (variable is GRBVar) {
                "x_${variable.index()}"
            } else {
                variable.toString()
            }
            if (coefficient != 1.0) {
                component = "$coefficient$component"
            }
            components.add(component)
        }
        return components.joinToString(" + ")
    }
}

operator fun <V> Double.times(expression: Expression<V>) = expression * this

interface LinearProgramBuilder<V> {
    fun newVariableRaw(type: VariableType): V
    fun newConstraint(left: Expression<V>, type: ConstraintType, right: Expression<V>)
    fun optimize(mode: OptimizationMode, expression: Expression<V>)

    fun newVariable(type: VariableType) = Expression.fromVariable(newVariableRaw(type))
    fun newConstraint(left: Expression<V>, type: ConstraintType, right: Double) = newConstraint(left, type, Expression.fromConstant(right))
    fun newConstraint(left: Double, type: ConstraintType, right: Expression<V>) = newConstraint(Expression.fromConstant(left), type, right)

    fun extractVariable(expression: Expression<V>): V {
        if (expression.terms.size == 1 && expression.terms[0].coefficient == 1.0 && expression.constant == .0) {
            return expression.terms[0].variable
        }
        val variable = newVariable(VariableType.UNBOUNDED)
        newConstraint(variable, ConstraintType.EQUAL, expression)
        return variable.terms[0].variable
    }
}