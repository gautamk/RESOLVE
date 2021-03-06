/**
 * TypeType.java
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

import edu.clemson.cs.r2jt.data.Location;
import edu.clemson.cs.r2jt.scope.Binding;
import edu.clemson.cs.r2jt.scope.ScopeID;

public class TypeType extends Type {

    public static final TypeType INSTANCE = new TypeType();

    @Override
    public Type instantiate(ScopeID sid, Binding binding) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TypeName getProgramName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getRelativeName(Location loc) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Type toMath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String asString() {
        // TODO Auto-generated method stub
        return null;
    }

}
