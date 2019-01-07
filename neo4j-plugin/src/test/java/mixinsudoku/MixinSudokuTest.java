package mixinsudoku;

import apoc.periodic.Periodic;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.harness.junit.Neo4jRule;

import static org.junit.Assert.assertEquals;
import static org.neo4j.driver.v1.Values.parameters;


public class MixinSudokuTest {

    @Rule
    public Neo4jRule neo4j = new Neo4jRule()
            .withProcedure(MixinSudoku.class)
            .withProcedure(Periodic.class);


    @Test
    public void testZeroMixins() throws Throwable {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            session.run("CREATE (n0:Input {id: 0, mixin: 0}), (n1:Input {id: 1, mixin: 0})," +
                    "(n2:Output {id: 2}), (n3:Output {id: 3})," +
                    "(n0)-[:REFERENCES]->(n2), (n1)-[:REFERENCES]->(n3)");

            session.run("CALL mixinsudoku.zeromixin()");

            long numberUniqueSpends = session.run("MATCH (o:UniqueSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(2, numberUniqueSpends);
        }
    }

    @Test
    public void basicTestForGraphSudoku() throws Throwable {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            session.run("CREATE (n0:Input {id: 0, mixin: 0})," +
                    "(n1:Input {id: 1}), (n2:Input {id: 2})," +
                    "(n3:Output {id: 3}), (n4:Output {id: 4}), (n5:Output {id: 5}), (n6:Output {id: 6})," +
                    "(n0)-[:REFERENCES]->(n3), (n1)-[:REFERENCES]->(n3), (n1)-[:REFERENCES]->(n4)," +
                    "(n2)-[:REFERENCES]->(n4), (n2)-[:REFERENCES]->(n5), (n2)-[:REFERENCES]->(n6)");


            session.run("CALL mixinsudoku.zeromixin()");
            session.run("CALL mixinsudoku.sudoku()");

            long numberUniqueSpends = session.run("MATCH (o:UniqueSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(2, numberUniqueSpends);

            long firstId = session.run("MATCH (o:UniqueSpend) RETURN MIN(o.id)").single().get(0).asLong();
            assertEquals(3, firstId);

            long secondId = session.run("MATCH (o:UniqueSpend) RETURN MAX(o.id)").single().get(0).asLong();
            assertEquals(4, secondId);

            long maxIter = session.run("MATCH (o:UniqueSpend) RETURN MAX(o.iteration)").single().get(0).asLong();
            assertEquals(2, maxIter);
        }
    }

    @Test
    public void allMatch() throws Throwable {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            session.run("CREATE (n0:Input {id: 0, mixin: 0})," +
                    "(n1:Input {id: 1}), (n2:Input {id: 2}), (n7:Input {id:7})," +
                    "(n3:Output {id: 3}), (n4:Output {id: 4}), (n5:Output {id: 5}), (n6:Output {id: 6})," +
                    "(n0)-[:REFERENCES]->(n3), (n1)-[:REFERENCES]->(n3), (n1)-[:REFERENCES]->(n4)," +
                    "(n2)-[:REFERENCES]->(n4), (n2)-[:REFERENCES]->(n5), (n2)-[:REFERENCES]->(n6)," +
                    "(n7)-[:REFERENCES]->(n6), (n7)-[:REFERENCES]->(n3)");


            session.run("CALL mixinsudoku.zeromixin()");
            session.run("CALL mixinsudoku.sudoku()");

            long numberUniqueSpends = session.run("MATCH (o:UniqueSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(4, numberUniqueSpends);

            long maxIter = session.run("MATCH (o:UniqueSpend) RETURN MAX(o.iteration)").single().get(0).asLong();
            assertEquals(3, maxIter);
        }
    }

    @Test
    public void shouldIdentifySimpleSpend() throws Throwable {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            // Given a simple graph example
            session.run("CREATE (n0:Input {id: 0, value: {value}})," +
                            "(n3:Output {value: {value}})," +
                            "(n4:Output:UniqueSpend {value: {value}, iteration: 1})," +
                            "(n0)-[:REFERENCES]->(n3), (n0)-[:REFERENCES]->(n4)",
                    parameters("value", 10));

            // When I run the Sudoku algorithm
            session.run("CALL mixinsudoku.sudoku()");

            // Then there should now be two unique spends
            long numberUniqueSpends = session.run("MATCH (o:UniqueSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(2, numberUniqueSpends);

            long nIteration = session.run("MATCH (o:Output) WHERE o.iteration >= 2 RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(1, nIteration);

            // And the deanonymized input has id 0
            long idDeanonymizedInput = session.run("MATCH (i:Input)-[:SPENDS]->(o:UniqueSpend) RETURN i.id")
                    .single()
                    .get(0)
                    .asLong();
            assertEquals(0, idDeanonymizedInput);
        }
    }
}
