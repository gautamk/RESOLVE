/**
 * PerformanceOperationDec.java
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

import edu.clemson.cs.r2jt.collections.List;
import edu.clemson.cs.r2jt.collections.Iterator;
import edu.clemson.cs.r2jt.data.Location;
import edu.clemson.cs.r2jt.data.Mode;
import edu.clemson.cs.r2jt.data.PosSymbol;

public class PerformanceOperationDec extends Dec implements ModuleParameter {

    // ===========================================================
    // Variables
    // ===========================================================

    /** The name member. */
    private PosSymbol name;

    /** The parameters member. */
    private List<ParameterVarDec> parameters;

    /** The returnTy member. */
    private Ty returnTy;

    /** The stateVars member. */
    private List<AffectsItem> stateVars;

    /** The requires member. */
    private Exp requires;

    /** The ensures member. */
    private Exp ensures;

    /** The duration member. */
    private Exp duration;

    /** The mainp_disp member. */
    private Exp mainp_disp;

    // ===========================================================
    // Constructors
    // ===========================================================

    public PerformanceOperationDec() {};

    public PerformanceOperationDec(PosSymbol name,
            List<ParameterVarDec> parameters, Ty returnTy,
            List<AffectsItem> stateVars, Exp requires, Exp ensures,
            Exp duration, Exp mainp_disp) {
        this.name = name;
        this.parameters = parameters;
        this.returnTy = returnTy;
        this.stateVars = stateVars;
        this.requires = requires;
        this.ensures = ensures;
        this.duration = duration;
        this.mainp_disp = mainp_disp;
    }

    // ===========================================================
    // Accessor Methods
    // ===========================================================

    // -----------------------------------------------------------
    // Get Methods
    // -----------------------------------------------------------

    /** Returns the value of the name variable. */
    public PosSymbol getName() {
        return name;
    }

    /** Returns the value of the parameters variable. */
    public List<ParameterVarDec> getParameters() {
        return parameters;
    }

    /** Returns the value of the returnTy variable. */
    public Ty getReturnTy() {
        return returnTy;
    }

    /** Returns the value of the stateVars variable. */
    public List<AffectsItem> getStateVars() {
        return stateVars;
    }

    /** Returns the value of the requires variable. */
    public Exp getRequires() {
        return requires;
    }

    /** Returns the value of the ensures variable. */
    public Exp getEnsures() {
        return ensures;
    }

    /** Returns the value of the duration variable. */
    public Exp getDuration() {
        return duration;
    }

    /** Returns the value of the ensures variable. */
    public Exp getMainp_disp() {
        return mainp_disp;
    }

    // -----------------------------------------------------------
    // Set Methods
    // -----------------------------------------------------------

    /** Sets the name variable to the specified value. */
    public void setName(PosSymbol name) {
        this.name = name;
    }

    /** Sets the parameters variable to the specified value. */
    public void setParameters(List<ParameterVarDec> parameters) {
        this.parameters = parameters;
    }

    /** Sets the returnTy variable to the specified value. */
    public void setReturnTy(Ty returnTy) {
        this.returnTy = returnTy;
    }

    /** Sets the stateVars variable to the specified value. */
    public void setStateVars(List<AffectsItem> stateVars) {
        this.stateVars = stateVars;
    }

    /** Sets the requires variable to the specified value. */
    public void setRequires(Exp requires) {
        this.requires = requires;
    }

    /** Sets the ensures variable to the specified value. */
    public void setEnsures(Exp ensures) {
        this.ensures = ensures;
    }

    /** Sets the duration variable to the specified value. */
    public void setDuration(Exp duration) {
        this.duration = duration;
    }

    /** Sets the mainp_disp variable to the specified value. */
    public void setMainp_disp(Exp mainp_disp) {
        this.mainp_disp = mainp_disp;
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    /** Accepts a ResolveConceptualVisitor. */
    public void accept(ResolveConceptualVisitor v) {
        v.visitPerformanceOperationDec(this);
    }

    /** Returns a formatted text string of this class. */
    public String asString(int indent, int increment) {

        StringBuffer sb = new StringBuffer();

        printSpace(indent, sb);
        sb.append("OperationDec\n");

        if (name != null) {
            sb.append(name.asString(indent + increment, increment));
        }

        if (parameters != null) {
            sb.append(parameters.asString(indent + increment, increment));
        }

        if (returnTy != null) {
            sb.append(returnTy.asString(indent + increment, increment));
        }

        if (stateVars != null) {
            sb.append(stateVars.asString(indent + increment, increment));
        }

        if (requires != null) {
            sb.append(requires.asString(indent + increment, increment));
        }

        if (ensures != null) {
            sb.append(ensures.asString(indent + increment, increment));
        }

        if (duration != null) {
            sb.append(duration.asString(indent + increment, increment));
        }

        if (mainp_disp != null) {
            sb.append(mainp_disp.asString(indent + increment, increment));
        }

        return sb.toString();
    }

    /** The parameter modes 'alters', 'evaluates', 'replaces', and 'restores'
        place certain restrictions on what state of each argument may be
        used in the requires and ensures clauses. If an invalid variable
        is used there, an error string is returned. **/
    public String checkRequiresEnsures() {
        Iterator<ParameterVarDec> i = parameters.iterator();
        final String ALTERS = "alters";
        final String EVALUATES = "evalutates";
        final String REPLACES = "replaces";
        final String RESTORES = "restores";
        final String PRESERVES = "preserves";
        String msg = null;
        while (msg == null && i.hasNext()) {
            ParameterVarDec param = i.next();
            String varName = (param.getName()).getName();
            if (ALTERS.equals((param.getMode()).getModeName())) {
                /* "alters" - The ensures clause cannot contain the changed value
                              of the variable. */
                if (ensures.containsVar(varName, false)) {
                    msg =
                            "Because of parameter mode 'alters', ensures clause of Operation "
                                    + name.getName() + " cannot contain "
                                    + varName;
                }
            }
            else if (EVALUATES.equals((param.getMode()).getModeName())) {
                /* "evaluates" - The ensures clause cannot contain the initial
                                 value of the variable. */
                if (ensures != null && ensures.containsVar(varName, true)) {
                    msg =
                            "Because of parameter mode 'evaluates', ensures clause of Operation "
                                    + name.getName() + " cannot contain #"
                                    + varName;
                }
            }
            else if (REPLACES.equals((param.getMode()).getModeName())) {
                /* "replaces" - The requires clause cannot contain the variable and
                                the ensures clause cannot contain the initial value
                                of the variable. */
                if (requires != null && requires.containsVar(varName, false)) {
                    msg =
                            "Because of parameter mode 'replaces', requires clause of Operation "
                                    + name.getName() + " cannot contain "
                                    + varName;
                }
                if (ensures != null && ensures.containsVar(varName, true)) {
                    msg =
                            "Because of parameter mode 'replaces', ensures clause of Operation "
                                    + name.getName() + " cannot contain #"
                                    + varName;
                }
            }
            else if (RESTORES.equals((param.getMode()).getModeName())
                    || PRESERVES.equals((param.getMode()).getModeName())) {
                /* "restores"/"preserves" - The ensures clause cannot contain the
                                            initial value of the variable. */
                if (ensures != null && ensures.containsVar(varName, true)) {
                    if (RESTORES.equals((param.getMode()).getModeName())) {
                        msg =
                                "Because of parameter mode 'restores', ensures clause of Operation "
                                        + name.getName() + " cannot contain #"
                                        + varName;
                    }
                    else {
                        msg =
                                "Because of parameter mode 'preserves', ensures clause of Operation "
                                        + name.getName() + " cannot contain #"
                                        + varName;
                    }
                }
            }
        }
        return msg;
    }
}
