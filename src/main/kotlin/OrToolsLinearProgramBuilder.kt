package org.example

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable

class OrToolsLinearProgramBuilder : LinearProgramBuilder<MPVariable> {

    init {
        // Essential: Loads the C++ native binaries bundled in the OR-Tools jar
        Loader.loadNativeLibraries()
    }

    // Initialize the GLOP solver for pure continuous linear programming
    val solver: MPSolver = MPSolver.createSolver("GLOP")
        ?: throw IllegalStateException("GLOP solver could not be created.")

    override fun newVariableRaw(type: VariableType): MPVariable {
        return when (type) {
            VariableType.NONNEGATIVE -> solver.makeNumVar(0.0, Double.POSITIVE_INFINITY, "")
            VariableType.NONPOSITIVE -> solver.makeNumVar(Double.NEGATIVE_INFINITY, 0.0, "")
            VariableType.UNBOUNDED -> solver.makeNumVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "")
        }
    }

    override fun newConstraint(
        left: Expression<MPVariable>,
        type: ConstraintType,
        right: Expression<MPVariable>
    ) {
        // Transform the equation to standard form: (left - right) op 0
        val combined = left - right

        // OR-Tools expects bounds in the form: lb <= expression <= ub
        // So for an expression E + constant >= 0, we rewrite as E >= -constant
        val rhs = -combined.constant

        val constraint = solver.makeConstraint()
        when (type) {
            ConstraintType.EQUAL -> constraint.setBounds(rhs, rhs)
            ConstraintType.GREATER_OR_EQUAL -> constraint.setBounds(rhs, Double.POSITIVE_INFINITY)
            ConstraintType.LESS_OR_EQUAL -> constraint.setBounds(Double.NEGATIVE_INFINITY, rhs)
        }

        // Aggregate terms by variable in case the expression has multiple terms for the same MPVariable
        combined.terms
            .groupBy { it.variable }
            .forEach { (variable, terms) ->
                val totalCoefficient = terms.sumOf { it.coefficient }
                if (totalCoefficient != 0.0) {
                    constraint.setCoefficient(variable, totalCoefficient)
                }
            }
    }

    override fun optimize(mode: OptimizationMode, expression: Expression<MPVariable>) {
        val objective = solver.objective()
        objective.clear()

        // Apply constant offset
        objective.setOffset(expression.constant)

        // Aggregate and apply coefficients
        expression.terms
            .groupBy { it.variable }
            .forEach { (variable, terms) ->
                val totalCoefficient = terms.sumOf { it.coefficient }
                if (totalCoefficient != 0.0) {
                    objective.setCoefficient(variable, totalCoefficient)
                }
            }

        when (mode) {
            OptimizationMode.MAXIMIZE -> objective.setMaximization()
            OptimizationMode.MINIMIZE -> objective.setMinimization()
        }
    }

    /**
     * Solves the linear program and returns the OR-Tools result status.
     */
    fun solve(): MPSolver.ResultStatus {
        return solver.solve()
    }

    /**
     * Helper to retrieve the solved value of a given variable.
     */
    fun getValue(variable: MPVariable): Double {
        return variable.solutionValue()
    }

    /**
     * Helper to retrieve the final objective value.
     */
    fun getObjectiveValue(): Double {
        return solver.objective().value()
    }

    /**
     * Returns the total number of variables currently added to the solver.
     */
    fun getNumVariables(): Int {
        return solver.numVariables()
    }

    /**
     * Returns the total number of constraints currently added to the solver.
     */
    fun getNumConstraints(): Int {
        return solver.numConstraints()
    }
}