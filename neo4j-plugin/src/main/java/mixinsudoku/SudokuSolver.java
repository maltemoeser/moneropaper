package mixinsudoku;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.core.DataStructureFactory;
import org.sat4j.minisat.core.Solver;
import org.sat4j.minisat.orders.RandomLiteralSelectionStrategy;
import org.sat4j.minisat.orders.VarOrderHeap;
import org.sat4j.minisat.restarts.Glucose21Restarts;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.sat4j.minisat.SolverFactory.newMiniLearningHeapRsatExpSimp;


public class SudokuSolver implements Callable<SudokuResult> {

    private GraphDatabaseService db;

    private ISolver solver;
    private Map<String, Object> readParams = new HashMap<>();
    private VecInt solution;
    private long value;

    static final String inputQuery = "MATCH (i:Input)" +
            " WHERE NOT (i)-[:SPENDS]->() AND i.value = {value}" +
            " MATCH (i)-[r:REFERENCES]->(o:Output)" +
            " WHERE NOT o:UniqueSpend" +
            " RETURN ID(i) as inputId, collect(ID(r)) as relIds";

    static final String outputQuery = "MATCH (i:Input)" +
            "  WHERE NOT (i)-[:SPENDS]->() AND i.value = {value}" +
            "  MATCH (i)-[:REFERENCES]->(o:Output)" +
            "  WHERE NOT o:UniqueSpend" +
            "  WITH DISTINCT(o)" +
            "  MATCH (o)<-[r:REFERENCES]-(x:Input)" +
            "  WHERE NOT (x)-[:SPENDS]->()" +
            "  RETURN ID(o) as outputId, collect(ID(r)) as relIds";


    SudokuSolver(GraphDatabaseService db, long value) {
        this.db = db;
        this.value = value;
        readParams.put("value", value);
    }

    private void initializeSolver() {
        Solver<DataStructureFactory> s = newMiniLearningHeapRsatExpSimp();
        s.setRestartStrategy(new Glucose21Restarts());
        s.setLearnedConstraintsDeletionStrategy(s.glucose);
        s.setOrder(new VarOrderHeap(new RandomLiteralSelectionStrategy()));
        s.setTimeout(3600); // 1 hour
        solver = s;
    }

    private void createInputClauses() {
        Result inputData = db.execute(inputQuery, readParams);
        createSolverClauses(inputData, solver, true);
    }

    private void createOutputClauses() {
        Result outputData = db.execute(outputQuery, readParams);
        createSolverClauses(outputData, solver, false);
    }

    /**
     * Iterates over a result of a cypher query and creates clauses based on the relationship ids in each row
     *
     * @param rows              the cypher Result
     * @param solver            the SAT solver to which the clauses are added
     * @param oneIdIsAlwaysTrue determines whether at least one of the relationship ids in a row must be always true
     */
    private void createSolverClauses(Result rows, ISolver solver, boolean oneIdIsAlwaysTrue) {
        try {
            while (rows.hasNext()) {
                Map<String, Object> row = rows.next();
                List<Long> relIds = (List<Long>) row.get("relIds");

                int[] array = new int[relIds.size()];
                for (int i = 0; i < relIds.size(); i++) array[i] = relIds.get(i).intValue() + 1;

                if (oneIdIsAlwaysTrue) {
                    // choose from all possible inputs/outputs
                    solver.addClause(new VecInt(array));
                }

                // but only choose one at a time
                for (int i = 0; i < array.length - 1; i++) {
                    for (int j = i + 1; j < array.length; j++) {
                        int[] n = {-array[i], -array[j]};
                        solver.addClause(new VecInt(n));
                    }
                }
            }
        } catch (ContradictionException e) {
            e.printStackTrace();
        }
    }

    private void solveSatProblem() {
        try {
            // Solve the sudoku
            while (solver.isSatisfiable()) {
                VecInt blockingClause = new VecInt(solver.model().length);
                VecInt nextSolution = new VecInt(solver.model().length);

                for (int i : solver.model()) {
                    if (i > 0) {
                        // We only care about the true assignments that we can carry over from the previous solution.
                        // This should give us better pruning of the search space.
                        if (solution == null || solution.contains(i)) {
                            nextSolution.push(i);
                            blockingClause.push(-i);
                        }
                    }
                }
                solution = nextSolution;

                if (blockingClause.size() == 0) {
                    // can happen if there is not a single valid assignment
                    break;
                } else {
                    // remove current solution from model
                    solver.addBlockingClause(blockingClause);
                }
            }
        } catch (TimeoutException | ContradictionException e) {
            //e.printStackTrace();
        }
    }

    private void writeUniqueSpendsToDatabase() {
        for (int i = 0; i < solution.size(); i++) {
            Map<String, Object> writeParams = new HashMap<>();
            // decrement ID by 1 again
            writeParams.put("relId", solution.get(i) - 1);
            db.execute("MATCH (i:Input)-[r:REFERENCES]->(o:Output)" +
                    " WHERE ID(r) = {relId}" +
                    " SET o:SatSpend" +
                    " CREATE (i)-[:SAT_SPEND]->(o)", writeParams);
        }
    }

    @Override
    public SudokuResult call() throws Exception {
        return solve();
    }

    SudokuResult solve() {
        initializeSolver();
        createInputClauses();
        createOutputClauses();
        solveSatProblem();
        writeUniqueSpendsToDatabase();
        return new SudokuResult(value, solution);
    }
}
