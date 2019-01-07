package mixinsudoku;

import org.neo4j.graphdb.Result;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.sat4j.core.VecInt;
import org.sat4j.specs.ISolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.WRITE;


public class SATSudoku {

    @Context
    public GraphDatabaseAPI db;

    @Context
    public Log log;

    private static final String updateQuery = "MATCH (i:Input)-[r:REFERENCES]->(o:Output)" +
            " WHERE ID(r) = {relId}" +
            " SET o:UniqueSpend, o:SatSpend" +
            " CREATE (i)-[:SPENDS]->(o)";


    @Procedure(name = "mixinsudoku.sat.all", mode = WRITE)
    public Stream<QueryOutput> sudokuAllSAT(@Name("nThreads") long nThreads) {

        List<Long> denominations = getAllDenominations();

        // Remove RingCT as it is too computationally intensive
        Long zero = 0L;
        denominations.remove(zero);

        ExecutorService executor = Executors.newFixedThreadPool((int) nThreads);
        CompletionService<SudokuResult> completionService = new ExecutorCompletionService<>(executor);

        // Add tasks to be processed
        for (long value : denominations) {
            completionService.submit(new SudokuSolver(db, value));
        }
        log.info("Added " + denominations.size() + " tasks to the pool.");
        executor.shutdown();

        // Retrieve results
        List<Integer> uniqueSpends = new ArrayList<>();
        try {
            while (!executor.isTerminated()) {
                Future<SudokuResult> future = completionService.take();
                SudokuResult result = future.get();
                uniqueSpends.add(result.size());
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return Stream.of(new QueryOutput("Added " + uniqueSpends.stream().mapToInt(Integer::intValue).sum() + " new spends."));
    }


    @Procedure(name = "mixinsudoku.sat.single", mode = WRITE)
    public Stream<QueryOutput> sudokuSAT(@Name("value") long value) {
        SudokuSolver solver = new SudokuSolver(db, value);
        SudokuResult result = solver.solve();
        return Stream.of(new QueryOutput(result.printResult()));
    }


    private List<Long> getAllDenominations() {
        // retrieve all denominations with potential for deanonymization
        Result rows = db.execute("MATCH (i:Input)" +
                " WHERE NOT (i)-[:SPENDS]->()" +
                " RETURN DISTINCT(i.value) as value");
        List<Long> denominations = new ArrayList<>(1000);

        while (rows.hasNext()) {
            Map<String, Object> row = rows.next();
            denominations.add((Long) row.get("value"));
        }
        return denominations;
    }


    private void writeUniqueSpendsToDatabase(VecInt solution) {
        log.info("Writing " + solution.size() + " rels to database: " + solution);
        for (int i = 0; i < solution.size(); i++) {
            Map<String, Object> writeParams = new HashMap<>();
            // decrement ID by 1 again
            writeParams.put("relId", solution.get(i) - 1);
            db.execute(updateQuery, writeParams);
        }
    }


    void logSolverInformation(ISolver solver, long value) {
        log.info("SAT problem for value " + value +
                " currently has " + solver.nVars() + " variables" +
                " and " + solver.nConstraints() + " constraints.");
    }
}
