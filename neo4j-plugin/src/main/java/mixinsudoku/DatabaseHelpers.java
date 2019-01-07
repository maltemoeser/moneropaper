package mixinsudoku;

import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Procedure;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.SCHEMA;
import static org.neo4j.procedure.Mode.WRITE;


public class DatabaseHelpers {

    @Context
    public GraphDatabaseService db;

    @Context
    public Log log;

    @Procedure(name = "mixinsudoku.schema", mode = SCHEMA)
    public void setupDatabase() {
        log.info("Creating database indizes.");
        db.execute("CREATE INDEX ON :Block(height)");
        db.execute("CREATE INDEX ON :Block(hash)");
        db.execute("CREATE INDEX ON :Transaction(hash)");
        db.execute("CREATE INDEX ON :Output(index)");
        db.execute("CREATE INDEX ON :Output(value)");
        db.execute("CREATE INDEX ON :Input(mixin)");
        db.execute("CREATE INDEX ON :Input(value)");
    }

    @Procedure(name = "mixinsudoku.coinbase", mode = WRITE)
    public void createCoinbaseLabels() {
        log.info("Creating coinbase labels.");
        db.execute("MATCH (n:Transaction) WHERE NOT (n)-[:TX_INPUT]->() SET n:Coinbase");
    }


    @Procedure(name = "mixinsudoku.checkdb", mode = READ)
    public Stream<QueryOutput> checkDatabase() {
        int unsuccessful = 0;
        int total = 0;

        log.info("Verifying correctness of a few database entries.");

        if (!checkBlockHashes()) {
            log.warn("Error verifying correctness of block hashes.");
            unsuccessful += 1;
        }
        total += 1;

        if (!checkNumberOfTransactionsInBlock()) {
            log.warn("Error verifying correctness of number of transactions in blocks.");
            unsuccessful += 1;
        }
        total += 1;

        if (!numberOfMixins()) {
            log.warn("Error verifying correctness of number of mixins.");
            unsuccessful += 1;
        }
        total += 1;

        if (!correctReferences()) {
            log.warn("Error verifying correctness of references.");
            unsuccessful += 1;
        }
        total += 1;

        log.info(unsuccessful + " out of " + total + " tests were unsuccessful.");

        return Stream.of(new QueryOutput(total - unsuccessful + " out of " + total + " tests successful."));
    }

    /**
     * Verifies the correct mapping between block heights and block hashes.
     */
    private boolean checkBlockHashes() {
        Node block = db.findNode(Label.label("Block"), "height", 100000);
        if (!block.getProperty("hash").equals("45eca0b6c1845c073d365fedb78ab14e4313d6392a2fde217a46882875428463")) {
            return false;
        }

        block = db.findNode(Label.label("Block"), "height", 1000000);
        if (!block.getProperty("hash").equals("a886ef5149902d8342475fee9bb296341b891ac67c4842f47a833f23c00ed721")) {
            return false;
        }

        block = db.findNode(Label.label("Block"), "height", 1250000);
        if (!block.getProperty("hash").equals("89df06e4bbf3f3af5a84c00e75ee8fd775eb9f9bc41d761f50b24afdc40dd581")) {
            return false;
        }

        return true;
    }

    /**
     * Verifies the expected number of transactions within a few selected blocks.
     */
    private boolean checkNumberOfTransactionsInBlock() {
        Node block = db.findNode(Label.label("Block"), "height", 100000);
        if (block.getDegree(RelationshipType.withName("IN_BLOCK")) != 3) {
            return false;
        }

        block = db.findNode(Label.label("Block"), "height", 1000000);
        if (block.getDegree(RelationshipType.withName("IN_BLOCK")) != 1) {
            return false;
        }

        block = db.findNode(Label.label("Block"), "height", 1250000);
        if (block.getDegree(RelationshipType.withName("IN_BLOCK")) != 3) {
            return false;
        }

        return true;
    }

