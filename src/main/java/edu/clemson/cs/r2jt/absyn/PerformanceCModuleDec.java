/**
 * PerformanceCModuleDec.java
 * ---------------------------------
 * Copyright (c) 2014
 * RESOLVE Software Research Group
 * School of Computing
 * Clemson University
 * All rights reserved.
 * ---------------------------------
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */
package edu.clemson.cs.r2jt.absyn;

import edu.clemson.cs.r2jt.collections.Iterator;
import edu.clemson.cs.r2jt.collections.List;
import edu.clemson.cs.r2jt.data.PosSymbol;
import edu.clemson.cs.r2jt.data.Symbol;

public class PerformanceCModuleDec extends ModuleDec {

    // ===========================================================
    // Variables
    // ===========================================================

    /** The ProfileNames member. */
    private PosSymbol name;

    /** The ProfileNames member. */
    private PosSymbol profileName1;

    /** The Profile's concept name member. */
    private PosSymbol profilecName;

    /** The parameters member. */
    private List<ModuleParameterDec> parameters;

    /** The usesItems member. */
    private List<UsesItem> usesItems;

    /** The requires member. */
    private Exp requires;

    /** The constraints member. */
    private List<Exp> constraints;

    /** The performance initialization member. */
    private PerformanceInitItem perfInit;

    /** The performance finalization member. */
    private PerformanceFinalItem perfFinal;

    /** The facilityInit member. */
    private InitItem facilityInit;

    /** The facilityFinal member. */
    private FinalItem facilityFinal;

    /** The decs member. */
    private List<Dec> decs;

    // ===========================================================
    // Constructors
    // ===========================================================

    public PerformanceCModuleDec() {};

    public PerformanceCModuleDec(PosSymbol name, PosSymbol profileName1,
            PosSymbol profilecName, List<ModuleParameterDec> parameters,
            List<UsesItem> usesItems, Exp requires, List<Exp> constraints,
            PerformanceInitItem perfInit, PerformanceFinalItem perfFinal,
            InitItem facilityInit, FinalItem facilityFinal, List<Dec> decs) {
        this.name = name;
        this.profileName1 = profileName1;
        this.profilecName = profilecName;
        this.parameters = parameters;
        this.usesItems = usesItems;
        this.requires = requires;
        this.constraints = constraints;
        this.perfInit = perfInit;
        this.perfFinal = perfFinal;
        this.facilityInit = facilityInit;
        this.facilityFinal = facilityFinal;
        this.decs = decs;
    }

    // ===========================================================
    // Accessor Methods
    // ===========================================================

    // -----------------------------------------------------------
    // Get Methods
    // -----------------------------------------------------------

    /** Returns the value of the profileNames variable. */
    public PosSymbol getName() {
        return name;
    }

    /** Returns the value of the profileNames variable. */
    public PosSymbol getProfileName1() {
        return profileName1;
    }

    /** Returns the value of the Profile's concept name variable. */
    public PosSymbol getProfilecName() {
        return profilecName;
    }

    /** Returns the value of the parameters variable. */
    public List<ModuleParameterDec> getParameters() {
        return parameters;
    }

    /** Returns the value of the usesItems variable. */
    public List<UsesItem> getUsesItems() {
        return usesItems;
    }

    /** Returns the value of the requires variable. */
    public Exp getRequires() {
        return requires;
    }

    /** Returns the value of the constraints variable. */
    public List<Exp> getConstraints() {
        return constraints;
    }

    /** Returns the value of the performance initialization variable. */
    public PerformanceInitItem getPerfInit() {
        return perfInit;
    }

    /** Returns the value of the performance finalization variable. */
    public PerformanceFinalItem getPerfFinal() {
        return perfFinal;
    }

    /** Returns the value of the facilityInit variable. */
    public InitItem getFacilityInit() {
        return facilityInit;
    }

    /** Returns the value of the facilityFinal variable. */
    public FinalItem getFacilityFinal() {
        return facilityFinal;
    }

