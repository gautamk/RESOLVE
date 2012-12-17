package edu.clemson.cs.r2jt.mathtype;

import edu.clemson.cs.r2jt.mathtype.MathSymbolTable.FacilityStrategy;
import edu.clemson.cs.r2jt.mathtype.MathSymbolTable.ImportStrategy;

public class UnqualifiedNameQuery
        extends
            BaseMultimatchSymbolQuery<SymbolTableEntry> {

    public UnqualifiedNameQuery(String searchString,
            ImportStrategy importStrategy, FacilityStrategy facilityStrategy,
            boolean stopAfterFirst, boolean localPriority) {

        super(new UnqualifiedPath(importStrategy, facilityStrategy,
                localPriority), new NameSearcher(searchString, stopAfterFirst));
    }

    public UnqualifiedNameQuery(String searchString) {
        this(searchString, ImportStrategy.IMPORT_NONE,
                FacilityStrategy.FACILITY_INSTANTIATE, true, true);
    }
}