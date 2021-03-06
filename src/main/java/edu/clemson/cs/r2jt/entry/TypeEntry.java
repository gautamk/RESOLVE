/**
 * TypeEntry.java
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
package edu.clemson.cs.r2jt.entry;

import edu.clemson.cs.r2jt.absyn.Exp;
import edu.clemson.cs.r2jt.absyn.MathVarDec;
import edu.clemson.cs.r2jt.data.Location;
import edu.clemson.cs.r2jt.data.PosSymbol;
import edu.clemson.cs.r2jt.data.Symbol;
import edu.clemson.cs.r2jt.scope.Binding;
import edu.clemson.cs.r2jt.scope.Scope;
import edu.clemson.cs.r2jt.scope.ScopeID;
import edu.clemson.cs.r2jt.type.Type;

public class TypeEntry extends Entry {

    // ===========================================================
    // Variables
    // ===========================================================

    private Scope scope = null;

    private PosSymbol name = null;

    private Type type = null;

    private PosSymbol exemplar = null;

    private MathVarDec local = null;

    private Exp where = null;

    private Exp obligation = null;

    // ===========================================================
    // Constructors
    // ===========================================================

    public TypeEntry(Scope scope, PosSymbol name, Type type) {
        this.scope = scope;
        this.name = name;
        this.type = type;
    }

    public TypeEntry(Scope scope, PosSymbol name, Type type, MathVarDec local,
            Exp where, Exp obligation) {
        this.scope = scope;
        this.name = name;
        this.type = type;
        this.local = local;
        this.where = where;
        this.obligation = obligation;
    }

    public TypeEntry(PosSymbol name, Type type) {
        this.name = name;
        this.type = type;
    }

    public TypeEntry(Scope scope, PosSymbol name, Type type, PosSymbol exemplar) {
        this.scope = scope;
        this.name = name;
        this.type = type;
        this.exemplar = exemplar;
    }

    /*
     public TypeEntry(PosSymbol name, Type type, PosSymbol exemplar) {
     this.name = name;
     this.type = type;
     this.exemplar = exemplar;
     }
     */

    // ===========================================================
    // Accessors
    // ===========================================================

    public Scope getScope() {
        return scope;
    }

    public Location getLocation() {
        return name.getLocation();
    }

    public Symbol getSymbol() {
        return name.getSymbol();
    }

    public PosSymbol getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean isConcType() {
        return (exemplar != null);
    }

    public PosSymbol getExemplar() {
        return exemplar;
    }

    public MathVarDec getLocal() {
        return local;
    }

    public boolean hasObligation() {
        return (obligation != null);
    }

    public Exp getObligation() {
        return obligation;
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    public TypeEntry instantiate(ScopeID sid, Binding binding) {
        return new TypeEntry(binding.getScope(), name, type.instantiate(sid,
                binding));
    }

    public String toString() {
        return "E(" + type.toString() + ")";
    }
}
