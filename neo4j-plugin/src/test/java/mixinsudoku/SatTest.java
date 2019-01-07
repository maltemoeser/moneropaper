package mixinsudoku;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.harness.junit.Neo4jRule;

import static org.junit.Assert.assertEquals;
import static org.neo4j.driver.v1.Values.parameters;


public class SatTest {


    private static final String singleUniqueSpendQuery = "CREATE (n0:Input {id: 0, value: {value}}), (n1:Input {id: 1, value: {value}})," +
            "(n2:Input {value: {value}}), (n3:Output {value: {value}})," +
            "(n4:Output {value: {value}}), (n5:Output {value: {value}})," +
            "(n0)-[:REFERENCES]->(n3), (n0)-[:REFERENCES]->(n4)," +
            "(n1)-[:REFERENCES]->(n4), (n1)-[:REFERENCES]->(n5)," +
            "(n2)-[:REFERENCES]->(n4), (n2)-[:REFERENCES]->(n5)";

    private static final String threeTrivialSpendsQuery = "CREATE (n0:Input {id: 0, value: {value}}), (n1:Input {id: 1, value: {value}})," +
            "(n2:Input {value: {value}}), (n3:Output {value: {value}})," +
            "(n4:Output {value: {value}}), (n5:Output {value: {value}})," +
            "(n0)-[:REFERENCES]->(n3)," +
            "(n1)-[:REFERENCES]->(n4)," +
            "(n2)-[:REFERENCES]->(n5)";

    @Rule
    public Neo4jRule neo4j = new Neo4jRule()
            .withProcedure(SATSudoku.class);

    @Test
    public void shouldIdentifySimpleSpendSAT() throws Throwable {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            // Given a simple graph example
            session.run("CREATE (n0:Input {id: 0, value: {value}})," +
                            "(n3:Output {value: {value}})," +
                            "(n4:Output:UniqueSpend {value: {value}})," +
                            "(n0)-[:REFERENCES]->(n3), (n0)-[:REFERENCES]->(n4)",
                    parameters("value", 10));

            // When I run the Sudoku algorithm
            session.run("CALL mixinsudoku.sat.single({value})", parameters("value", 10));

            // Then there should now be two unique spends
            //long numberUniqueSpends = session.run("MATCH (o:UniqueSpend) RETURN COUNT(o)").single().get(0).asLong();
            //assertEquals(2, numberUniqueSpends);

            long nSat = session.run("MATCH (o:SatSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(1, nSat);

            // And the deanonymized input has id 0
            long idDeanonymizedInput = session.run("MATCH (i:Input)-[:SAT_SPEND]->(o:SatSpend) RETURN i.id")
                    .single()
                    .get(0)
                    .asLong();
            assertEquals(0, idDeanonymizedInput);
        }
    }


    @Test
    public void shouldIdentifySingleUniqueSpend() throws Throwable {

        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            // Given a simple graph example
            session.run(singleUniqueSpendQuery, parameters("value", 10));

            // When I run the Sudoku algorithm
            session.run("CALL mixinsudoku.sat.single({value})", parameters("value", 10));

            // Then there should be only one unique spend
            long numberUniqueSpends = session.run("MATCH (o:SatSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(1, numberUniqueSpends);

            // And the deanonymized input has id 0
            long idDeanonymizedInput = session.run("MATCH (i:Input)-[:SAT_SPEND]->(o:SatSpend) RETURN i.id")
                    .single()
                    .get(0)
                    .asLong();
            assertEquals(0, idDeanonymizedInput);
        }
    }

    @Test
    public void shouldIdentifyThreeTrivialSpendsSAT() throws Throwable {

        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            // Given a simple graph example
            session.run(threeTrivialSpendsQuery, parameters("value", 10));

            // When I run the sudoku algorithm
            session.run("CALL mixinsudoku.sat.single({value})", parameters("value", 10));

            // Then there should be three unique spends
            long numberUniqueSpends = session.run("MATCH (o:SatSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(3, numberUniqueSpends);
        }
    }

    @Test
    public void shouldIdentifyNoSpend() throws Throwable {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            // Given a simple graph example
            session.run("CREATE (n1:Input {id: 1, value: {value}}), (n2:Input {value: {value}})," +
                            "(n4:Output {value: {value}}), (n5:Output {value: {value}})," +
                            "(n1)-[:REFERENCES]->(n4), (n1)-[:REFERENCES]->(n5)," +
                            "(n2)-[:REFERENCES]->(n4), (n2)-[:REFERENCES]->(n5)",
                    parameters("value", 10));

            // When I run the sudoku algorithm
            session.run("CALL mixinsudoku.sat.single({value})", parameters("value", 10));

            // Then there should be no unique spend
            long numberUniqueSpends = session.run("MATCH (o:SatSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(0, numberUniqueSpends);
        }
    }

    @Test
    public void shouldIdentifySpendsForTwoDenominationsSAT() throws Throwable {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            // Example from above, one trivial spend.
            session.run(singleUniqueSpendQuery, parameters("value", 10));
            session.run(threeTrivialSpendsQuery, parameters("value", 20));

            // When I run the Mixin Sudoku for all denominations
            session.run("CALL mixinsudoku.sat.all(2)");

            // Then there should be four (1@10 and 3@20) unique spends
            long numberUniqueSpends = session.run("MATCH (o:SatSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(4, numberUniqueSpends);
        }
    }

    @Test
    public void shouldIdentifySpendsForTenDenominations() throws Throwable {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), Config.build()
                .withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig())) {
            Session session = driver.session();

            int[] denominations = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

            for (int v : denominations) {
                session.run(singleUniqueSpendQuery, parameters("value", v));
            }

            // When I run the Mixin Sudoku for all denominations
            session.run("CALL mixinsudoku.sat.all(3)");

            // Then there should be ten unique spends
            long numberUniqueSpends = session.run("MATCH (o:SatSpend) RETURN COUNT(o)").single().get(0).asLong();
            assertEquals(10, numberUniqueSpends);
        }
    }
}