    /** Returns the value of the decs variable. */
    public List<Dec> getDecs() {
        return decs;
    }

    /** Returns a list of procedures in this realization. */
    public List<Symbol> getLocalProcedureNames() {
        List<Symbol> retval = new List<Symbol>();
        Iterator<Dec> it = decs.iterator();
        while (it.hasNext()) {
            Dec d = it.next();
            if (d instanceof ProcedureDec) {
                retval.add(d.getName().getSymbol());
            }
        }
        return retval;
    }

    // -----------------------------------------------------------
    // Set Methods
    // -----------------------------------------------------------

    /** Sets the profileName1 variable to the specified value. */
    public void setName(PosSymbol name) {
        this.name = name;
    }

    /** Sets the profileName1 variable to the specified value. */
    public void setProfileNames1(PosSymbol profileName1) {
        this.profileName1 = profileName1;
    }

    /** Sets the Profile's concept name variable to the specified value. */
    public void setProfilecName(PosSymbol profilecName) {
        this.profilecName = profilecName;
    }

    /** Sets the parameters variable to the specified value. */
    public void setParameters(List<ModuleParameterDec> parameters) {
        this.parameters = parameters;
    }

    /** Sets the usesItems variable to the specified value. */
    public void setUsesItems(List<UsesItem> usesItems) {
        this.usesItems = usesItems;
    }

    /** Sets the requires variable to the specified value. */
    public void setRequires(Exp requires) {
        this.requires = requires;
    }

    /** Sets the constraints variable to the specified value. */
    public void setConstraints(List<Exp> constraints) {
        this.constraints = constraints;
    }

    /** Sets the performance initialization variable to the specified value. */
    public void setPerfInit(PerformanceInitItem perfInit) {
        this.perfInit = perfInit;
    }

    /** Sets the performance finalization variable to the specified value. */
    public void setPerfFinal(PerformanceFinalItem perfFinal) {
        this.perfFinal = perfFinal;
    }

    /** Sets the facilityInit variable to the specified value. */
    public void setFacilityInit(InitItem facilityInit) {
        this.facilityInit = facilityInit;
    }

    /** Sets the facilityFinal variable to the specified value. */
    public void setFacilityFinal(FinalItem facilityFinal) {
        this.facilityFinal = facilityFinal;
    }

    /** Sets the decs variable to the specified value. */
    public void setDecs(List<Dec> decs) {
        this.decs = decs;
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    /** Accepts a ResolveConceptualVisitor. */
    public void accept(ResolveConceptualVisitor v) {
        v.visitPerformanceCModuleDec(this);
    }

    /** Returns a formatted text string of this class. */
    public String asString(int indent, int increment) {

        StringBuffer sb = new StringBuffer();

        printSpace(indent, sb);
        sb.append("PerformanceCModuleDec\n");

        if (name != null) {
            sb.append(name.asString(indent + increment, increment));
        }

        if (profileName1 != null) {
            sb.append(profileName1.asString(indent + increment, increment));
        }

        if (profilecName != null) {
            sb.append(profilecName.asString(indent + increment, increment));
        }

        if (parameters != null) {
            sb.append(parameters.asString(indent + increment, increment));
        }

        if (usesItems != null) {
            sb.append(usesItems.asString(indent + increment, increment));
        }

        if (requires != null) {
            sb.append(requires.asString(indent + increment, increment));
        }

        if (constraints != null) {
            sb.append(constraints.asString(indent + increment, increment));
        }

        if (perfInit != null) {
            sb.append(perfInit.asString(indent + increment, increment));
        }

        if (perfFinal != null) {
            sb.append(perfFinal.asString(indent + increment, increment));
        }

        if (facilityInit != null) {
            sb.append(facilityInit.asString(indent + increment, increment));
        }

        if (facilityFinal != null) {
            sb.append(facilityFinal.asString(indent + increment, increment));
        }

        if (decs != null) {
            sb.append(decs.asString(indent + increment, increment));
        }

        return sb.toString();
    }
}