    /**
     * Verifies the expected number of mixins of each input in a transaction.
     * (Inputs don't need to be the same for every input, but for the ones tested here, they are.)
     */
    private boolean numberOfMixins() {
        String txhash = "9c3c0086ef9aa98f370dac303c5dca109678bf95c9e4252e103dab16dce46fa8";
        Node tx = db.findNode(Label.label("Transaction"), "hash", txhash);

        if (!checkNumberOfMixins(tx, 0)) {
            return false;
        }

        txhash = "3b26d90c460ccab37925300ca830b569636ba8053f859a95312c227312d1a72d";
        tx = db.findNode(Label.label("Transaction"), "hash", txhash);

        if (!checkNumberOfMixins(tx, 3)) {
            return false;
        }

        txhash = "a50ba059e1dda2c8ee6caa0f3c74569856462f3515d7a3e6588ec87090c05908";
        tx = db.findNode(Label.label("Transaction"), "hash", txhash);

        if (!checkNumberOfMixins(tx, 4)) {
            return false;
        }

        return true;
    }

    private boolean checkNumberOfMixins(Node node, int mixins) {
        for (Relationship rel : node.getRelationships(RelationshipType.withName("TX_INPUT"))) {
            Node input = rel.getEndNode();
            if (!input.getProperty("mixin").equals(mixins)) {
                log.warn("Expected " + mixins + " mixins, found " + input.getProperty("mixin"));
                return false;
            }
        }
        return true;
    }

    /**
     * Verifies that inputs reference the correct outputs, by checking the height of the block
     * that the referenced outputs were included in.
     */
    private boolean correctReferences() {
        String txhash = "9c3c0086ef9aa98f370dac303c5dca109678bf95c9e4252e103dab16dce46fa8";
        Node tx = db.findNode(Label.label("Transaction"), "hash", txhash);
        Integer[] arr1 = {72186};
        HashSet<Integer> blockHeights = new HashSet<>(Arrays.asList(arr1));

        if (!correctReferencesOfFirstInput(tx, 5000000000L, blockHeights)) {
            return false;
        }

        txhash = "3b26d90c460ccab37925300ca830b569636ba8053f859a95312c227312d1a72d";
        tx = db.findNode(Label.label("Transaction"), "hash", txhash);
        Integer[] arr2 = {141541, 323002, 734661, 924393};
        blockHeights = new HashSet<>(Arrays.asList(arr2));

        if (!correctReferencesOfFirstInput(tx, 700000000000L, blockHeights)) {
            return false;
        }

        txhash = "a50ba059e1dda2c8ee6caa0f3c74569856462f3515d7a3e6588ec87090c05908";
        tx = db.findNode(Label.label("Transaction"), "hash", txhash);
        Integer[] arr3 = {1236088, 1241771, 1242716, 1245605, 1249987};
        blockHeights = new HashSet<>(Arrays.asList(arr3));

        if (!correctReferencesOfFirstInput(tx, 0L, blockHeights)) {
            return false;
        }

        return true;
    }


    private boolean correctReferencesOfFirstInput(Node node, long value, Set<Integer> blockHeights) {
        boolean didNotSkipAll = false;
        for (Relationship rel : node.getRelationships(RelationshipType.withName("TX_INPUT"))) {
            Node input = rel.getEndNode();
            if (!input.getProperty("value").equals(value)) {
                continue;
            }
            didNotSkipAll = true;
            for (Relationship ref : input.getRelationships(RelationshipType.withName("REFERENCES"))) {
                Node block = ref
                        .getEndNode() // Output
                        .getSingleRelationship(RelationshipType.withName("TX_OUTPUT"), Direction.INCOMING)
                        .getStartNode() // Transaction
                        .getSingleRelationship(RelationshipType.withName("IN_BLOCK"), Direction.OUTGOING)
                        .getEndNode(); // Block
                Integer blockHeight = (Integer) block.getProperty("height");
                if (!blockHeights.contains(blockHeight)) {
                    log.warn("Found height of " + blockHeight + ", expected " + blockHeights.toString());
                    return false;
                }
            }
            break;
        }
        return didNotSkipAll;
    }


    /**
     * Sanity checks for the sudoku algorithm.
     */
    @Procedure(name = "mixinsudoku.checksudoku", mode = READ)
    public Stream<QueryOutput> checkSudokuResult() {
        Result result = db.execute("MATCH (i:Input)-[:SPENDS]->(o:Output)\n" +
                "WITH i, COUNT(o) as cnt\n" +
                "WHERE cnt > 1\n" +
                "RETURN i, cnt");
        if (result.hasNext()) {
            String error = "Error in Sudoku algorithm, result contains inputs that spend multiple outputs.";
            log.warn(error);
            return Stream.of(new QueryOutput(error));
        }
        return Stream.of(new QueryOutput("Test successful."));
    }
}
