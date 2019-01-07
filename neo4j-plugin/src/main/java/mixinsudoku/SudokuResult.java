package mixinsudoku;


import org.sat4j.core.VecInt;

public class SudokuResult {

    private final long value;
    private final VecInt relationshipIds;
    private boolean isAnalyzed = false;
    private int spends = 0;
    private int falseRefs = 0;

    SudokuResult(long value, VecInt relationshipIds) {
        this.value = value;
        this.relationshipIds = relationshipIds;
    }

    public long getValue() {
        return value;
    }

    VecInt getRelationshipIds() {
        return relationshipIds;
    }

    int size() {
        return this.relationshipIds.size();
    }

    private void doAnalyze() {
        for(int i = 0; i < relationshipIds.size(); i++) {
            if(relationshipIds.get(i) > 0) {
                spends += 1;
            } else {
                falseRefs += 1;
            }
        }
        isAnalyzed = true;
    }

    String printResult() {
        if(!isAnalyzed) {
            doAnalyze();
        }
        return "Value " + value + ": " + spends + " new unique spends, " + falseRefs + " removed references.";
    }
}
