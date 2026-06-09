# Slurm & Cluster Cheat Sheet (cml-scavenger)

## 1. Monitor the 2TB Machine (cml35)
To see exactly who is on `cml35`, how long they have left, and what resources (GPUs/RAM) they are hoarding.

**The Combined Master Command:**
`squeue -w cml35 -o "%.23i %.10u %.15j %.2t %.10M %.10L %.6C %.8m %.25b"`
*(Columns: JobID | User | Job Name | State | Time Used | Time Left | CPUs | Memory | TRES)*

`squeue -w cmlcpu01 -o "%.12i %.10u %.15j %.2t %.10M %.10L %.6C %.8m %.25b"`

## 2. Check the "Waiting Room" (Pending Queue)
To see all jobs currently in line for the partition, including how many CPUs/RAM they want and why they are waiting:

`squeue -p cml-scavenger -t PD -o "%.8i %.12u %.6C %.10m %.20R"`
*(Look at the far-right column to see if they are waiting on `(Resources)` or `(Priority)`).*

## 3. Manage Your Own Jobs
To see a quick summary of everything currently running or pending under your username:

`squeue -u $USER`

To instantly kill one of your jobs (if you need to clear the way or fix a bug):

`scancel <JOB_ID>`

## 4. Live Feed of Your Solver
To watch the Kotlin compilation and Gurobi matrix generation in real-time. (You can safely `Ctrl+C` to exit the view; the job will keep running in the background):

`tail -f solver_log_*.txt`
*(If you have multiple log files in the directory, replace the `*` with your actual Job ID).*

## 5. Helpful Extras: Node Hunting & Diagnostics

**Find Empty Machines Now:**
To see a list of completely empty nodes, ranked by memory size:

`sinfo -p cml-scavenger --states=idle -N --sort=-m --format="%15N %10c %15m"`
`sinfo -p cml-scavenger -N --sort=-m --format="%15N %10c %15m"`
sinfo -p cml-scavenger -N --sort=-m --format="%15N %10c %15m %30G"

**The Autopsy Report (Post-Job Efficiency):**
Once a job finishes or is canceled, use this to see its high-water mark for RAM usage. This tells you if you can safely shrink your `--mem` request next time to skip the queue:

`seff <JOB_ID>`

# Solver Bash Script
#SBATCH --sockets-per-node=1     # Guarantee all cores are on the same physical processor chip
#SBATCH --mincpus=32

#SBATCH --sockets-per-node=1     # Guarantee all cores are on the same physical processor chip
```
cat << 'EOF' > run_solver.sh
#!/bin/bash
#SBATCH --job-name=matroid_tight_11_nnz
#SBATCH --partition=cml-scavenger
#SBATCH --qos=cml-scavenger
#SBATCH --account=cml-scavenger
#SBATCH --time=3-00:00:00
#SBATCH --nodes=1                # Guarantee all cores are on the same physical machine
#SBATCH --cpus-per-task=8
#SBATCH --mem=60G
#SBATCH --array=1-40
#SBATCH --output=solver_log_%A_%a.txt

# 1. Setup the environment
module purge
module load openjdk/22
module load gurobi/12.0.3
hash -r

# 2. Navigate into the project directory
cd ~/MatroidSecretary

# 3. Execute the solver
echo "--- Starting Solver for Array Task $SLURM_ARRAY_TASK_ID ---"

# Memory fixed: JVM gets 8G, leaving 52G for Gurobi's native C++ engine.
# Arguments: Master Job ID ($SLURM_ARRAY_JOB_ID), Array Index ($SLURM_ARRAY_TASK_ID), then your custom args.
# ... slurm headers ...

# Create a unique local copy on the specific compute node
LOCAL_JAR="/tmp/matroid_${SLURM_ARRAY_JOB_ID}_${SLURM_ARRAY_TASK_ID}.jar"
cp ~/MatroidSecretary/build/libs/MatroidSecretary-1.0-SNAPSHOT.jar $LOCAL_JAR

# Run the local copy
java -Xmx8G -Djava.library.path=$GUROBI_HOME/lib -cp $LOCAL_JAR \
org.example.MainTightConjectureSingleKt \
$SLURM_ARRAY_JOB_ID $SLURM_ARRAY_TASK_ID 7 0 11

# Clean up after the job finishes
rm $LOCAL_JAR
EOF
```