package org.example

import com.gurobi.gurobi.*

class GurobiLinearProgramBuilder(logToConsole: Boolean = true) : LinearProgramBuilder<GRBVar>, AutoCloseable {

    val env: GRBEnv
    val model: GRBModel

    init {
        // Initialize an empty environment, configure logging, then start it
        env = GRBEnv(true)
        if (logToConsole) {
            //env.set(GRB.StringParam.LogFile, "/Users/Tlatoani/Projects/MatroidSecretary/gurobi_matroid.log")
        } else {
            env.set(GRB.IntParam.OutputFlag, 0)
        }
        env.start()

        // Create the model using the environment
        model = GRBModel(env)

        // Limit Gurobi to exactly 1 thread
        model.set(GRB.IntParam.Threads, 2)

        // Explicitly disable the Crossover phase
        model.set(GRB.IntParam.Crossover, 0)

        // Optional: Force Gurobi to use the Barrier method by default
        model.set(GRB.IntParam.Method, GRB.METHOD_BARRIER)
    }

    override fun newVariableRaw(type: VariableType): GRBVar {
        val lb = when (type) {
            VariableType.NONNEGATIVE -> 0.0
            VariableType.NONPOSITIVE -> -GRB.INFINITY
            VariableType.UNBOUNDED -> -GRB.INFINITY
        }
        val ub = when (type) {
            VariableType.NONNEGATIVE -> GRB.INFINITY
            VariableType.NONPOSITIVE -> 0.0
            VariableType.UNBOUNDED -> GRB.INFINITY
        }
        // Add variable to the model (lower bound, upper bound, objective weight, type, name)
        return model.addVar(lb, ub, 0.0, GRB.CONTINUOUS, "")
    }

    override fun newConstraint(left: Expression<GRBVar>, type: ConstraintType, right: Expression<GRBVar>) {
        val lhs = left.toGRBLinExpr()
        val rhs = right.toGRBLinExpr()

        val sense = when (type) {
            ConstraintType.EQUAL -> GRB.EQUAL
            ConstraintType.GREATER_OR_EQUAL -> GRB.GREATER_EQUAL
            ConstraintType.LESS_OR_EQUAL -> GRB.LESS_EQUAL
        }

        model.addConstr(lhs, sense, rhs, "")
    }

    override fun optimize(mode: OptimizationMode, expression: Expression<GRBVar>) {
        val objExpr = expression.toGRBLinExpr()
        val sense = when (mode) {
            OptimizationMode.MAXIMIZE -> GRB.MAXIMIZE
            OptimizationMode.MINIMIZE -> GRB.MINIMIZE
        }

        model.setObjective(objExpr, sense)
        model.optimize()
    }

    /**
     * Helper extension function to convert your custom Expression into Gurobi's linear expression.
     */
    private fun Expression<GRBVar>.toGRBLinExpr(): GRBLinExpr {
        val grbExpr = GRBLinExpr()
        grbExpr.addConstant(this.constant)

        if (this.terms.isNotEmpty()) {
            val coeffs = DoubleArray(this.terms.size)
            val vars = arrayOfNulls<GRBVar>(this.terms.size)

            for (i in this.terms.indices) {
                coeffs[i] = this.terms[i].coefficient
                vars[i] = this.terms[i].variable
            }

            // Massive JNI Optimization: Only ONE native call per constraint
            grbExpr.addTerms(coeffs, vars)
        }
        return grbExpr
    }

    /**
     * Retrieves the optimal value of a raw variable after optimization.
     */
    fun getValue(variable: GRBVar): Double {
        return variable.get(GRB.DoubleAttr.X)
    }

    /**
     * Retrieves the final objective value of the model.
     */
    fun getObjectiveValue(): Double {
        return model.get(GRB.DoubleAttr.ObjVal)
    }

    /**
     * Cleans up native C-pointers to prevent memory leaks.
     */
    override fun close() {
        model.dispose()
        env.dispose()
    }

    /**
     * Forces Gurobi to process pending model modifications.
     * Call this if you need accurate variable/constraint counts before calling optimize().
     */
    fun updateModel() {
        model.update()
    }

    /**
     * Retrieves the total number of variables currently integrated into the model.
     * Note: Requires model.update() or model.optimize() to have been called first.
     */
    fun getNumVariables(): Int {
        return model.get(GRB.IntAttr.NumVars)
    }

    /**
     * Retrieves the total number of linear constraints currently integrated into the model.
     * Note: Requires model.update() or model.optimize() to have been called first.
     */
    fun getNumConstraints(): Int {
        return model.get(GRB.IntAttr.NumConstrs)
    }

    /**
     * Retrieves the total number of non-zero coefficients in the linear constraint matrix.
     * Note: Requires model.update() or model.optimize() to have been called first.
     */
    fun getNumNonZeros(): Int {
        return model.get(GRB.IntAttr.NumNZs)
    }
}