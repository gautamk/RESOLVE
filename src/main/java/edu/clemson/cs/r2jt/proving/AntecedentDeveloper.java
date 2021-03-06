/**
 * AntecedentDeveloper.java
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
package edu.clemson.cs.r2jt.proving;

import java.util.Iterator;

/**
 * <p>An <code>AntecedentDeveloper</code> provides a mechanism for iterating
 * over those conjuncts that should be added to a given <code>Antecedent</code>
 * according to some pre-defined rule.  Note that this class differs from
 * <code>AntecedentTransformer</code> in that returned <code>Antecedent</code>s
 * contain <em>only</em> those conjuncts with which the given
 * <code>Antecedent</code> should be extended, and none of the original 
 * conjuncts.</p>
 */
public interface AntecedentDeveloper
        extends
            Transformer<Antecedent, Iterator<Antecedent>> {

}
