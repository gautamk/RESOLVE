/**
 * SymbolSearchException.java
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
package edu.clemson.cs.r2jt.location;

public class SymbolSearchException extends Exception {

    // ==========================================================
    // Constructors
    // ==========================================================

    public SymbolSearchException() {
        ;
    }

    public SymbolSearchException(String msg) {
        super(msg);
    }
}
