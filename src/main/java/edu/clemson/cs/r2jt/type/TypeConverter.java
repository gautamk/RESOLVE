/**
 * TypeConverter.java
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
package edu.clemson.cs.r2jt.type;

import edu.clemson.cs.r2jt.absyn.ArbitraryExpTy;
import edu.clemson.cs.r2jt.absyn.ArrayTy;
import edu.clemson.cs.r2jt.absyn.CartProdTy;
import edu.clemson.cs.r2jt.absyn.ConstructedTy;
import edu.clemson.cs.r2jt.absyn.DefinitionDec;
import edu.clemson.cs.r2jt.absyn.FunctionTy;
import edu.clemson.cs.r2jt.absyn.MathVarDec;
import edu.clemson.cs.r2jt.absyn.NameTy;
import edu.clemson.cs.r2jt.absyn.BooleanTy;
import edu.clemson.cs.r2jt.absyn.RecordTy;
import edu.clemson.cs.r2jt.absyn.TupleTy;
import edu.clemson.cs.r2jt.absyn.Ty;
import edu.clemson.cs.r2jt.absyn.VarDec;
import edu.clemson.cs.r2jt.collections.Iterator;
import edu.clemson.cs.r2jt.collections.List;
import edu.clemson.cs.r2jt.data.Location;
import edu.clemson.cs.r2jt.data.PosSymbol;
import edu.clemson.cs.r2jt.data.Symbol;
import edu.clemson.cs.r2jt.entry.TypeEntry;
import edu.clemson.cs.r2jt.typeandpopulate.MTType;
import edu.clemson.cs.r2jt.scope.Binding;
import edu.clemson.cs.r2jt.scope.OldSymbolTable;
import edu.clemson.cs.r2jt.scope.TypeID;

public class TypeConverter {

    // ===========================================================
    // Constructors
    // ===========================================================

    OldSymbolTable table = null;

    // ===========================================================
    // Constructors
    // ===========================================================

    public TypeConverter(OldSymbolTable table) {
        this.table = table;
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    public Type getProgramType(Ty ty) {
        if (ty == null) {
            return new VoidType();
        }
        else if (ty instanceof ArrayTy) {
            return getArrayType((ArrayTy) ty);
        }
        else if (ty instanceof NameTy) {
            return getProgramIndirectType((NameTy) ty);
        }
        else if (ty instanceof RecordTy) {
            return getRecordType((RecordTy) ty);
        }
        else {
            assert false : "ty is invalid";
            return null;
        }
    }

    public Type getMathType(Ty ty) {
        Type t = null;
        if (ty instanceof CartProdTy) {
            t = getTupleType((CartProdTy) ty);
        }
        else if (ty instanceof ConstructedTy) {
            t = getConstructedType((ConstructedTy) ty);
        }
        else if (ty instanceof FunctionTy) {
            t = getFunctionType((FunctionTy) ty);
        }
        else if (ty instanceof NameTy) {
            t = getMathIndirectType((NameTy) ty);
        }
        else if (ty instanceof BooleanTy) {
            t = getBooleanType((BooleanTy) ty);
        }
        else if (ty instanceof TupleTy) {
            t = getTupleType((TupleTy) ty);
        }
        else if (ty instanceof ArbitraryExpTy) {
            MTType newType = ty.getMathType();

            if (newType == null) {
                throw new RuntimeException("" + ty + " does not have an "
                        + "embedded mathematical type.");
            }

            t = new NewMathType(newType);
        }
        else {
            throw new RuntimeException("Invalid type: " + ty + "("
                    + ty.getClass() + ")");
        }
        //System.out.println("New type introduced: " + t.asString());
        //System.out.flush();
        return t;
    }

    public Type getArrayType(ArrayTy ty) {
        PosSymbol intqual =
                new PosSymbol(ty.getLocation(), Symbol
                        .symbol("Std_Integer_Fac"));
        PosSymbol intname =
                new PosSymbol(ty.getLocation(), Symbol.symbol("Integer"));
        NameTy index = new NameTy(intqual, intname);
        return new ArrayType(table.getModuleID(), createArrayName(ty
                .getLocation()), ty.getLo(), ty.getHi(), getProgramType(index),
                getProgramType(ty.getEntryType()));
    }

    public Type getProgramIndirectType(NameTy ty) {
        Binding binding = table.getCurrentBinding();
        binding.addProgramIndirectName(ty.getQualifier(), ty.getName());
        return new IndirectType(ty.getQualifier(), ty.getName(), binding);
    }

    public Type getRecordType(RecordTy ty) {
        List<FieldItem> fields = new List<FieldItem>();
        Iterator<VarDec> i = ty.getFields().iterator();
        while (i.hasNext()) {
            VarDec var = i.next();
            FieldItem item =
                    new FieldItem(var.getName(), getProgramType(var.getTy()));
            fields.add(item);
        }
        return new RecordType(table.getModuleID(), createRecordName(ty
                .getLocation()), fields);
    }

    public Type getTupleType(CartProdTy ty) {
        List<FieldItem> fields = new List<FieldItem>();
        Iterator<MathVarDec> i = ty.getFields().iterator();
        while (i.hasNext()) {
            MathVarDec var = i.next();
            FieldItem item =
                    new FieldItem(var.getName(), getMathType(var.getTy()));
            fields.add(item);
        }
        return new TupleType(fields);
    }

    public Type getConstructedType(ConstructedTy ty) {
        // For now, just convert powersets to sets of the same type
        if (ty.getName().getName().equalsIgnoreCase("powerset")) {
            ty.setName(new PosSymbol(ty.getName().getLocation(), Symbol
                    .symbol("Set")));
        }
        Binding binding = table.getCurrentBinding();
        binding.addConstructedName(ty.getQualifier(), ty.getName(), ty
                .getArgs() != null ? ty.getArgs().size() : 0);
        List<Type> args = new List<Type>();
        if (ty.getArgs() != null) {
            Iterator<Ty> i = ty.getArgs().iterator();
            while (i.hasNext()) {
                Ty ty2 = i.next();
                Type type = getMathType(ty2);
                args.add(type);
            }
        }
        return new ConstructedType(ty.getQualifier(), ty.getName(), args, table
                .getCurrentBinding());
    }

    public Type getFunctionType(FunctionTy ty) {
        return new FunctionType(getMathType(ty.getDomain()), getMathType(ty
                .getRange()));
    }

    public Type getMathIndirectType(NameTy ty) {
        Binding binding = table.getCurrentBinding();
        binding.addMathIndirectName(ty.getQualifier(), ty.getName());
        return new IndirectType(ty.getQualifier(), ty.getName(), binding);
    }

    public Type getBooleanType(BooleanTy ty) {
        Binding binding = table.getCurrentBinding();
        BooleanType bt = BooleanType.INSTANCE;
        //binding.addTypeMapping(new TypeEntry(ty.getName(), bt));
        return bt;

    }

    public Type getTupleType(TupleTy ty) {
        List<FieldItem> fields = new List<FieldItem>();
        Iterator<Ty> i = ty.getFields().iterator();
        while (i.hasNext()) {
            Ty ty2 = i.next();
            PosSymbol name = new PosSymbol(ty.getLocation(), Symbol.symbol(""));
            FieldItem item = new FieldItem(name, getMathType(ty2));
            fields.add(item);
        }
        return new TupleType(fields);
    }

    public Type getProgramType(Ty ty, PosSymbol name) {
        Type type = getProgramType(ty);
        if (type instanceof ArrayType) {
            ((ArrayType) type).setName(name);
        }
        if (type instanceof RecordType) {
            ((RecordType) type).setName(name);
        }
        //    if (type instanceof IndirectType) {
        //        type = new NameType(table.getModuleID(), name, type);
        //    }
        return type;
    }

    public Type getConceptualType(Ty ty, PosSymbol name) {
        return new ConcType(table.getModuleID(), name, getMathType(ty));
    }

    // -----------------------------------------------------------
    // Type Translation for Definitions
    // -----------------------------------------------------------

    public Type getFunctionType(DefinitionDec dec) {
        assert dec.getParameters().size() > 0 : "function takes not parameters";
        return new FunctionType(getTupleType(dec.getParameters()),
                getMathType(dec.getReturnTy()));
    }

    public Type getTupleType(List<MathVarDec> decs) {
        List<FieldItem> fields = new List<FieldItem>();
        Iterator<MathVarDec> i = decs.iterator();
        while (i.hasNext()) {
            MathVarDec dec = i.next();
            FieldItem item =
                    new FieldItem(dec.getName(), getMathType(dec.getTy()));
            fields.add(item);
        }
        return new TupleType(fields);
    }

    public PosSymbol createArrayName(Location loc) {
        Symbol sym =
                Symbol.symbol("%Array(" + loc.getPos().getLine() + ","
                        + loc.getPos().getColumn() + ")");
        return new PosSymbol(loc, sym);
    }

    public PosSymbol createRecordName(Location loc) {
        Symbol sym =
                Symbol.symbol("%Record(" + loc.getPos().getLine() + ","
                        + loc.getPos().getColumn() + ")");
        return new PosSymbol(loc, sym);
    }

    public TypeID buildTypeID(Type t) {
        PosSymbol qual = null;
        PosSymbol name = null;
        int params = 0;
        if (t instanceof ArrayType) {
            name = ((ArrayType) t).getName();
            params = 1;
        }
        else if (t instanceof ConcType) {
            name = ((ConcType) t).getName();
        }
        else if (t instanceof ConstructedType) {
            qual = ((ConstructedType) t).getQualifier();
            name = ((ConstructedType) t).getName();
            params = ((ConstructedType) t).getArgs().size();
        }
        else if (t instanceof FieldItem) {
            name = ((FieldItem) t).getName();
        }
        else if (t instanceof FormalType) {
            name = ((FormalType) t).getName();
        }
        else if (t instanceof FunctionType) {
            return null;
        }
        else if (t instanceof IndirectType) {
            qual = ((IndirectType) t).getQualifier();
            name = ((IndirectType) t).getName();
        }
        else if (t instanceof MathFormalType) {
            name = ((MathFormalType) t).getName();
        }
        else if (t instanceof NameType) {
            name = ((NameType) t).getName();
        }
        else if (t instanceof PrimitiveType) {
            name = ((PrimitiveType) t).getName();
            params = ((PrimitiveType) t).paramCount();
        }
        else if (t instanceof RecordType) {
            name = ((RecordType) t).getName();
            //params = ((RecordType)t).getFields().size();
        }
        else if (t instanceof TupleType) {
            return null;
        }
        else {
            return null;
        }
        return new TypeID(qual, name, params);
    }

}
