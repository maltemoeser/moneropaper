package mixinsudoku;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Procedure;

import static org.neo4j.procedure.Mode.WRITE;


/**
 * Mixin Sudoku Solver for Neo4j.
 *
 * @author Malte Moeser
 */
public class MixinSudoku {

    @Context
    public GraphDatabaseService db;

    @Context
    public Log log;

    @Procedure(name = "mixinsudoku.zeromixin", mode = WRITE)
    public void sudokuZeroMixin() {
        db.execute("CALL apoc.periodic.iterate(" +
                "\"MATCH (i:Input)" +
                " WHERE i.mixin = 0" +
                " RETURN i\"," +
                " \"MATCH (i)-[:REFERENCES]->(o:Output)" +
                " SET o:UniqueSpend, o.iteration = 1" +
                " CREATE (i)-[:SPENDS]->(o)\"," +
                "{batchSize: 10000, parallel:true, iterateList:true})");
    }

    @Procedure(name = "mixinsudoku.sudoku", mode = WRITE)
    public void sudoku() {
        db.execute("CALL apoc.periodic.commit(\"" +
                "  MATCH (i:Input)-[:REFERENCES]->(x)" +
                "  WHERE NOT x:UniqueSpend" +
                "  WITH i, COUNT(DISTINCT(x)) as cnt" +
                "  WHERE cnt = 1" +
                "  WITH i limit {limit}" +
                "  MATCH (i)-[:REFERENCES]->(y)" +
                "  WHERE NOT y:UniqueSpend" +
                "  MATCH (i)-[:REFERENCES]->(z:UniqueSpend)" +
                "  WITH i, y, MAX(z.iteration) AS iter" +
                "  SET y:UniqueSpend, y.iteration = iter + 1" +
                "  CREATE (i)-[:SPENDS]->(y)" +
                "  RETURN count(*)\"," +
                "  {limit: 10000}" +
                ")");
    }
}
