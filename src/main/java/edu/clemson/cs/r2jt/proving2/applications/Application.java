/**
 * Application.java
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
package edu.clemson.cs.r2jt.proving2.applications;

import edu.clemson.cs.r2jt.proving2.model.Conjunct;
import edu.clemson.cs.r2jt.proving2.model.PerVCProverModel;
import edu.clemson.cs.r2jt.proving2.model.Site;
import java.util.Set;

/**
 *
 * @author hamptos
 */
public interface Application {

    public void apply(PerVCProverModel m);

    public Set<Site> involvedSubExpressions();

    public String description();

    public Set<Conjunct> getPrerequisiteConjuncts();

    public Set<Conjunct> getAffectedConjuncts();

    public Set<Site> getAffectedSites();
}
