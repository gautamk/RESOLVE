/**
 * SymbolTableException.java
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
package edu.clemson.cs.r2jt.typeandpopulate;

@SuppressWarnings("serial")
public class SymbolTableException extends Exception {

    public SymbolTableException() {
        super();
    }

    public SymbolTableException(String msg) {
        super(msg);
    }

    public SymbolTableException(Exception e) {
        super(e);
    }
}
