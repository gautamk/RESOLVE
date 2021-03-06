/**
 * ProofChecker.java
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
package edu.clemson.cs.r2jt.proofchecking;

import edu.clemson.cs.r2jt.absyn.*;
import edu.clemson.cs.r2jt.analysis.Analyzer;
import edu.clemson.cs.r2jt.analysis.TypeResolutionException;
import edu.clemson.cs.r2jt.collections.Iterator;
import edu.clemson.cs.r2jt.collections.List;
import edu.clemson.cs.r2jt.collections.Stack;
import edu.clemson.cs.r2jt.data.Location;
import edu.clemson.cs.r2jt.data.Symbol;
import edu.clemson.cs.r2jt.data.PosSymbol;
import edu.clemson.cs.r2jt.entry.DefinitionEntry;
import edu.clemson.cs.r2jt.entry.Entry;
import edu.clemson.cs.r2jt.entry.TheoremEntry;
import edu.clemson.cs.r2jt.entry.TypeEntry;
import edu.clemson.cs.r2jt.errors.ErrorHandler;
import edu.clemson.cs.r2jt.init.CompileEnvironment;
import edu.clemson.cs.r2jt.location.DefinitionLocator;
import edu.clemson.cs.r2jt.location.SymbolSearchException;
import edu.clemson.cs.r2jt.location.TheoremLocator;
import edu.clemson.cs.r2jt.location.TypeLocator;
import edu.clemson.cs.r2jt.scope.*;
import edu.clemson.cs.r2jt.type.*;
import edu.clemson.cs.r2jt.utilities.Flag;
import edu.clemson.cs.r2jt.utilities.FlagDependencies;

public class ProofChecker {

    /**
     * <p>Causes proof units to be mechanically checked.</p>
     */
    public static final Flag FLAG_PROOFCHECK =
            new Flag("Proving", "proofcheck",
                    "Check consistency of user-supplied proofs.");

    private static final boolean debugOn = false;

    // ==========================================================
    // Variables
    // ==========================================================

    private ErrorHandler err;

    //private Environment env = Environment.getInstance();
    CompileEnvironment myInstanceEnvironment;

    private OldSymbolTable table;

    private TypeMatcher tm;

    private Stack<Exp> previousLines;

    private IdPredicate idPredicate;

    // ==========================================================
    // Constructors
    // ==========================================================

    public ProofChecker(OldSymbolTable table, TypeMatcher tm,
            CompileEnvironment instanceEnvironment) {
        myInstanceEnvironment = instanceEnvironment;
        this.table = table;
        this.tm = tm;
        previousLines = new Stack<Exp>();

        idPredicate = new IdPredicate();
        this.err = myInstanceEnvironment.getErrorHandler();
    }

    public void proofcheck(Exp value) {
        if (value instanceof SuppositionDeductionExp) {
            proofcheckSupDeduc((SuppositionDeductionExp) value);
        }
        else {
            //			Exp x = null;
            //			if (!(lines.isEmpty())) {
            //				x = lines.pop();
            //				lines.push(x);
            //			}
            if (value instanceof JustifiedExp) {
                JustifiedExp j = (JustifiedExp) value;
                redirectToRule(j);
            }
            else {
                previousLines.push(applyQuantifiedExp(value));
            }
        }

        Stack<Exp> backup = new Stack<Exp>();
        while (!previousLines.isEmpty()) {
            backup.push(previousLines.pop());
        }

        while (!backup.isEmpty()) {
            previousLines.push(backup.pop());
        }

        return;
    }

    /*
     * Ok, it seems that all justifications percolate down through this 
     * method, which routes to a specific checking method based on the kind
     * of justification.  -HwS
     */
    private void redirectToRule(JustifiedExp exp) {
        PosSymbol rule = exp.getJustification().getRule();
        boolean isDef = exp.getJustification().getIsDef();

        if (isDef) {
            /*
             * it never gets here because a justification of this kind will be
             * parsed as a justification by equality!
             */
            if (rule.equals("ThereExistsUnique")) {
                checkHypDesigAndDefThereExistsUnique(exp);
                return;
            }

            checkHypDesigAndDef(exp);
            return;
        }
        if (rule == null || rule.equals("Equality")) {
            checkHypDesigsAndEquality(exp);
            return;
        }
        if (rule.equals("ModusPonens")) {
            checkHypDesigsAndModusPonens(exp);
            return;
        }
        if (rule.equals("AndRule")) {
            checkHypDesigsAndAndRule(exp);
            return;
        }
        if (rule.equals("Contradiction")) {
            checkHypDesigsAndContradiction(exp);
            return;
        }
        if (rule.equals("ReductioAdAbsurdum")) {
            checkHypDesigAndReductioAdAbsurdum(exp);
            return;
        }
        if (rule.equals("UniversalGeneralization")) {
            checkHypDesigAndUniversalGeneralization(exp);
            return;
        }
        if (rule.equals("ExistentialGeneralization")) {
            checkHypDesigAndExistentialGeneralization(exp);
            return;
        }
        if (rule.equals("OrRule")) {
            checkHypDesigAndOrRule(exp);
            return;
        }
        if (rule.equals("ConjunctElimination")) {
            checkHypDesigAndConjunctElimination(exp);
            return;
        }
        if (rule.equals("ExcludedMiddle")) {
            checkExcludedMiddle(exp);
            return;
        }
        if (rule.equals("AlternativeElimination")) {
            checkHypDesigsAndAlternativeElimination(exp);
            return;
        }
        if (rule.equals("CommonConclusion")) {
            checkHypDesigsAndCommonConclusion(exp);
            return;
        }
        if (rule.equals("QuantifierDistribution")) {
            checkHypDesigAndQuantifierDistribution(exp);
            return;
        }
        if (rule.equals("UniversalInstantiation")) {
            checkHypDesigAndUniversalInstantiation(exp);
            return;
        }
        if (rule.equals("ExistentialInstantiation")) {
            checkHypDesigAndExistentialInstantiation(exp);
            return;
        }
        // System.out.println("error location 1");
        String msg = couldNotApplyRuleMessage(rule.getName());
        err.error(exp.getLocation(), msg);
        return;
    }

    // ==========================================================
    // Modus Ponens and related verification methods
    // ==========================================================

    private void checkHypDesigsAndModusPonens(JustifiedExp exp) {
        // System.out.println("In ProofChecker.checkHypDesigsAndModusPonens()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 2);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp ifPart = exps2.get(0);
        Exp matchTest = exps2.get(1);
        Exp matchThen = exp.getExp();
        boolean checked = false;
        List<VariableExpBinding> vars;
        if (ifPart instanceof IfExp) {
            Exp ifPartTest = ((IfExp) ifPart).getTest();
            Exp ifPartThen = ((IfExp) ifPart).getThenclause();
            vars = new List<VariableExpBinding>();
            if (equals(matchThen, ifPartThen, null, null, false, false, true,
                    vars)
                    && compareVarScopes(vars)) {
                if (equals(matchTest, ifPartTest, null, null, false, false,
                        true, vars)) {
                    if (compareVarScopes(vars)) {
                        checked = true;
                    }
                }
            }
        }
        if (!checked) {
            ifPart = exps2.get(1);
            matchTest = exps2.get(0);
            if (ifPart instanceof IfExp) {
                Exp ifPartTest = ((IfExp) ifPart).getTest();
                Exp ifPartThen = ((IfExp) ifPart).getThenclause();
                vars = new List<VariableExpBinding>();
                if (equals(matchThen, ifPartThen, null, null, false, false,
                        true, vars)
                        && compareVarScopes(vars)) {
                    if (equals(matchTest, ifPartTest, null, null, false, false,
                            true, vars)) {
                        if (compareVarScopes(vars)) {
                            checked = true;
                        }
                    }
                }
            }
        }
        if (!checked) {
            String msg = couldNotApplyRuleMessage("Modus Ponens");
            err.error(exp.getExp().getLocation(), msg);
            return;
        }
        // if(!(equals(((IfExp)ifPart).getThenclause(), exp.getExp(), null,
        // null, false, false, false))) {
        // String msg = "Could not match then clause of modus ponens to current
        // line.";
        // err.error(exp.getLocation(), msg);
        // return;
        // }
    }

    // ==========================================================
    // Alternative Elimination and related verification methods
    // ==========================================================

    private void checkHypDesigsAndAlternativeElimination(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigsAndAlternativeElimination()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 2);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp ref1 = exps2.get(0);
        Exp ref2 = exps2.get(1);
        Exp step = exp.getExp();
        if (isOr(ref1)) {
            Exp LHS = ((InfixExp) ref1).getLeft();
            Exp RHS = ((InfixExp) ref1).getRight();
            if (areLogicalOpposites(LHS, ref2)) {
                if (verifyClause(step, RHS)) {
                    return;
                }
            }
            if (areLogicalOpposites(RHS, ref2)) {
                if (verifyClause(step, LHS)) {
                    return;
                }
            }
        }
        if (isOr(ref2)) {
            Exp LHS = ((InfixExp) ref2).getLeft();
            Exp RHS = ((InfixExp) ref2).getRight();
            if (areLogicalOpposites(LHS, ref1)) {
                if (verifyClause(step, RHS)) {
                    return;
                }
            }
            if (areLogicalOpposites(RHS, ref1)) {
                if (verifyClause(step, LHS)) {
                    return;
                }
            }
        }
        String msg = couldNotApplyRuleMessage("Alternative Elimination");
        err.error(exp.getExp().getLocation(), msg);
        return;
    }

    private boolean verifyClause(Exp e1, Exp e2) {
        return equals(e1, e2, null, null, true, false, false, null);
    }

    // ==========================================================
    // Common Conclusion and related verification methods
    // ==========================================================

    private void checkHypDesigsAndCommonConclusion(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigsAndCommonConclusion()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 2);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp ref1 = exps2.get(0);
        Exp ref2 = exps2.get(1);
        if ((ref1 instanceof IfExp) && (ref2 instanceof IfExp)) {
            Exp test1 = ((IfExp) ref1).getTest();
            Exp test2 = ((IfExp) ref2).getTest();
            Exp then1 = ((IfExp) ref1).getThenclause();
            Exp then2 = ((IfExp) ref2).getThenclause();
            Exp step = exp.getExp();
            if (step instanceof IfExp) {
                Exp stepTest = ((IfExp) step).getTest();
                Exp stepThen = ((IfExp) step).getThenclause();
                List<VariableExpBinding> vars1 = new List<VariableExpBinding>();
                List<VariableExpBinding> vars2 = new List<VariableExpBinding>();
                if ((equals(stepThen, then1, null, null, false, false, false,
                        vars1) && compareVarScopes(vars1))
                        && (equals(stepThen, then2, null, null, false, false,
                                false, vars2) && compareVarScopes(vars2))) {
                    if (isOr(stepTest)) {
                        Exp LHS = ((InfixExp) stepTest).getLeft();
                        Exp RHS = ((InfixExp) stepTest).getRight();
                        if ((equals(LHS, test1, null, null, false, false,
                                false, vars1) && compareVarScopes(vars1))
                                && (equals(RHS, test2, null, null, false,
                                        false, false, vars2) && compareVarScopes(vars2))) {
                            return;
                        }
                        else {
                            if ((equals(LHS, test2, null, null, false, false,
                                    false, vars1) && compareVarScopes(vars1))
                                    && (equals(RHS, test1, null, null, false,
                                            false, false, vars2) && compareVarScopes(vars2))) {
                                return;
                            }
                        }
                    }
                }
            }
        }
        String msg = couldNotApplyRuleMessage("Common Conclusion");
        err.error(exp.getExp().getLocation(), msg);
        return;
    }

    // ==========================================================
    // Quantifier Distribution and related verification methods
    // ==========================================================

    private void checkHypDesigAndQuantifierDistribution(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigAndQuantifierDistribution()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        if (!(equals(exps2.get(0), exp.getExp(), null, null, false, false,
                false, vars) && compareVarScopes(vars))) {
            String msg = couldNotApplyRuleMessage("Quantifier Distribution");
            err.error(exp.getExp().getLocation(), msg);
            return;
        }
    }

    // ==========================================================
    // Def There Exists Unique and related verification methods
    // ==========================================================

    private void checkHypDesigAndDefThereExistsUnique(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigAndDefThereExistsUnique()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        if (matchUniqueFromTwoReferences(exp.getExp(), exps2.get(0))) {
            return;
        }
        if (matchUniqueFromOneReference(exp.getExp(), exps2.get(0))) {
            return;
        }
        String msg =
                couldNotApplyRuleMessage("Definition of There Exists Unique");
        err.error(exp.getLocation(), msg);
    }

    private boolean matchUniqueFromTwoReferences(Exp e, Exp ref) {
        VarExp var = findQuantifiedVariable(e, VarExp.UNIQUE);
        if (var == null) {
            return false;
        }
        if (!isAnd(ref)) {
            return false;
        }
        Exp LHS = ((InfixExp) ref).getLeft();
        Exp RHS = ((InfixExp) ref).getRight();
        if (matchUniqueReferences(e, LHS, RHS, var)) {
            return true;
        }
        if (matchUniqueReferences(e, RHS, LHS, var)) {
            return true;
        }
        return false;
    }

    private boolean matchUniqueFromOneReference(Exp e, Exp ref) {
        if (!isAnd(e)) {
            return false;
        }
        Exp LHS = ((InfixExp) e).getLeft();
        Exp RHS = ((InfixExp) e).getRight();
        if (verifyUniqueReferences(LHS, RHS, ref)) {
            return true;
        }
        if (verifyUniqueReferences(RHS, LHS, ref)) {
            return true;
        }
        return false;
    }

    private VarExp findQuantifiedVariable(Exp e, int quant) {
        if (e instanceof VarExp) {
            if (((VarExp) e).getQuantification() == quant) {
                return (VarExp) e;
            }
            else {
                return null;
            }
        }
        else {
            Iterator<Exp> subs = e.getSubExpressions().iterator();
            VarExp var = null;
            VarExp var2 = null;
            while (subs.hasNext()) {
                var2 = findQuantifiedVariable(subs.next(), quant);
                if (var2 != null) {
                    if (var == null) {
                        var = var2;
                    }
                    else if (!equals(var, var2, null, null, true, false, false,
                            null)) {
                        String msg;
                        if (quant == VarExp.NONE) {
                            msg =
                                    "Can only have one variable quantified NONE in the expression if calling this rule.";
                        }
                        else if (quant == VarExp.FORALL) {
                            msg =
                                    "Can only have one variable quantified FORALL in the expression if calling this rule.";
                        }
                        else if (quant == VarExp.EXISTS) {
                            msg =
                                    "Can only have one variable quantified EXISTS in the expression if calling this rule.";
                        }
                        else {
                            msg =
                                    "Can only have one variable quantified UNIQUE in the expression if calling this rule.";
                        }
                        err.error(e.getLocation(), msg);
                        return null;
                    }
                }
            }
            return var;
        }
    }

    private boolean matchUniqueReferences(Exp step, Exp ref1, Exp ref2,
            VarExp var) {
        if (matchVariables(step, ref1, VarExp.EXISTS, VarExp.UNIQUE) == null) {
            return false;
        }
        else {
            if (!(ref2 instanceof IfExp)) {
                return false;
            }
            IfExp ifE = (IfExp) ref2;
            if (!isAnd(ifE.getTest())) {
                return false;
            }
            Exp LHS = ((InfixExp) (ifE.getTest())).getLeft();
            List<VariableExpBinding> LHSvars =
                    matchVariables(step, LHS, VarExp.UNIQUE, VarExp.FORALL);
            Exp RHS = ((InfixExp) (ifE.getTest())).getRight();
            List<VariableExpBinding> RHSvars =
                    matchVariables(step, RHS, VarExp.UNIQUE, VarExp.FORALL);
            if (LHSvars == null) {
                return false;
            }
            if (RHSvars == null) {
                return false;
            }
            if (!(ifE.getThenclause() instanceof EqualsExp)) {
                return false;
            }
            EqualsExp eqE = (EqualsExp) (ifE.getThenclause());
            if (eqE.getOperator() != EqualsExp.EQUAL) {
                return false;
            }
            // compare y = z
            LHS = eqE.getLeft();
            RHS = eqE.getRight();
            Exp LHSmapping = getMapping(LHSvars, LHS);
            Exp RHSmapping = getMapping(RHSvars, RHS);
            if (LHSmapping == null || RHSmapping == null) {
                return false;
            }
            if (!((equals(LHSmapping, var, null, null, false, false, false,
                    null)) && (equals(RHSmapping, var, null, null, false,
                    false, false, null)))) {
                return false;
            }
            return true;
        }
    }

    private Exp getMapping(List<VariableExpBinding> vars, Exp e) {
        Iterator<VariableExpBinding> varsIt = vars.iterator();
        VariableExpBinding temp = null;
        while (varsIt.hasNext()) {
            temp = varsIt.next();
            if (equals(temp.getVarExp(), e, null, null, false, false, false,
                    null)) {
                return temp.getExp();
            }
        }
        return null;
    }

    private List<VariableExpBinding> matchVariables(Exp step, Exp ref,
            int refQuant, int stepQuant) {
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        if (equals(step, ref, null, null, false, false, false, vars)) {
            if (compareVars(vars, refQuant, stepQuant)) {
                return vars;
            }
        }
        return null;
    }

    private boolean verifyUniqueReferences(Exp e1, Exp e2, Exp ref) {
        VarExp var = findQuantifiedVariable(e1, VarExp.EXISTS);
        if (var == null) {
            return false;
        }
        List<VariableExpBinding> vars =
                matchVariables(e1, ref, VarExp.EXISTS, VarExp.UNIQUE);
        if (vars == null) {
            return false;
        }
        if (!(e2 instanceof IfExp)) {
            return false;
        }
        IfExp ifE = (IfExp) e2;
        if (!isAnd(ifE.getTest())) {
            return false;
        }
        Exp LHS = ((InfixExp) (ifE.getTest())).getLeft();
        List<VariableExpBinding> LHSvars =
                matchVariables(LHS, ref, VarExp.UNIQUE, VarExp.FORALL);
        Exp RHS = ((InfixExp) (ifE.getTest())).getRight();
        List<VariableExpBinding> RHSvars =
                matchVariables(RHS, ref, VarExp.UNIQUE, VarExp.FORALL);
        if (LHSvars == null || RHSvars == null) {
            return false;
        }
        if (!(ifE.getThenclause() instanceof EqualsExp)) {
            return false;
        }
        EqualsExp eqE = (EqualsExp) (ifE.getThenclause());
        if (eqE.getOperator() != EqualsExp.EQUAL) {
            return false;
        }
        LHS = eqE.getLeft();
        RHS = eqE.getRight();
        Exp LHSmapping = getMapping(LHSvars, LHS);
        Exp RHSmapping = getMapping(RHSvars, RHS);
        if (LHSmapping == null || RHSmapping == null) {
            return false;
        }
        if (!((equals(LHSmapping, var, null, null, false, false, false, null)) && (equals(
                RHSmapping, var, null, null, false, false, false, null)))) {
            return false;
        }
        return true;
    }

    private boolean compareVars(List<VariableExpBinding> vars, int refQuant,
            int stepQuant) {
        Iterator<VariableExpBinding> varsIt = vars.iterator();
        VariableExpBinding temp = null;
        while (varsIt.hasNext()) {
            temp = varsIt.next();
            if (!(temp.getExp() instanceof VarExp)) {
                return false;
            }
            if (!((temp.getVarExp().getQuantification() == refQuant))
                    && (((VarExp) (temp.getExp())).getQuantification() == stepQuant)) {
                return false;
            }
        }
        return true;
    }

    // ==========================================================
    // Universal Quantification and related verification methods
    // ==========================================================

    private boolean matchSubExpressionWithUniversalQuantification(Exp exp,
            Exp ref, boolean instantiation) {
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        if (equals(exp, ref, null, null, false, false, true, vars)) {
            if (instantiation) {
                if (compareUIVarScopes(vars)) {
                    return true;
                }
            }
            else {
                if (compareUGVarScopes(vars)) {
                    return true;
                }
            }
        }
        Iterator<Exp> subExpsIt = ref.getSubExpressions().iterator();
        Exp subRef = null;
        while (subExpsIt.hasNext()) {
            subRef = subExpsIt.next();
            if (matchSubExpressionWithUniversalQuantification(exp, subRef,
                    instantiation))
                return true;
        }
        return false;
    }

    private void checkHypDesigAndUniversalGeneralization(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigAndUniversalGeneralization()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp ref = exps2.get(0);
        Exp step = exp.getExp();
        if (!matchSubExpressionWithUniversalQuantification(step, ref, false)) {
            String msg = couldNotApplyRuleMessage("Universal Generalization");
            err.error(exp.getLocation(), msg);
        }
    }

    private boolean compareUGVarScopes(List<VariableExpBinding> vars) {
        Iterator<VariableExpBinding> varsIt = vars.iterator();
        VariableExpBinding current = null;
        boolean applied = false;
        VarExp ref = null;
        VarExp step = null;
        while (varsIt.hasNext()) {
            current = varsIt.next();
            if (current.getExp() instanceof VarExp) {
                ref = current.getVarExp();
                step = (VarExp) (current.getExp());
                if (ref.getQuantification() == 0
                        && step.getQuantification() == VarExp.FORALL) {
                    applied = true;
                }
                else if (ref.getQuantification() != step.getQuantification()) {
                    return false;
                }
            }
        }
        return applied;
    }

    private void checkHypDesigAndUniversalInstantiation(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigAndUniversalInstantiation()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp ref = exps2.get(0);
        Exp step = exp.getExp();
        if (!matchSubExpressionWithUniversalQuantification(step, ref, true)) {
            String msg = couldNotApplyRuleMessage("Universal Instantiation");
            err.error(exp.getLocation(), msg);
        }
    }

    private boolean compareUIVarScopes(List<VariableExpBinding> vars) {
        Iterator<VariableExpBinding> varsIt = vars.iterator();
        VariableExpBinding current = null;
        boolean applied = false;
        VarExp ref = null;
        VarExp step = null;
        while (varsIt.hasNext()) {
            current = varsIt.next();
            if (current.getExp() instanceof VarExp) {
                ref = current.getVarExp();
                step = (VarExp) (current.getExp());
                if (ref.getQuantification() == VarExp.FORALL
                        && step.getQuantification() == 0) {
                    applied = true;
                }
                else if (ref.getQuantification() != 0) {
                    if (step.getQuantification() != ref.getQuantification()) {
                        return false;
                    }
                }
            }
            else if (ref != null && ref.getQuantification() == VarExp.FORALL) {
                applied = true;
            }
        }
        return applied;
    }

    // ==========================================================
    // Existential Quantification and related verification methods
    // ==========================================================

    private boolean matchSubExpressionWithExistentialQuantification(Exp exp,
            Exp ref, boolean instantiation) {
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        if (equals(exp, ref, null, null, false, false, true, vars)) {
            if (instantiation) {
                if (compareEIVarScopes(vars)) {
                    return true;
                }
            }
            else {
                if (compareEGVarScopes(vars)) {
                    return true;
                }
            }
        }
        Iterator<Exp> subExpsIt = ref.getSubExpressions().iterator();
        Exp subRef = null;
        while (subExpsIt.hasNext()) {
            subRef = subExpsIt.next();
            if (matchSubExpressionWithExistentialQuantification(exp, subRef,
                    instantiation))
                return true;
        }
        return false;
    }

    private void checkHypDesigAndExistentialGeneralization(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigAndExistentialGeneralization()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp ref = exps2.get(0);
        Exp step = exp.getExp();
        if (!matchSubExpressionWithExistentialQuantification(step, ref, false)) {
            String msg = couldNotApplyRuleMessage("Existential Generalization");
            err.error(exp.getLocation(), msg);
        }
    }

    private boolean compareEGVarScopes(List<VariableExpBinding> vars) {
        Iterator<VariableExpBinding> varsIt = vars.iterator();
        VariableExpBinding current = null;
        boolean applied = false;
        VarExp ref = null;
        VarExp step = null;
        while (varsIt.hasNext()) {
            current = varsIt.next();
            if (current.getExp() instanceof VarExp) {
                ref = current.getVarExp();
                step = (VarExp) (current.getExp());
                if (ref.getQuantification() == 0
                        && step.getQuantification() == VarExp.EXISTS) {
                    applied = true;
                }
                else if (ref.getQuantification() != step.getQuantification()) {
                    return false;
                }
            }
        }
        return applied;
    }

    private void checkHypDesigAndExistentialInstantiation(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigAndExistentialInstantiation()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp ref = exps2.get(0);
        Exp step = exp.getExp();
        if (!matchSubExpressionWithExistentialQuantification(step, ref, true)) {
            String msg = couldNotApplyRuleMessage("Existential Instantiation");
            err.error(exp.getLocation(), msg);
        }
    }

    private boolean compareEIVarScopes(List<VariableExpBinding> vars) {
        Iterator<VariableExpBinding> varsIt = vars.iterator();
        VariableExpBinding current = null;
        boolean applied = false;
        VarExp ref = null;
        VarExp step = null;
        while (varsIt.hasNext()) {
            current = varsIt.next();
            if (current.getExp() instanceof VarExp) {
                ref = current.getVarExp();
                step = (VarExp) (current.getExp());
                if (ref.getQuantification() == VarExp.EXISTS
                        && step.getQuantification() == 0) {
                    applied = true;
                }
                else if (ref.getQuantification() != 0) {
                    if (step.getQuantification() != ref.getQuantification()) {
                        return false;
                    }
                }
            }
        }
        return applied;
    }

    // ==========================================================
    // And and related verification methods
    // ==========================================================

    private void checkHypDesigsAndAndRule(JustifiedExp exp) {
        // System.out.println("In ProofChecker.checkHypDesigsAndAndRule()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 2);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp currentLine = exp.getExp();
        if (isAnd(currentLine)) {
            List<Exp> LHSRHS = currentLine.getSubExpressions();
            List<VariableExpBinding> vars = new List<VariableExpBinding>();
            boolean matched =
                    (equals(LHSRHS.get(0), exps2.get(0), null, null, false,
                            false, true, vars) && compareVarScopes(vars));
            if (matched) {
                if (!(equals(LHSRHS.get(1), exps2.get(1), null, null, false,
                        false, true, vars) && compareVarScopes(vars))) {
                    System.out.println("Error 1");
                    andRuleError(exp.getLocation());
                }
            }
            else {
                vars = new List<VariableExpBinding>();
                if (!(equals(LHSRHS.get(0), exps2.get(1), null, null, false,
                        false, true, vars) && compareVarScopes(vars))) {
                    System.out.println("Error 2");
                    andRuleError(exp.getLocation());
                }
                vars = new List<VariableExpBinding>();
                if (!(equals(LHSRHS.get(1), exps2.get(0), null, null, false,
                        false, true, vars) && compareVarScopes(vars))) {
                    System.out.println("Error 3");
                    andRuleError(exp.getLocation());
                }
            }
        }
        else {
            System.out.println("Error 4");
            andRuleError(exp.getLocation());
        }
    }

    // ==========================================================
    // Contradiction and related verification methods
    // ==========================================================

    private void checkHypDesigsAndContradiction(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigsAndContradiction()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 2);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        // Check that the justified expression is just "false"
        if (exp.getExp() instanceof VarExp) {
            VarExp body = (VarExp) (exp.getExp());
            if (body.getName().getName().equalsIgnoreCase("false")) {
                // continue
            }
            else {
                String msg =
                        "Proof line must include False when using the contradiction rule.";
                err.error(exp.getLocation(), msg);
            }
        }
        else {
            String msg =
                    "Proof line must include False when using the contradiction rule.";
            err.error(exp.getLocation(), msg);
        }
        // Confirm the contradiction
        Exp e1 = applyQuantifiedExp(exps2.get(0));
        Exp e2 = applyQuantifiedExp(exps2.get(1));
        if (isNot(e1)) {
            List<VariableExpBinding> vars = new List<VariableExpBinding>();
            Exp subE = ((PrefixExp) e1).getArgument();
            if (equals(e2, subE, null, null, true, false, false, vars)
                    && compareVarScopes(vars))
                return;
        }
        if (isNot(e2)) {
            List<VariableExpBinding> vars = new List<VariableExpBinding>();
            Exp subE = ((PrefixExp) e2).getArgument();
            if (equals(e1, subE, null, null, true, false, false, vars)
                    && compareVarScopes(vars))
                return;
        }
        if ((isTrue(e1) && isFalse(e2)) || (isTrue(e2) && isFalse(e1)))
            return;
        String msg = couldNotApplyRuleMessage("Contradiction");
        err.error(exp.getLocation(), msg);
    }

    // ==========================================================
    // Reductio Ad Absurdum and related verification methods
    // ==========================================================

    private void checkHypDesigAndReductioAdAbsurdum(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigAndReductioAdAbsurdum()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        if (!findConflict(exps2.get(0))) {
            String msg = couldNotApplyRuleMessage("Reductio Ad Absurdum");
            err.error(exp.getLocation(), msg);
        }
    }

    private boolean findConflict(Exp exp) {
        exp = applyQuantifiedExp(exp);
        if (exp instanceof EqualsExp) {
            EqualsExp eqExp = (EqualsExp) exp;
            Exp LHS = eqExp.getLeft();
            Exp RHS = eqExp.getRight();
            if (eqExp.getOperator() == EqualsExp.EQUAL) {
                return areLogicalOpposites(LHS, RHS);
            }
            else {
                return !(areLogicalOpposites(LHS, RHS));
            }
        }
        return isFalse(exp);
    }

    // ==========================================================
    // Equality and related verification methods
    // ==========================================================

    // This method appears to proofcheck an equality step. -HwS
    // XXX : In the process of refactoring this.  -HwS
    private void checkHypDesigsAndEquality(
            JustifiedExp justifiedEqualityExpression) {
        debug("checkHypDesigsAndEquality...");

        debug("   " + justifiedEqualityExpression.asString(3, 5));

        applyQuantifiedExp(justifiedEqualityExpression);
        previousLines.push(justifiedEqualityExpression);

        //Get a handle on any referenced expressions
        List<Exp> referencedExpressions =
                getHypothesesExpressions(justifiedEqualityExpression, 1);
        if (referencedExpressions == null)
            return;

        Exp expressionWithoutJustification =
                applyQuantifiedExp(justifiedEqualityExpression.getExp());
        Exp substitutionExpression =
                applyQuantifiedExp(referencedExpressions.get(0));

        //Exp expressionWithoutJustification = expressionWithoutJustification; //applyQuantifiedExp(expressionWithoutJustification);
        //Exp substitutionExpression = substitutionExpression; //applyQuantifiedExp(substitutionExpression);

        if (!(expressionWithoutJustification instanceof EqualsExp)
                || !(substitutionExpression instanceof EqualsExp)) {

            debug("One of expressionWithoutJustification or substitutionExpression not an EqualsExp.");

            expressionWithoutJustification =
                    applyQuantifiedExp(unwrapSetExp(expressionWithoutJustification));
            substitutionExpression =
                    applyQuantifiedExp(unwrapSetExp(substitutionExpression));

            if (!(matchExpressions(expressionWithoutJustification, null,
                    substitutionExpression, null))) {

                debug("Did not match after unwrapSetExp and applyQuantifiedExp.");

                // try doing a simple substitution
                if (isIn(expressionWithoutJustification)) {
                    checkFunctionExpression(expressionWithoutJustification,
                            substitutionExpression);
                }
                else if (expressionWithoutJustification instanceof EqualsExp) {
                    substitute((EqualsExp) expressionWithoutJustification,
                            justifiedEqualityExpression.getJustification()
                                    .getHypDesig1().getMathExp().getId(),
                            substitutionExpression);
                }
                else {
                    String msg =
                            "This type of justified statement is not yet supported.";
                    err
                            .error(
                                    expressionWithoutJustification
                                            .getLocation(), msg);
                }
            }
            return;
        }

        Exp expressionLeft =
                ((EqualsExp) expressionWithoutJustification).getLeft();
        Exp expressionRight =
                ((EqualsExp) expressionWithoutJustification).getRight();
        Exp substitutionLeft = ((EqualsExp) substitutionExpression).getLeft();
        Exp substitutionRight = ((EqualsExp) substitutionExpression).getRight();

        boolean checked =
                traverseTree(expressionLeft, expressionLeft, expressionRight,
                        expressionRight, substitutionLeft, substitutionRight);
        if (!checked) {

            debug("Not checked.");

            checked =
                    traverseTree(expressionLeft, expressionLeft,
                            expressionRight, expressionRight,
                            substitutionRight, substitutionLeft);
            if (!checked) {

                debug("Still not checked.");

                if (expressionWithoutJustification instanceof EqualsExp) {

                    debug("expressionWithoutJustification an EqualsExp.");

                    substitute((EqualsExp) expressionWithoutJustification,
                            justifiedEqualityExpression.getJustification()
                                    .getHypDesig1().getMathExp().getId(),
                            substitutionExpression);
                }
                else {

                    debug("expressionWithoutJustification NOT an EqualsExp.");

                    String msg = couldNotApplyRuleMessage(null);
                    err.error(expressionLeft.getLocation(), msg);
                    return;
                }
            }
        }

        debug("...checkHypDesigsAndEquality");
    }

    private void checkHypDesigAndDef(JustifiedExp exp) {
        System.out.println("In ProofChecker.checkHypDesigAndDef()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        System.out
                .println("The Equality (Def) rule has not yet been implemented.");
    }

    // ==========================================================
    // Or and related verification methods
    // ==========================================================

    private void checkHypDesigAndOrRule(JustifiedExp exp) {
        // System.out.println("In ProofChecker.checkHypDesigAndOrRule()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        Exp currentLine = exp.getExp();
        boolean matched = false;
        if (isOr(currentLine)) {
            List<Exp> LHSRHS = currentLine.getSubExpressions();
            applyQuantExps(LHSRHS);

            List<VariableExpBinding> vars = new List<VariableExpBinding>();
            matched =
                    (equals(LHSRHS.get(0), applyQuantifiedExp(exps2.get(0)),
                            null, null, false, false, true, vars) && compareVarScopes(vars));
            if (!matched) {
                matched =
                        (equals(LHSRHS.get(1),
                                applyQuantifiedExp(exps2.get(0)), null, null,
                                false, false, true, vars) && compareVarScopes(vars));
            }
        }
        if (!matched) {
            orRuleError(exp.getLocation());
        }
    }

    // ==========================================================
    // Conjunct Elimination and related verification methods
    // ==========================================================

    private void checkHypDesigAndConjunctElimination(JustifiedExp exp) {
        // System.out.println("In
        // ProofChecker.checkHypDesigAndConjunctElimination()");
        List<Exp> exps2 = getHypothesesExpressions(exp, 1);
        if (exps2 == null)
            return;
        exp = (JustifiedExp) (applyQuantifiedExp(exp));
        previousLines.push(exp);
        if (!matchConjunct(exp.getExp(), exps2.get(0))) {
            String msg = couldNotApplyRuleMessage("Conjunct Elimination");
            err.error(exp.getLocation(), msg);
        }
    }

    private boolean matchConjunct(Exp e1, Exp e2) {
        e1 = applyQuantifiedExp(e1);
        e2 = applyQuantifiedExp(e2);
        if (e2 instanceof InfixExp) {
            if (isAnd(e2)) {
                List<VariableExpBinding> vars = new List<VariableExpBinding>();
                if (equals(e1, applyQuantifiedExp(((InfixExp) e2).getLeft()),
                        null, null, false, false, false, vars)
                        && compareVarScopes(vars)) {
                    return true;
                }
                vars = new List<VariableExpBinding>();
                if (equals(e1, applyQuantifiedExp(((InfixExp) e2).getRight()),
                        null, null, false, false, false, vars)
                        && compareVarScopes(vars)) {
                    return true;
                }
            }
        }
        List<Exp> subExps = e2.getSubExpressions();
        Iterator<Exp> it = subExps.iterator();
        while (it.hasNext()) {
            if (matchConjunct(e1, it.next())) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================
    // Excluded Middle and related verification methods
    // ==========================================================

    private void checkExcludedMiddle(JustifiedExp exp) {
        // System.out.println("In ProofChecker.checkExcludedMiddle()");
        Exp je = applyQuantifiedExp(exp.getExp());
        if (isAnd(je)) {
            Exp LHS = ((InfixExp) je).getLeft();
            Exp RHS = ((InfixExp) je).getRight();
            if (isNot(LHS)) {
                List<VariableExpBinding> vars = new List<VariableExpBinding>();
                Exp subE = ((PrefixExp) LHS).getArgument();
                if (equals(RHS, subE, null, null, true, false, false, vars)
                        && compareVarScopes(vars))
                    return;
            }
            else if (isNot(RHS)) {
                List<VariableExpBinding> vars = new List<VariableExpBinding>();
                Exp subE = ((PrefixExp) RHS).getArgument();
                if (equals(LHS, subE, null, null, true, false, false, vars)
                        && compareVarScopes(vars))
                    return;
            }
            if ((isTrue(LHS) && isFalse(RHS)) || (isTrue(RHS) && isFalse(LHS)))
                return;
        }
        String msg = couldNotApplyRuleMessage("Excluded Middle");
        err.error(je.getLocation(), msg);
    }

    // ==========================================================
    // Expression unwrapping methods
    // ==========================================================

    /**
     * The method appears to take an expression of the sort that wraps an
     * internal mathematical expression (such as a justified expression, which 
     * consists of a mathematical expression and a justification), and just
     * return the mathematical expression.
     */
    private Exp unwrapExp(Location loc, Exp matchedLine) {
        if (matchedLine == null) {
            return null;
        }
        if (matchedLine instanceof ProofDefinitionExp) {
            DefinitionDec dec = ((ProofDefinitionExp) matchedLine).getExp();
            if (dec.getDefinition() != null) {
                return dec.getDefinition();
            }
            else {
                String msg =
                        "Must reference a single case of an inductive proof.";
                err.error(loc, msg);
                return null;
            }
        }
        if (matchedLine instanceof SuppositionExp) {
            if (((SuppositionExp) matchedLine).getExp() == null) {
                SuppositionExp sExp = (SuppositionExp) matchedLine;
                List<MathVarDec> vars = sExp.getVars();
                if (vars.size() == 0) {
                    return null;
                }
                else {
                    return null;
                }
            }
            else {
                return ((SuppositionExp) matchedLine).getExp();
            }
        }
        if (matchedLine instanceof DeductionExp) {
            return ((DeductionExp) matchedLine).getExp();
        }
        if (matchedLine instanceof JustifiedExp) {
            return ((JustifiedExp) matchedLine).getExp();
        }
        else {
            System.out.println(matchedLine.getClass());
            String msg = "Cannot reference a GoalExp as a justification.";
            err.error(loc, msg);
            return null;
        }
    }

    //	public Exp oldUnwrapQuantExp(Exp e) {
    //		if (e instanceof QuantExp) {
    //			Iterator<MathVarDec> it = ((QuantExp)e).getVars().iterator();
    //			MathVarDec dec = null;
    //			while (it.hasNext()) {
    //				dec = it.next();
    //				e = markVars(((QuantExp)e).getOperator(), dec, e);
    //			}
    //			return unwrapQuantExp(((QuantExp)e).getBody());
    //		}
    //		return e;
    //	}

    private DefinitionDec unwrapDefinition(DefinitionDec d) {
        DefinitionDec temp = (DefinitionDec) d;
        temp.setDefinition(applyQuantifiedExp(temp.getDefinition()));
        temp.setBase(applyQuantifiedExp(temp.getBase()));
        temp.setHypothesis(applyQuantifiedExp(temp.getHypothesis()));
        return temp;
    }

    /**
     * <p>Recursively descends into a provided expression, removing any 
     * <code>QuantExp</code> nodes and distributing them down to the variables 
     * beneath by setting those variables' quantifier field.  This modification 
     * is done in place and in almost all cases this method will return the same
     * <code>Exp</code> reference it was provided.  However, if the top level of
     * <code>e</code> is itself a <code>QuantExp</code>, the return value will
     * be a reference to the first non-<code>QuantExp</code> node.
     * 
     * @param e The expression to descend into and mark.
     * @return A reference to the new top level of the modified <code>e</code> 
     *         with the necessary changes made.
     */
    private Exp applyQuantifiedExp(Exp e) {
        if (e == null)
            return e;
        if (e instanceof AltItemExp) {
            AltItemExp temp = (AltItemExp) e;
            temp.setTest(applyQuantifiedExp(temp.getTest()));
            temp.setAssignment(applyQuantifiedExp(temp.getAssignment()));
            return temp;
        }
        else if (e instanceof BetweenExp) {
            BetweenExp temp = (BetweenExp) e;
            applyQuantExps(temp.getLessExps());
            return temp;
        }
        else if (e instanceof DeductionExp) {
            DeductionExp temp = (DeductionExp) e;
            temp.setExp(applyQuantifiedExp(temp.getExp()));
            return temp;
        }
        else if (e instanceof DotExp) {
            DotExp temp = (DotExp) e;
            applyQuantExps(temp.getSegments());
            temp.setSemanticExp(applyQuantifiedExp(temp.getSemanticExp()));
            return temp;
        }
        else if (e instanceof EqualsExp) {
            EqualsExp temp = (EqualsExp) e;
            temp.setLeft(applyQuantifiedExp(temp.getLeft()));
            temp.setRight(applyQuantifiedExp(temp.getRight()));
            return temp;
        }
        else if (e instanceof FieldExp) {
            FieldExp temp = (FieldExp) e;
            temp.setField(applyQuantifiedExp(temp.getField()));
            temp.setStructure(applyQuantifiedExp(temp.getStructure()));
            return temp;
        }
        else if (e instanceof FunctionExp) {
            FunctionExp temp = (FunctionExp) e;
            temp.setNatural(applyQuantifiedExp(temp.getNatural()));
            return temp;
        }
        else if (e instanceof GoalExp) {
            GoalExp temp = (GoalExp) e;
            temp.setExp(applyQuantifiedExp(temp.getExp()));
            return temp;
        }
        else if (e instanceof IfExp) {
            IfExp temp = (IfExp) e;
            temp.setTest(applyQuantifiedExp(temp.getTest()));
            temp.setThenclause(applyQuantifiedExp(temp.getThenclause()));
            temp.setElseclause(applyQuantifiedExp(temp.getElseclause()));
            return temp;
        }
        else if (e instanceof InfixExp) {
            InfixExp temp = (InfixExp) e;
            temp.setLeft(applyQuantifiedExp(temp.getLeft()));
            temp.setRight(applyQuantifiedExp(temp.getRight()));
            return temp;
        }
        else if (e instanceof IterativeExp) {
            IterativeExp temp = (IterativeExp) e;
            temp.setWhere(applyQuantifiedExp(temp.getWhere()));
            temp.setBody(applyQuantifiedExp(temp.getBody()));
            return temp;
        }
        else if (e instanceof JustifiedExp) {
            JustifiedExp temp = (JustifiedExp) e;
            temp.setExp(applyQuantifiedExp(temp.getExp()));
            return temp;
        }
        else if (e instanceof LambdaExp) {
            LambdaExp temp = (LambdaExp) e;
            temp.setBody(applyQuantifiedExp(temp.getBody()));
            return temp;
        }
        else if (e instanceof OldExp) {
            OldExp temp = (OldExp) e;
            temp.setExp(applyQuantifiedExp(temp.getExp()));
            return temp;
        }
        else if (e instanceof OutfixExp) {
            OutfixExp temp = (OutfixExp) e;
            temp.setArgument(applyQuantifiedExp(temp.getArgument()));
            return temp;
        }
        else if (e instanceof PrefixExp) {
            PrefixExp temp = (PrefixExp) e;
            temp.setArgument(applyQuantifiedExp(temp.getArgument()));
            return temp;
        }
        else if (e instanceof ProofDefinitionExp) {
            ProofDefinitionExp temp = (ProofDefinitionExp) e;
            temp.setExp(unwrapDefinition(temp.getExp()));
            return temp;
        }
        else if (e instanceof QuantExp) {
            Iterator<MathVarDec> it = ((QuantExp) e).getVars().iterator();
            MathVarDec dec = null;
            while (it.hasNext()) {
                dec = it.next();
                e = markVars(((QuantExp) e).getOperator(), dec, e);
            }
            return applyQuantifiedExp(((QuantExp) e).getBody());
        }
        else if (e instanceof SetExp) {
            SetExp temp = (SetExp) e;
            temp.setWhere(applyQuantifiedExp(temp.getWhere()));
            temp.setBody(applyQuantifiedExp(temp.getBody()));
            return temp;
        }
        else if (e instanceof SuppositionExp) {
            SuppositionExp temp = (SuppositionExp) e;
            temp.setExp(applyQuantifiedExp(temp.getExp()));
            return temp;
        }
        else if (e instanceof TupleExp) {
            TupleExp temp = (TupleExp) e;
            applyQuantExps(temp.getFields());
            return temp;
        }
        else if (e instanceof TypeFunctionExp) {
            TypeFunctionExp temp = (TypeFunctionExp) e;
            applyQuantExps(temp.getParams());
            return temp;
        }
        else if (e instanceof UnaryMinusExp) {
            UnaryMinusExp temp = (UnaryMinusExp) e;
            temp.setArgument(applyQuantifiedExp(temp.getArgument()));
            return temp;
        }
        else if (e instanceof VarExp) {
            return e;
        }
        else if (e instanceof IntegerExp) {
            return e;
        }
        else {
            System.out
                    .println("NOTE: In unwrapQuantExp(), encountered an exp of class "
                            + e.getClass());
            return e;
        }
    }

    /**
     * Recursively descends into each <code>Exp</code> in the provided list,
     * marking any variables that are bound by quantified expressions (for all, 
     * there exists, and unique) appropriately (by setting their quantifier 
     * field).  Does not change the structure of the expressions, except to
     * remove quantified expressions by distributing them over their sub
     * expression.
     * 
     * @param list The list of expressions to descend into.
     */
    private void applyQuantExps(List<Exp> list) {
        for (int curExpIndex = 0; curExpIndex < list.size(); curExpIndex++) {
            list.set(curExpIndex, applyQuantifiedExp(list.get(curExpIndex)));
        }
    }

    /**
     * Recursively descends into <code>targetExpression</code> and marks any
     * occurrences of <code>quantifiedVariable</code> (matching both name and
     * type) with the correct quantifier operator.
     * 
     * @param operator The operator to mark the variables with.
     * @param quantifiedVariable The variable of which to look for instances.
     * @param targetExpression The expression in which to look.
     * @return The marked expression.
     */
    private Exp markVars(int operator, MathVarDec quantifiedVariable,
            Exp targetExpression) {

        TypeConverter converter = new TypeConverter(table);
        String quantifiedVariableName = quantifiedVariable.getName().getName();
        Type quantifiedVariableType =
                converter.getMathType(quantifiedVariable.getTy());

        return markVars(operator, targetExpression, quantifiedVariableName,
                quantifiedVariableType);

    }

    /**
     * Just a recursive helper for the above method.  Prevents repeated
     * allocation of new objects and calculating of un-changing data.
     * 
     * @param operator The operator to mark the variables with.
     * @param targetExpression The expression in which to look.
     * @param quantifiedVariableName The name of the quantifiedVariable above.
     * @param quantifiedVariableType The Type of the quantifiedVariable above.
     * @return The marked expression.
     */
    private Exp markVars(int operator, Exp targetExpression,
            String quantifiedVariableName, Type quantifiedVariableType) {

        if (targetExpression instanceof VarExp) {
            VarExp targetAsVarExp = (VarExp) targetExpression;
            String targetName = targetAsVarExp.getName().getName();

            Type targetType = targetAsVarExp.getType();

            if (targetName.equals(quantifiedVariableName)
                    && tm.mathMatches(targetType, quantifiedVariableType)) {

                targetAsVarExp.setQuantification(operator);
            }
        }
        else {
            List<Exp> subExpList = targetExpression.getSubExpressions();
            for (int index = 0; index < subExpList.size(); index++) {
                subExpList.set(index, markVars(operator, subExpList.get(index),
                        quantifiedVariableName, quantifiedVariableType));
            }
        }
        return targetExpression;
    }

    private Exp unwrapSetExp(Exp exp) {
        if (exp instanceof SetExp) {
            return unwrapSetExp(((SetExp) exp).getBody());
        }
        return exp;
    }

    // ==========================================================
    // Reference resolution methods
    // ==========================================================

    /**
     * <p>Takes a <code>JustifiedExp</code> and converts its hypotheses (named 
     * or implied) into a <code>List</code> of <code>Exp</code>s, returning that
     * list.</p>
     * 
     * <p>If the correct number of hypotheses was not provided and cannot be
     * coerced (by included the previous line, for example), or a given 
     * expression cannot be found, gives an error using <code>err</code> and
     * returns <code>null</code>.</p>
     * 
     * @param justifiedExp The expression for which to find the hypotheses.
     * @param expectedHypothesesCount The number of hypotheses we expect to 
     *                                find, based on the justification being
     *                                used.
     * 
     * @return A <code>List</code> of <code>Exp</code>s containing named and
     *         implicit hypotheses as expressions.
     *         
     * @throws ReferenceNotFoundException If a named hypothesis cannot be
     *                                    found.
     */
    private List<Exp> getHypothesesExpressions(JustifiedExp exp, int no_expected) {

        try {
            List<Exp> list = innerGetHypothesesExpressions(exp, no_expected);
            return list;
        }
        catch (ReferenceNotFoundException e) {
            String msg = referenceNotFound();
            err.error(exp.getLocation(), msg);
            return null;
        }
    }

    /**
     * <p>Takes a <code>JustifiedExp</code> and converts its hypotheses (named 
     * or implied) into a <code>List</code> of <code>Exp</code>s, returning that
     * list.</p>
     * 
     * <p>If the correct number of hypotheses was not provided and cannot be
     * coerced (by included the previous line, for example), gives an error
     * using <code>err</code></p>
     * 
     * @param justifiedExp The expression for which to find the hypotheses.
     * @param expectedHypothesesCount The number of hypotheses we expect to 
     *                                find, based on the justification being
     *                                used.
     * 
     * @return A <code>List</code> of <code>Exp</code>s containing named and
     *         implicit hypotheses as expressions.
     * @throws ReferenceNotFoundException If a named hypothesis cannot be
     *                                    found.
     */
    private List<Exp> innerGetHypothesesExpressions(JustifiedExp justifiedExp,
            int expectedHypothesesCount) throws ReferenceNotFoundException {
        List<Exp> retval = null;

        //Get any MathRefs contained in the justification
        List<MathRefExp> list =
                extractMathRefs(justifiedExp.getJustification());

        //We have references, but we need the things those references refer to.
        //Find any that are in the symbol table...
        List<Entry> entries = getEntryRefs(list);

        //And any that are in the proof itself to this point...
        List<Exp> exps = getExpRefs(list);

        //If we were given fewer hypotheses than we expected, count the previous
        //line as a hypothesis (assuming there is a previous line).
        if (list.size() < expectedHypothesesCount) {
            if (previousLines.size() > 0) {
                exps.add(getPreviousLine());
            }
        }

        //If the line references itself, add itself as an available fact
        if (containsSelfReference(justifiedExp.getJustification())) {
            exps.add(getSelfReference(justifiedExp.getExp()));
        }

        // XXX : This sort of check probably belongs in the Analyzer. See also
        //       getEntryRefs() -HwS
        if (!err.countExceeds(0)) {
            if (((entries.size() + exps.size() != expectedHypothesesCount))) {
                //We can't get the proper number of hypotheses
                String msg = "Improper number of hypothesis designators.";
                err.error(justifiedExp.getJustification().getLocation(), msg);
            }
            else {
                //Compile the final list of Exps
                retval = new List<Exp>();

                for (Entry curEntry : entries) {
                    retval.add(getExpFromEntry(curEntry));
                }

                for (Exp curExp : exps) {
                    retval.add(curExp);
                }

                applyQuantExps(retval);
            }
        }

        return retval;
    }

    /**
     * Returns true iff the provided reference refers to "self".
     * 
     * @param mre A reference.
     * @return True iff the reference refers to "self".
     */
    private boolean isSelfReference(MathRefExp mre) {
        return (mre.getKind() == MathRefExp.SELF);
    }

    /**
     * Returns true iff the provided <code>JustificationExp</code> lists itself
     * as part of its justification.
     * 
     * @param exp The <code>JustificationExp</code> in question.
     * @return True iff <code>exp</code> lists itself as part of its 
     *         justification.
     */
    private boolean containsSelfReference(JustificationExp exp) {
        HypDesigExp hde = exp.getHypDesig1();
        if (hde != null && isSelfReference(hde.getMathExp())) {
            return true;
        }
        hde = exp.getHypDesig2();
        if (hde != null && isSelfReference(hde.getMathExp())) {
            return true;
        }
        return false;
    }

    private void findMemberships(Exp exp, List<MathVarDec> members) {
        if (exp instanceof QuantExp) {
            members.addAll(((QuantExp) exp).getVars());
        }
        else if (exp instanceof SetExp) {
            members.add(((SetExp) exp).getVar());
        }
        else if (exp instanceof SuppositionExp) {
            members.addAll(((SuppositionExp) exp).getVars());
        }
        Iterator<Exp> it = exp.getSubExpressions().iterator();
        while (it.hasNext()) {
            findMemberships(it.next(), members);
        }
    }

    private Exp convertTyToExp(Location loc, Ty ty) {
        if (!(ty instanceof NameTy)) {
            System.out.println("Conversion from " + ty.getClass()
                    + " to Exp not yet supported.");
            return null;
        }
        else {
            NameTy nty = (NameTy) ty;
            VarExp ve = new VarExp(loc, nty.getQualifier(), nty.getName());
            TypeConverter tc = new TypeConverter(table);
            List<Type> types = new List<Type>();
            types.add(tc.getMathType(nty));
            PosSymbol set = new PosSymbol(loc, Symbol.symbol("Set"));
            ConstructedType ct =
                    new ConstructedType(null, set, types, table
                            .getCurrentBinding());
            ve.setType(ct);
            return ve;
        }
    }

    private Exp buildIsIn(Location loc, MathVarDec dec) {
        PosSymbol isIn = new PosSymbol(loc, Symbol.symbol("is_in"));
        VarExp element = new VarExp(loc, null, dec.getName());
        TypeConverter tc = new TypeConverter(table);
        element.setType(tc.getMathType(dec.getTy()));
        Exp set = convertTyToExp(loc, dec.getTy());
        InfixExp ie = new InfixExp(loc, element, isIn, set);
        TypeBuilder tb = new TypeBuilder(table, myInstanceEnvironment);
        ie.setType(tb.getType("Boolean_Theory", "B", loc, false));
        return ie;
    }

    private Exp buildAnd(Location loc, Exp LHS, Exp RHS) {
        PosSymbol and = new PosSymbol(loc, Symbol.symbol("and"));
        InfixExp ie = new InfixExp(loc, LHS, and, RHS);
        TypeBuilder tb = new TypeBuilder(table, myInstanceEnvironment);
        ie.setType(tb.getType("Boolean_Theory", "B", loc, false));
        return ie;
    }

    private Exp buildMembershipExp(Location loc, List<MathVarDec> members) {
        if (members.size() == 1) {
            return buildIsIn(loc, members.get(0));
        }
        else {
            return buildMembershipExpSub(loc, members, 0);
        }
    }

    private Exp buildMembershipExpSub(Location loc, List<MathVarDec> members,
            int i) {
        if (i == members.size() - 1)
            return buildIsIn(loc, members.get(i));
        return buildAnd(loc, buildIsIn(loc, members.get(i)),
                buildMembershipExpSub(loc, members, i + 1));
    }

    private Exp getSelfReference(Exp exp) {
        if (!(exp instanceof QuantExp)) {
            return null;
        }
        List<MathVarDec> members = new List<MathVarDec>();
        findMemberships(exp, members);
        if (members.size() < 1) {
            return null;
        }
        else {
            return buildMembershipExp(exp.getLocation(), members);
        }
    }

    /**
     * Takes a <code>List</code> of <code>MathRefExp</code>s and returns a
     * <code>List</code> of corresponding <code>Entry</code>s.  Note that the
     * <code>n</code>th element of the returned list does not necessarily
     * correspond to the <code>n</code>th element in the given list, as any
     * reference that can not be located in the symbol table does not appear in
     * the returned list.
     * 
     * @param mathRefs The <code>List</code> of <code>MathRefExp</code>s to
     *                 be converted into their corresponding <code>Entry</code>s
     *                 from the current symbol table.
     *                 
     * @return The corresponding <code>Entry</code>s.
     */
    private List<Entry> getEntryRefs(List<MathRefExp> mathRefs) {
        List<Entry> entries = new List<Entry>();

        for (MathRefExp curMathReference : mathRefs) {
            if (curMathReference.getKind() != MathRefExp.SUPPOSITION
                    && curMathReference.getKind() != MathRefExp.DEDUCTION
                    && curMathReference.getKind() != MathRefExp.LINEREF) {

                entries.add(findHypothesisIfEntry(curMathReference));
            }
        }

        return entries;
    }

    /**
     * Takes a <code>List</code> of <code>MathRefExp</code>s and searches the
     * lines parsed so far (previousLines) for the corresponding expressions, 
     * returning a list of any such expressions it finds. Note that the 
     * <code>n</code>th element of the returned list does not necessarily 
     * correspond to the <code>n</code>th element in the given list, as any 
     * reference that can not be located in the lines parsed so far does not 
     * appear in the returned list.
     * 
     * @param mathRefs The <code>List</code> of <code>MathRefExp</code>s to
     *                 be converted into their corresponding <code>Exp</code>s
     *                 from previousLines.
     *                 
     * @return The corresponding <code>Exp</code>s from previousLines.
     */
    private List<Exp> getExpRefs(List<MathRefExp> mathRefs)
            throws ReferenceNotFoundException {

        List<Exp> exps = new List<Exp>();

        Exp e;
        for (MathRefExp curReference : mathRefs) {
            if (curReference.getKind() == MathRefExp.SUPPOSITION
                    || curReference.getKind() == MathRefExp.DEDUCTION
                    || curReference.getKind() == MathRefExp.LINEREF) {

                e = findHypothesisIfExp(curReference);

                if (e == null) {
                    throw new ReferenceNotFoundException();
                }

                exps.add(e);
            }
        }

        return exps;
    }

    /**
     * Takes an <code>Entry</code> and returns its embedded <code>Exp</code>.
     * 
     * XXX : This method will only return expressions for entries of kind
     *       definition or theorem.  Are there other possibilities?
     *       
     * @param e The Entry in question.
     * @return The embedded Exp.
     */
    private Exp getExpFromEntry(Entry e) {
        Exp retval = null;

        if (e instanceof DefinitionEntry) {
            DefinitionEntry eAsDefinitionEntry = (DefinitionEntry) e;

            //Evil evil hack. See TO DO note at top of Analyzer.java. May the 
            //gods forgive me. -HwS
            if (eAsDefinitionEntry.getIndex() != null
                    && eAsDefinitionEntry.getIndex().equals("i")) {
                retval = eAsDefinitionEntry.getBaseDefinition();
            }
            else {
                retval = eAsDefinitionEntry.getValue();
            }
        }
        else if (e instanceof TheoremEntry) {
            retval = ((TheoremEntry) e).getValue();
        }
        else {
            System.out.println("Some weird kind of Entry!");
        }

        return retval;
    }

    /**
     * Returns from the current symbol table the <code>Entry</code> pointed to 
     * by the <code>MathRefExp</code> or <code>null</code> if no such entry can 
     * be found.
     * 
     * XXX : This method will only return entries for references of kind
     *       definition or theorem.  Are there other possibilities?
     *       
     * @param exp The <code>MathRefExp</code> for which to return the
     *            corresponding <code>Entry</code> from the symbol table.
     *            
     * @return The corresponding <code>Entry</code> or <code>null</code> if
     *         no such entry can be found.
     */
    private Entry findHypothesisIfEntry(MathRefExp exp) {
        //debug ("findHypothesisIfEntry: " + exp.getId());
        int kind = exp.getKind();
        if (kind == MathRefExp.DEFINITION) {
            DefinitionLocator locator =
                    new DefinitionLocator(table, true, tm, err);
            try {
                DefinitionEntry de =
                        locator.locateDefinition(exp.getSourceModule(), exp
                                .getId());

                //Evil evil hack. See TO DO note at top of Analyzer.java. May 
                //the gods forgive me. -HwS
                if (exp.getIndex() != null) {
                    de.setIndex(exp.getIndex());

                    if (de.getBaseDefinition() == null) {
                        err.error(de.getLocation(),
                                "Not an inductive definition.");
                    }
                }

                return de;
            }
            catch (SymbolSearchException e) {
                return null;
            }
        }
        else {
            TheoremLocator locator = new TheoremLocator(table, tm, err);
            try {
                TheoremEntry te = locator.locateTheorem(exp.getId());
                if (te.getKind() == kind) {
                    return te;
                }
                else {
                    // System.out.println("error location 2");
                    String msg =
                            locator.cantFindTheoremMessage(exp.getId()
                                    .getName());
                    err.error(exp.getLocation(), msg);
                    return null;
                }
            }
            catch (SymbolSearchException e) {
                return null;
            }
        }
    }

    /**
     * Takes a <code>MathRefExp</code> and returns the corresponding 
     * <code>Exp</code> to which it refers.  So, for example, if the reference 
     * is to a named line, returns the expression at that line.  And if its a 
     * definition, returns that definition.
     * 
     * @param reference The reference to find. 
     * @return It's corresponding expression, or <code>null</code> if no such
     *         expression can be found.
     */
    private Exp findHypothesisIfExp(MathRefExp reference) {
        int kind = reference.getKind();

        if (kind == MathRefExp.SUPPOSITION) {
            return findMostRecentSupposition();
        }
        else {
            return findNamedLine(reference.getId());
        }
    }

    /**
     * Returns a list of <code>MathRefExp</code>s pointed to inside a
     * justification by either hypothesis designator.  Self references are not
     * returned.  So, in general will return a list of zero, one, or two
     * <code>MathRefExp</code>s, since Justifications can only reference up to
     * two things.
     * 
     * @param justification The <code>JustificationExp</code> to be searched.
     * 
     * @return A <code>List</code> of <code>MathRefExp</code>s containing any
     *         <code>MathRefExp</code>s found inside the
     *         <code>JustificationExp</code>.
     */
    private List<MathRefExp> extractMathRefs(JustificationExp justification) {
        List<MathRefExp> referencedMathRefExps = new List<MathRefExp>();

        //Get the first hypothesis designator
        HypDesigExp wrkHypothesis = justification.getHypDesig1();
        if (wrkHypothesis != null) {

            //Figure out what the hypothesis designator points to and add it
            //to the list if it isn't a self-reference
            MathRefExp wrkMathReference = wrkHypothesis.getMathExp();
            if (wrkMathReference != null && !isSelfReference(wrkMathReference)) {

                referencedMathRefExps.add(wrkMathReference);
            }

            //Now check the second hypothesis designator and do the same.
            wrkHypothesis = justification.getHypDesig2();
            if (wrkHypothesis != null) {
                wrkMathReference = wrkHypothesis.getMathExp();
                if (wrkMathReference != null
                        && !isSelfReference(wrkMathReference)) {

                    referencedMathRefExps.add(wrkMathReference);
                }
            }
        }

        return referencedMathRefExps;
    }

    /**
     * Returns the most recent supposition from previousLines.
     * 
     * @return The <code>SuppositionExp</code> corresponding to that
     *         supposition.
     */
    private SuppositionExp findMostRecentSupposition() {
        return findSupposition(false);
    }

    /**
     * Returns the <code>Exp</code> corresponding to the expression with the
     * given line id in the lines checked so far.
     * 
     * @param lineId The line id to search for.
     * @return The corresponding <code>Exp</code>.
     */
    private Exp findNamedLine(PosSymbol lineId) {
        Exp retval;

        idPredicate.setName(lineId);
        retval = findLineMatching(idPredicate);
        if (retval != null) {
            retval = unwrapExp(retval.getLocation(), retval);
        }

        return retval;
    }

    /**
     * Takes a predicate and returns the most recent line from previousLines
     * for which the predicate returns true, or null if there is no such line.
     * 
     * @param p The predicate to use.
     * @return The most recent line from previousLines for which the predicate
     *         returns true, or null if there is no such line.
     */
    private Exp findLineMatching(ExpPredicate p) {
        Exp retval = null;

        if (!previousLines.isEmpty()) {
            Stack<Exp> backup = new Stack<Exp>();
            Exp currentExp = null;
            boolean matches = false;

            while (!previousLines.isEmpty() && !matches) {
                currentExp = previousLines.pop();
                backup.push(currentExp);
                matches = p.match(currentExp);
            }

            if (matches) {
                retval = currentExp;
            }

            while (!backup.isEmpty()) {
                previousLines.push(backup.pop());
            }
        }

        return retval;
    }

    /**
     * This method returns the <code>SuppositionExp</code> that matches the 
     * <code>DeductionExp</code> at the top of previousLines, or the most recent
     * <code>SuppositionExp</code> if <code>match</code> is false.
     * 
     * @param match Whether or not to skip internal Supposition/Deduction pairs
     *              or simply return the first Supposition we find.
     *              
     * @return If <code>match</code> is true, the supposition from previousLines
     *         that matches the deduction on top of previousLines.  If it is 
     *         false, the most recent supposition.
     */
    private SuppositionExp findSupposition(boolean match) {
        Stack<Exp> s2 = new Stack<Exp>();
        Exp deduction = previousLines.pop();

        Exp currentLine = null;
        SuppositionExp matchedLine = null;
        int counter = 0;
        while (!(previousLines.isEmpty()) && (matchedLine == null)) {
            currentLine = previousLines.pop();

            if (currentLine instanceof DeductionExp) {
                counter++;
            }
            else if (currentLine instanceof SuppositionExp) {
                if (!match || (match && counter == 0)) {
                    matchedLine = (SuppositionExp) currentLine;
                }
                counter--;
            }

            s2.push(currentLine);
        }

        //Restore lines to the way it was before
        while (!(s2.isEmpty())) {
            previousLines.push(s2.pop());
        }
        previousLines.push(deduction);

        return matchedLine;
    }

    private Exp getPreviousLine() {
        Exp eCurrent = previousLines.pop();
        previousLines.push(eCurrent);
        return unwrapExp(eCurrent.getLocation(), eCurrent);
    }

    /**
     * Tests to see if a provided <code>PosSymbol</code> is the name of a 
     * provided <code>Exp</code>.  If it is, returns the <code>Exp</code>,
     * otherwise, returns <code>null</code>.
     * 
     * @param lineId The name to test <code>currentExp</code> against.
     * @param currentExp The expression whose name to test.
     * 
     * @return <code>currentExp</code> if it is named <code>lineId</code>,
     *         <code>null</code> otherwise.
     */
    private Exp matchCurrentLine(PosSymbol lineId, Exp currentExp) {
        if (lineNamed(currentExp, lineId)) {
            return currentExp;
        }
        return null;
    }

    /**
     * Returns <code>true</code> iff the provided <code>lineId</code>'s name
     * matches the name of <code>line</code>.
     * 
     * @param line   An expression.
     * @param lineId The name of a line.
     * @return <code>True</code> if the given expression is named to match the
     *         given <code>lineId</code>.
     */
    private boolean lineNamed(Exp line, PosSymbol lineId) {
        PosSymbol ps = getLineId(line);

        return (ps != null && ps.equals(lineId.getName()));
    }

    /**
     *  Returns the line number (as a <code>PosSymbol</code>) of the given
     *  <code>Exp</code> if it is one of the <code>Exp</code> subtypes that
     *  contains a line number.  Otherwise, returns <code>null</code>.
     *  
     *  @param line The <code>Exp</code> for which the line number is desired.
     *  
     *  @return A <code>PosSymbol</code> containing the line number, or null if
     *          <code>line</code> is not of a subtype that has a line number.
     */
    private PosSymbol getLineId(Exp line) {
        PosSymbol retval;

        if (line instanceof LineNumberedExp) {
            retval = ((LineNumberedExp) line).getLineNum();
        }
        else {
            retval = null;
        }

        return retval;
    }

    // ==========================================================
    // Supposition/Deduction verification methods
    // ==========================================================

    private boolean findInSupposition(Exp e, Exp seContents) {
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        if (equals(e, seContents, null, null, false, false, true, vars)
                && compareVarScopes(vars)) {
            return true;
        }
        else {
            Iterator<Exp> it = seContents.getSubExpressions().iterator();
            while (it.hasNext()) {
                if (findInSupposition(e, it.next())) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean findExpWithinSupposition(Exp e) {
        Stack<Exp> tempStack = new Stack<Exp>();
        Exp deduction = previousLines.pop();
        Exp tempLine = previousLines.pop();
        Exp tlContents = null;
        boolean found = false;
        List<VariableExpBinding> vars;
        while (!(tempLine instanceof SuppositionExp)) {
            if (!(tempLine instanceof GoalExp)) {
                tlContents = unwrapExp(e.getLocation(), tempLine);
                vars = new List<VariableExpBinding>();
                if (equals(e, tlContents, null, null, false, false, true, vars)
                        && compareVarScopes(vars)) {
                    found = true;
                }
            }
            tempStack.push(tempLine);
            tempLine = previousLines.pop();
        }
        previousLines.push(tempLine);
        while (!(tempStack.isEmpty())) {
            previousLines.push(tempStack.pop());
        }
        previousLines.push(deduction);
        return found;
    }

    private boolean findExpInSupDeducBlock(Exp e) {
        Stack<Exp> temp = new Stack<Exp>();
        Exp currentStep = null;
        Exp currentStepUnwrapped = null;
        boolean found = false;
        while (!(previousLines.isEmpty()) && !found) {
            currentStep = previousLines.pop();
            if (currentStep instanceof SuppositionExp) {
                temp.push(currentStep);
                break;
            }
            else if (!(currentStep instanceof DeductionExp)) {
                currentStepUnwrapped =
                        unwrapExp(currentStep.getLocation(), currentStep);
                List<VariableExpBinding> vars = new List<VariableExpBinding>();
                if (equals(e, currentStepUnwrapped, null, null, false, false,
                        true, vars)
                        && compareVarScopes(vars)) {
                    found = true;
                }
            }
            temp.push(currentStep);
        }
        while (!(temp.isEmpty())) {
            previousLines.push(temp.pop());
        }
        return found;
    }

    private List<Exp> substituteWithinSupposition(Exp e) {
        List<Exp> list = new List<Exp>();
        Stack<Exp> tempStack = new Stack<Exp>();
        Exp deduction = previousLines.pop();
        Exp tempLine = previousLines.pop();
        Exp tlContents = null;
        Exp LHS = null;
        Exp RHS = null;
        List<VariableExpBinding> vars;
        while (!(tempLine instanceof SuppositionExp)) {
            if (!(tempLine instanceof GoalExp)) {
                tlContents = unwrapExp(e.getLocation(), tempLine);
                if (tlContents instanceof EqualsExp) {
                    if (((EqualsExp) tlContents).getOperator() == EqualsExp.EQUAL) {
                        LHS = ((EqualsExp) tlContents).getLeft();
                        RHS = ((EqualsExp) tlContents).getRight();
                        vars = new List<VariableExpBinding>();
                        if (equals(LHS, e, null, null, false, false, true, vars)
                                && compareVarScopes(vars)) {
                            if (RHS.getMarker() != previousLines.size()) {
                                list.add(RHS);
                                RHS.setMarker(previousLines.size());
                            }
                        }
                        else {
                            vars = new List<VariableExpBinding>();
                            if (equals(RHS, e, null, null, false, false, true,
                                    vars)
                                    && compareVarScopes(vars)) {
                                if (LHS.getMarker() != previousLines.size()) {
                                    list.add(LHS);
                                    LHS.setMarker(previousLines.size());
                                }
                            }
                        }
                    }
                }
            }
            tempStack.push(tempLine);
            tempLine = previousLines.pop();
        }
        previousLines.push(tempLine);
        while (!(tempStack.isEmpty())) {
            previousLines.push(tempStack.pop());
        }
        previousLines.push(deduction);
        return list;
    }

    private void proofcheckSupDeduc(SuppositionDeductionExp value) {
        proofcheck(value.getSupposition());
        Iterator<Exp> it = value.getBody().iterator();
        while (it.hasNext()) {
            proofcheck(it.next());
        }
        proofcheckDeduction(value.getDeduction());
    }

    private void proofcheckDeduction(DeductionExp de) {
        de = (DeductionExp) (applyQuantifiedExp(de));
        previousLines.push(de);
        Exp deContents = unwrapExp(de.getLocation(), de);
        // match if part
        if (!(deContents instanceof IfExp)) {
            suppositionError(de.getLocation());
            return;
        }
        Exp ifPart_de = ((IfExp) deContents).getTest();
        Exp thenPart_de = ((IfExp) deContents).getThenclause();
        SuppositionExp se = findSupposition(true);
        Exp seContents = unwrapExp(se.getLocation(), se);
        if (!(checkIfPart(ifPart_de, seContents))) {
            suppositionError(de.getLocation());
            return;
        }
        // match then part
        if (thenPart_de instanceof EqualsExp) {
            boolean matched =
                    traceDeduction(((EqualsExp) thenPart_de).getRight(),
                            ((EqualsExp) thenPart_de).getLeft());
            if (!matched) {
                matched =
                        traceDeduction(((EqualsExp) thenPart_de).getLeft(),
                                ((EqualsExp) thenPart_de).getRight());
            }
            if (((EqualsExp) thenPart_de).getOperator() == EqualsExp.EQUAL) {
                if (!matched) {
                    suppositionError(de.getLocation());
                }
            }
            else {
                if (matched) {
                    suppositionError(de.getLocation());
                }
            }
        }
        else {
            if (!(findExpWithinSupposition(thenPart_de))) {
                // try to find the statement somewhere in the body of the sup/deduc block!
                if (!(findExpInSupDeducBlock(thenPart_de))) {
                    suppositionError(de.getLocation());
                }
            }
        }
    }

    private boolean traceDeduction(Exp e1, Exp e2) {
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        if (equals(e2, e1, null, null, false, false, true, vars)
                && compareVarScopes(vars)) {
            return true;
        }
        List<Exp> list = substituteWithinSupposition(e2);
        if (list.size() == 0) {
            return false;
        }
        Iterator<Exp> it = list.iterator();
        Exp temp;
        while (it.hasNext()) {
            temp = it.next();
            if (traceDeduction(e1, temp)) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================
    // Expression identification methods
    // ==========================================================

    /**
     * Returns true iff <code>e</code> is the "and" operation from
     * <code>Boolean_Theory</code>.
     * 
     * XXX - Need this be hardcoded?  -HwS
     * 
     * @param e The expression to test.
     * 
     * @return True iff <code>e</code> is the "and" operation from
     * <code>Boolean_Theory</code>.
     */
    private boolean isAnd(Exp e) {
        return (isInfixBooleanOperation(e) && infixOperationNamed((InfixExp) e,
                "and"));
    }

    /**
     * Returns true iff <code>e</code> is the "or" operation from
     * <code>Boolean_Theory</code>.
     * 
     * XXX - Need this be hardcoded?  -HwS
     * 
     * @param e The expression to test.
     * 
     * @return True iff <code>e</code> is the "or" operation from
     * <code>Boolean_Theory</code>.
     */
    private boolean isOr(Exp e) {
        return (isInfixBooleanOperation(e) && infixOperationNamed((InfixExp) e,
                "or"));
    }

    /**
     * Returns true iff <code>e</code> is an infix operation with boolean
     * return type.
     * 
     * @param e The expression to test.
     * 
     * @return True iff <code>e</code> is an infix operation with boolean
     *         return type.
     */
    private boolean isInfixBooleanOperation(Exp e) {
        return ((e instanceof InfixExp) && hasBooleanReturnType(e));
    }

    /**
     * Returns true iff <code>e</code>'s operation's name matches 
     * <code>name</code>.  The comparison is case-insensitive.
     * 
     * @param e The <code>InfixExp</code> whose name to test.
     * @param name The name to test it against.
     * @return True iff <code>e</code>'s operation's name matches 
     *         <code>name</code> without respect to case.
     */
    private boolean infixOperationNamed(InfixExp e, String name) {
        String infixName = e.getOpName().getSymbol().getName().toLowerCase();

        return infixName.equals(name.toLowerCase());
    }

    /**
     * Returns true iff <code>e</code> has the RESOLVE type "B".
     * 
     * @param e The <code>Exp</code> to test.
     * 
     * @return True iff <code>e</code> has the RESOLVE type "B".
     */
    private boolean hasBooleanReturnType(Exp e) {
        return e.getMathType().isBoolean();
    }

    private boolean isNot(Exp e) {
        if (e instanceof PrefixExp) {
            String s1 = "not";
            String s2 = "Not";
            String s3 = "NOT";
            if (((PrefixExp) e).getSymbol().equals(s1)
                    || ((PrefixExp) e).getSymbol().equals(s2)
                    || ((PrefixExp) e).getSymbol().equals(s3)) {
                if (e.getMathType().isBoolean()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTrue(Exp e) {
        if (e instanceof VarExp) {
            if (((VarExp) e).getName().getName().equals("true")) {
                return true;
            }
        }
        return false;
    }

    private boolean isFalse(Exp e) {
        if (e instanceof VarExp) {
            if (((VarExp) e).getName().getName().equals("false")) {
                return true;
            }
        }
        return false;
    }

    private boolean isIn(Exp e) {
        if (e instanceof InfixExp) {
            if (((InfixExp) e).getOpName().equals("is_in")) {
                if (e.getMathType().isBoolean()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==========================================================
    // Verification methods for built-in definitions
    // ==========================================================

    private void checkFunctionExpression(Exp e, Exp seContents) {
        if (!checkIsIn(e, seContents)) {
            String msg = notIsInMessage();
            err.error(e.getLocation(), msg);
        }
    }

    private boolean checkAnd(Exp e1, Exp e2, Exp seContents) {
        return (checkIfPart(e1, seContents) && checkIfPart(e2, seContents));
    }

    private boolean checkOr(Exp e1, Exp e2, Exp seContents) {
        return (checkIfPart(e1, seContents) || checkIfPart(e2, seContents));
    }

    private boolean checkNot(Exp e1, Exp seContents) {
        return !(checkIfPart(e1, seContents));
    }

    private boolean checkSetMembership(Exp e, PosSymbol local, Ty localType,
            Exp body, Exp set) {
        VarExp var = new VarExp(local.getLocation(), null, local);
        TypeConverter tc = new TypeConverter(table);
        var.setType(tc.getMathType(localType));
        VariableExpBinding veb = new VariableExpBinding(var, e);
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        vars.add(veb);
        if (equals(set, body, null, null, true, false, false, vars)) {
            if (compareVarScopes(vars)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkIsIn(Exp e, Exp seContents) {
        Exp e1 = ((InfixExp) e).getLeft();
        Exp e2 = ((InfixExp) e).getRight();
        /* check to see whether or not the VarExp e1 is in Type e2 */
        boolean found = false;
        if (e2 instanceof VarExp) {
            Type t1 = e1.getType();
            ProofScope ps = getProofScope();
            TypeLocator tl = new TypeLocator(ps, myInstanceEnvironment);
            try {
                TypeEntry t2e =
                        tl.locateMathType(new TypeID(((VarExp) e2)
                                .getQualifier(), ((VarExp) e2).getName(), 0));
                Type t2 = t2e.getType();
                if (tm.mathMatches(t1, t2)) {
                    if (t2e.hasObligation()) {
                        // if the types have the same obligations, we're good
                        TypeConverter tc = new TypeConverter(table);
                        TypeEntry t1e = tl.locateMathType(tc.buildTypeID(t1));
                        if (t1e.hasObligation()) {
                            List<VariableExpBinding> vars =
                                    new List<VariableExpBinding>();
                            if (equals(t1e.getObligation(),
                                    t2e.getObligation(), null, null, false,
                                    false, false, vars)
                                    && compareVarScopes(vars)) {
                                return true;
                            }
                        }
                        if (seContents == null) {
                            String msg = "Reference could not be located.";
                            err.error(e.getLocation(), msg);
                            return false;
                        }
                        return checkSetMembership(e1, t2e.getLocal().getName(),
                                t2e.getLocal().getTy(), applyQuantifiedExp(t2e
                                        .getObligation()), seContents);
                    }
                    return true;
                }
                else {
                    /*
                     * see if it is stated explicitly somewhere in the
                     * supposition
                     */
                    return findInSupposition(e, seContents);
                }
            }
            catch (SymbolSearchException ex) {
                return findInSupposition(e, seContents);
            }
        }
        else if (!found && e2 instanceof SetExp) {
            if (seContents == null) {
                String msg = "Reference could not be located.";
                err.error(e.getLocation(), msg);
                return false;
            }
            return checkSetMembership(e1, ((SetExp) e2).getVar().getName(),
                    ((SetExp) e2).getVar().getTy(), ((SetExp) e2).getBody(),
                    seContents);
        }
        else {
            String msg = "Cannot resolve the element as a member of the set.";
            err.error(e1.getLocation(), msg);
            return false;
        }
    }

    private boolean checkIfPart(Exp e, Exp seContents) {
        /* base case */
        if (isIn(e)) {
            return checkIsIn(e, seContents);
        }
        if (isAnd(e)) {
            return checkAnd(((InfixExp) e).getLeft(),
                    ((InfixExp) e).getRight(), seContents);
        }
        if (isOr(e)) {
            return checkOr(((InfixExp) e).getLeft(), ((InfixExp) e).getRight(),
                    seContents);
        }
        if (isNot(e)) {
            return checkNot(((PrefixExp) e).getArgument(), seContents);
        }
        else {
            /* you need to search the nearest supposition for this expression! */
            return findInSupposition(e, seContents);
        }
    }

    // ==========================================================
    // Expression comparison methods
    // ==========================================================

    // XXX - This method is in the process of being refactored. -HwS
    private boolean traverseTree(Exp root, Exp e1, Exp root2, Exp e2, Exp se1,
            Exp se2) {

        debug("################ TRAVERSE TREE #####################");
        debug(" root = " + root.asString(8, 8));
        debug("   e1 = " + e1.asString(8, 8));
        debug("root2 = " + root2.asString(8, 8));
        debug("   e2 = " + e2.asString(8, 8));
        debug("  se1 = " + se1.asString(8, 8));
        debug("  se2 = " + se2.asString(8, 8));

        if (e1 == null) {
            if (e2 != null) {
                if (!mismatchedEqualityError(e2.getLocation())) {
                    return false;
                }
                return true;
            }
            return false;
        }
        /* check to see if this is it! */
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        if (equals(e1, se1, null, null, false, false, true, vars)) {
            debug("Equal...");

            //printVars(vars);

            if (compareVarScopes(vars)) {
                // the prospective RHS
                if (!checkUntransformedPortion(root, e1, root2, e2)) {
                    return false;
                }
                if (compareRHS(e1, e2, se1, se2, false, true, true, vars)) {
                    return true;
                }
            }
        }

        debug("Not it...");

        //		printVars(vars);
        // XXX - If e2 could potentially be null here, then e2.getClass() is a
        //       potential NullPointerException.
        if (e2 == null
                || !(e1.getClass().getName().equals(e2.getClass().getName()))) {
            debug("Something crazily un-understandable.");
            return false;
        }

        debug("Still trucking.");

        // we can assume e1 and e2 are of the same class
        if (!(e1.shallowCompare(e2))) {
            debug("Shallow compare failed.");
            if (!mismatchedEqualityError(e2.getLocation())) {
                debug("Not mismatched equality error.");
                return false;
            }

            debug("Mismatched equality error.");
            return true;
        }

        debug("Shallow compare succeeded.");

        if (!findNextInList(root, e1.getSubExpressions(), root2, e2
                .getSubExpressions(), se1, se2)) {

            debug("Could not findNextInList.");

            return false;
        }

        debug("findNextInList succeeded.");

        return true;
    }

    // XXX - This method is in the process of being refactored.  -HwS
    private boolean findNextInList(Exp root, List<Exp> list, Exp root2,
            List<Exp> list2, Exp se1, Exp se2) {
        boolean found = false;
        if (list.size() != list2.size()) {
            // System.out.println("error location 6");
            System.out.println("(9)");
            return mismatchedRHSError(root.getLocation());
        }

        Iterator<Exp> it = list.iterator();
        Iterator<Exp> it2 = list2.iterator();
        Exp temp = null;
        Exp temp2 = null;
        while (it.hasNext()) {
            temp = it.next();
            temp2 = it2.next();
            found = traverseTree(root, temp, root2, temp2, se1, se2);
            if (found) {
                return true;
            }
        }
        return false;
    }

    // XXX - This method is in the process of being refactored
    private boolean findMatchingExpInList(Exp e, List<Exp> list, Exp se1,
            Exp se2, boolean varStrict, boolean compareRHS,
            boolean matchTypesOnly, List<VariableExpBinding> vars) {
        Iterator<Exp> it = list.iterator();
        Exp current = null;
        while (it.hasNext()) {
            current = it.next();
            if (equals(e, current, se1, se2, varStrict, compareRHS,
                    matchTypesOnly, vars)) {
                return true;
            }
        }
        return false;
    }

    // XXX - This method is in the process of being refactored
    private boolean matchAndOr(InfixExp and1, InfixExp and2, Exp se1, Exp se2,
            boolean varStrict, boolean compareRHS, boolean matchTypesOnly,
            List<VariableExpBinding> vars) {

        List<Exp> sub1 = and1.getSubExpressions();
        List<Exp> sub2 = and2.getSubExpressions();

        boolean match = false;

        Iterator<Exp> it = sub1.iterator();
        while (it.hasNext() && !match) {
            match =
                    findMatchingExpInList(it.next(), sub2, se1, se2, varStrict,
                            compareRHS, matchTypesOnly, vars);
        }

        return match;
    }

    /*
     * flags: varStrict - if true, compares variables in each expression for the
     * same name (i.e. "for all a: N, a + 0 = a" will *not* match "for all b: N,
     * b + 0 = b") compareRHS - ultimately used by the
     * compareHypDesigsAndEquality() method; if true, on a VarExp in e2,
     * redirects to a special method "compareRHS(...)" in order to compare the
     * RHS of an expression with the RHS of a definition or assertion;
     * compareRHS takes the same flags as equals() matchTypesOnly - if true,
     * matches an expression of type X in the e1 to a variable of type X in e2
     * (i.e. "for all a, b: N, (a + b) + 0 = (a + b)" matches "for all a: N, a +
     * 0 = a"; if the flag were not on "a + b" would not match "a" because "a +
     * b" is an InfixExp and "b" is a VarExp) vars - if not null, a mapping of
     * variables in e2 to variables/expressions of the same type in e1 (i.e.
     * "for all a: N, a + 0 = a" will *not* match "for all m, n: N, m + 0 = n"
     * because "a" should map exclusively to m; however "for all a, b: N, a + 0 =
     * b" matches "for all m: N, m + 0 = m" because the "local" variables "a"
     * and "b" are both allowed to map to "m")
     */
    // XXX : This method is in the process of being refactored.  -HwS
    public boolean equals(Exp e1, Exp e2, Exp RHS, Exp se2, boolean varStrict,
            boolean compareRHS, boolean matchTypesOnly,
            List<VariableExpBinding> vars) {

        if ((isAnd(e1) && isAnd(e2)) || (isOr(e1) && isOr(e2))) {
            return matchAndOr((InfixExp) e1, (InfixExp) e2, RHS, se2,
                    varStrict, compareRHS, matchTypesOnly, vars);
        }

        if (!(e2 instanceof VarExp)) {
            if (!(e1.shallowCompare(e2))) {
                return false;
            }
            List<Exp> l1 = e1.getSubExpressions();
            List<Exp> l2 = e2.getSubExpressions();
            if (l1.size() != l2.size()) {
                return false;
            }
            Iterator<Exp> i1 = l1.iterator();
            Iterator<Exp> i2 = l2.iterator();
            while (i1.hasNext()) {
                if (!(equals(i1.next(), i2.next(), RHS, se2, varStrict,
                        compareRHS, matchTypesOnly, vars))) {
                    return false;
                }
            }
            return true;
        }
        else {
            if (vars != null && ((VarExp) e2).getIsLocal()) {
                return addVarToBinding((VarExp) e2, e1, vars);
            }
            if (compareRHS) {
                return compareRHS(RHS, e1, se2, e2, varStrict, compareRHS,
                        matchTypesOnly, vars);
            }
            else {
                return compareVarExps(e1, (VarExp) e2, RHS, se2, varStrict,
                        compareRHS, matchTypesOnly, vars);
            }
        }
    }

    private boolean compareRHS(Exp LHS, Exp RHS, Exp se1, Exp se2,
            boolean varStrict, boolean compareRHS, boolean matchTypesOnly,
            List<VariableExpBinding> vars) {
        if (se2 instanceof VarExp) {
            if (((VarExp) se2).getIsLocal()) {
                Exp subSE2 =
                        findVariableInSE(LHS, se1, ((VarExp) se2).getName(),
                                true);
                if (subSE2 == null) {
                    if (vars == null) {
                        // System.out.println("error location 5");
                        String msg =
                                equivalentExpNotFound(((VarExp) se2).getName()
                                        .getName());
                        err.error(se2.getLocation(), msg);
                        return false;
                    }
                    else {
                        return addVarToBinding((VarExp) se2, RHS, vars);
                    }
                }
                return equals(RHS, subSE2, LHS, se1, true, false, false, null);
            }
        }
        if (equals(RHS, se2, LHS, se1, false, true, matchTypesOnly, vars)) {
            if (compareVarScopes(vars)) {
                return true;
            }
        }
        return false;
    }

    private Exp findVariableInSE(Exp LHS, Exp se1, PosSymbol name,
            boolean foundMarked) {
        if (se1 == null)
            return null;
        if (se1 instanceof VarExp) {
            if (foundMarked && ((VarExp) se1).getName().equals(name.getName())) {
                return LHS;
            }
        }
        return findVariableInList(LHS.getSubExpressions(), se1
                .getSubExpressions(), name, foundMarked);
    }

    private Exp findVariableInList(List list, List list_se, PosSymbol name,
            boolean foundMarked) {
        Exp e = null;
        Iterator<Exp> it = list.iterator();
        Iterator<Exp> it_se = list_se.iterator();
        while (it_se.hasNext()) {
            Exp i1 = it.next();
            Exp i2 = it_se.next();
            e = findVariableInSE(i1, i2, name, foundMarked);
            if (e != null)
                return e;
        }
        return null;
    }

    private boolean compareVarExps(Exp e1, VarExp e2, Exp RHS, Exp se2,
            boolean varStrict, boolean compareRHS, boolean matchTypesOnly,
            List<VariableExpBinding> vars) {
        if (!(e1 instanceof VarExp)) {
            if (!matchTypesOnly || !(e2.getIsLocal())) {
                return false;
            }
            // compare the types of the two expressions
            return (tm.mathMatches(e1.getType(), e2.getType()));
        }
        // compare qualifiers
        if (((VarExp) e1).getQualifier() != null) {
            if (e2.getQualifier() == null
                    || !(((VarExp) e1).getQualifier().equals(e2.getQualifier()
                            .getName()))) {
                return false;
            }
        }
        else {
            if (e2.getQualifier() != null) {
                return false;
            }
        }
        if (e2.getIsLocal()) {
            // if the variable in e2 is local, compare on types only
            if (!(tm.mathMatches(e1.getType(), e2.getType()))) {
                return false;
            }
            else {
                if (varStrict) {
                    // if the varStrict flag is on, compare the names too
                    if (!(((VarExp) e1).getName()
                            .equals(e2.getName().getName()))) {
                        return false;
                    }
                }
            }
        }
        else {
            if (!(((VarExp) e1).getName().equals(e2.getName().getName()))
                    || !(tm.mathMatches(e1.getType(), e2.getType()))) {
                return false;
            }
        }
        return true;
    }

    private boolean checkUntransformedPortion(Exp rootLHS, Exp LHS,
            Exp rootRHS, Exp RHS) {
        if (rootLHS == LHS) {
            return true;
        }
        if (!rootLHS.shallowCompare(rootRHS))
            return false;
        List<Exp> LHSChildren = rootLHS.getSubExpressions();
        List<Exp> RHSChildren = rootRHS.getSubExpressions();
        if (LHSChildren.size() != RHSChildren.size())
            return false;
        Iterator<Exp> LHSIt = LHSChildren.iterator();
        Iterator<Exp> RHSIt = RHSChildren.iterator();
        while (LHSIt.hasNext()) {
            if (!checkUntransformedPortion(LHSIt.next(), LHS, RHSIt.next(), RHS))
                return false;
        }
        return true;
    }

    // ==========================================================
    // Utility methods
    // ==========================================================

    private ProofScope getProofScope() {
        Stack<Scope> scopes = table.getStack();
        Stack<Scope> scopes2 = new Stack<Scope>();
        Scope temp = null;
        boolean found = false;
        while (!found && !(scopes.isEmpty())) {
            temp = scopes.pop();
            if (temp instanceof ProofScope) {
                found = true;
            }
            scopes.push(temp);
        }
        while (!(scopes2.isEmpty())) {
            scopes.push(scopes2.pop());
        }
        if (found)
            return (ProofScope) temp;
        else
            return null;
    }

    private boolean areLogicalOpposites(Exp e1, Exp e2) {
        if (isNot(e1)) {
            List<VariableExpBinding> vars = new List<VariableExpBinding>();
            Exp subE = ((PrefixExp) e1).getArgument();
            if (equals(e2, subE, null, null, true, false, false, vars)
                    && compareVarScopes(vars)) {
                return true;
            }
        }
        if (isNot(e2)) {
            List<VariableExpBinding> vars = new List<VariableExpBinding>();
            Exp subE = ((PrefixExp) e2).getArgument();
            if (equals(e1, subE, null, null, true, false, false, vars)
                    && compareVarScopes(vars)) {
                return true;
            }
        }
        if ((isTrue(e1) && isFalse(e2)) || (isTrue(e2) && isFalse(e1)))
            return true;
        return false;
    }

    // XXX : This method is in the process of being refactored.  -HwS
    private boolean matchExpressions(Exp e1, Exp origE1, Exp e2, Exp origE2) {
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        if (equals(e1, e2, null, null, false, false, true, vars)) {
            return compareVarScopes(vars);
        }
        return false;
    }

    private boolean addVarToBinding(VarExp var, Exp e,
            List<VariableExpBinding> list) {
        Exp savedE = getExpFromVar(var, list);
        if (savedE != null) {
            return equals(savedE, e, null, null, true, false, false, null);
        }
        VariableExpBinding veb = new VariableExpBinding(var, e);
        list.add(veb);
        return true;
    }

    /*
     * It seems that this method attempts the actual substitution.  Near as I
     * can tell, exp is the expression we're trying to substitute within,
     * refName is the name of the reference we're supposed to use for the
     * substitution.  refValue is the actual expression associated with that
     * name.   -HwS
     */
    // XXX - This method in the process of being refactored.  -HwS
    private boolean substitute(EqualsExp exp, PosSymbol refName, Exp refValue) {
        VarExp var = new VarExp(refName.getLocation(), null, refName);
        var.setType(refValue.getType());
        VariableExpBinding veb = new VariableExpBinding(var, refValue);
        List<VariableExpBinding> vars = new List<VariableExpBinding>();
        vars.add(veb);
        if (!substituteSub(exp.getLeft(), exp.getRight(), vars)) {
            String msg =
                    "Could not apply substitution to the justified expression.";
            err.error(exp.getLocation(), msg);
            return false;
        }
        return true;
    }

    private boolean substituteSub(Exp e1, Exp e2, List<VariableExpBinding> vars) {
        if (e1.shallowCompare(e2)) {
            List<Exp> sub1 = e1.getSubExpressions();
            List<Exp> sub2 = e2.getSubExpressions();
            if (sub1.size() != sub2.size())
                return false;
            Iterator<Exp> subIt1 = sub1.iterator();
            Iterator<Exp> subIt2 = sub2.iterator();
            while (subIt1.hasNext()) {
                if (!substituteSub(subIt1.next(), subIt2.next(), vars)) {
                    return false;
                }
            }
            return true;
        }
        else {
            Exp surrogate = findMapping(e1, vars);
            if (surrogate != null) {
                if (equals(surrogate, e2, null, null, true, false, false, null))
                    return true;
            }
            surrogate = findMapping(e2, vars);
            if (surrogate != null) {
                if (equals(surrogate, e2, null, null, true, false, false, null))
                    return true;
            }
            return false;
        }
    }

    private Exp findMapping(Exp e, List<VariableExpBinding> vars) {
        if (!(e instanceof VarExp)) {
            return null;
        }
        Iterator<VariableExpBinding> it = vars.iterator();
        VariableExpBinding veb = null;
        while (it.hasNext()) {
            veb = it.next();
            if (((VarExp) e).getName().getName().equals(veb.getVarName())) {
                if (tm.mathMatches(e.getType(), veb.getVarType())) {
                    return veb.getExp();
                }
            }
        }
        return null;
    }

    private void printVars(List<VariableExpBinding> vars) {
        Iterator<VariableExpBinding> varsIt = vars.iterator();
        while (varsIt.hasNext()) {
            varsIt.next().prettyPrint();
            System.out.println();
        }
    }

    private void printList(List<Exp> exps) {
        Iterator<Exp> expsIt = exps.iterator();
        while (expsIt.hasNext()) {
            expsIt.next().prettyPrint();
            System.out.println();
        }
    }

    private boolean compareVarExpScopes(VarExp variable, VarExp match) {
        return variable.getQuantification() == VarExp.FORALL
                || variable.getQuantification() == match.getQuantification();
    }

    private boolean compareSubExpressionScopes(VarExp variable,
            Exp matchedExpression) {
        boolean retval = true;

        List<Exp> subexpressions = matchedExpression.getSubExpressions();

        Exp curExpression;
        Iterator<Exp> subexpressionsIter = subexpressions.iterator();
        while (retval && subexpressionsIter.hasNext()) {
            curExpression = subexpressionsIter.next();

            if (curExpression instanceof VarExp) {
                retval = compareVarExpScopes(variable, (VarExp) curExpression);
            }
            else {
                retval = compareSubExpressionScopes(variable, curExpression);
            }
        }

        return retval;
    }

    private boolean compareVarScopes(List<VariableExpBinding> vars) {
        boolean retval = true;

        VariableExpBinding currentBinding;
        Iterator<VariableExpBinding> varsIter = vars.iterator();
        while (retval && varsIter.hasNext()) {
            currentBinding = varsIter.next();

            if (currentBinding.getExp() instanceof VarExp) {
                retval =
                        compareVarExpScopes(currentBinding.getVarExp(),
                                (VarExp) currentBinding.getExp());
            }
            else {
                retval =
                        compareSubExpressionScopes(currentBinding.getVarExp(),
                                currentBinding.getExp());
            }
        }

        return retval;

        /*
        Iterator<VariableExpBinding> varsIt = vars.iterator();
        VariableExpBinding current = null;
        while (varsIt.hasNext()) {
        	current = varsIt.next();
        	if (current.getExp() instanceof VarExp) {
        		
        //				if(((VarExp)(current.getExp())).getQuantification() != 0 && ((VarExp)(current.getExp())).getQuantification() != 0) {
        			if (!((current.getVarExp().getQuantification() == ((VarExp) (current
        					.getExp())).getQuantification()))) {
            			return false;
        			}
        //				}
        	}
        	else {
        		if(!matchSubVarScopes(current.getExp(), current.getVarExp().getQuantification())) {
        			return false;
        		}
        	}
        }
        return true;
         */
    }

    private boolean matchSubVarScopes(Exp e, int quant) {
        if (e instanceof VarExp) {
            return (((VarExp) e).getQuantification() == quant);
        }
        Iterator<Exp> subVarsIt = e.getSubExpressions().iterator();
        while (subVarsIt.hasNext()) {
            if (!(matchSubVarScopes(subVarsIt.next(), quant))) {
                return false;
            }
        }
        return true;
    }

    //	private boolean comparePossibleInstantiationVarScopes(List<VariableExpBinding> vars) {
    //		Iterator<VariableExpBinding> varsIt = vars.iterator();
    //		VariableExpBinding current = null;
    //		VarExp ref = null;
    //		VarExp step = null;
    //		while (varsIt.hasNext()) {
    //			current = varsIt.next();
    //			if (current.getExp() instanceof VarExp) {
    //				ref = current.getVarExp();
    //				step = (VarExp)(current.getExp());
    //				if(ref.getQuantification() == VarExp.FORALL) {
    //					if(step.getQuantification() != VarExp.NONE &&
    //							step.getQuantification() != VarExp.FORALL) {
    //						return false;
    //					}
    ////					step.setQuantification(ref.getQuantification());
    //				}
    //				else if(ref.getQuantification() == VarExp.EXISTS) {
    //					if(step.getQuantification() != VarExp.NONE &&
    //							step.getQuantification() != VarExp.EXISTS) {
    //						return false;
    //					}
    ////					step.setQuantification(ref.getQuantification());
    //				}
    //				else {
    //					if(ref.getQuantification() != step.getQuantification()) {
    //						return false;
    //					}
    ////					step.setQuantification(ref.getQuantification());
    //				}
    //			}
    //		}
    //		return true;
    //	}

    private Exp getExpFromVar(VarExp var, List<VariableExpBinding> list) {
        String varStr = var.getName().getName();
        Iterator<VariableExpBinding> it = list.iterator();
        VariableExpBinding temp = null;
        while (it.hasNext()) {
            temp = it.next();
            if (temp.getVarName().equals(varStr)
                    && (tm.mathMatches(temp.getVarType(), var.getType()))) {
                return temp.getExp();
            }
        }
        return null;
    }

    // ==========================================================
    // Error methods
    // ==========================================================

    private String lineNotFoundMessage(String lineId) {
        return "Could not find a line by the identifier " + lineId
                + " above the currentLine.";
    }

    private boolean mismatchedRHSError(Location loc) {
        String msg = couldNotApplyRuleMessage(null);
        err.error(loc, msg);
        return false;
    }

    private String couldNotApplyRuleMessage(String rule) {
        if (rule == null) {
            return "Could not apply the rule to the proof expression.";
        }
        return "Could not apply the rule " + rule + " to the proof expression.";
    }

    private String notEqualsExpMessage() {
        return "The justified expression is not an expression of equality.";
    }

    private String notIsInMessage() {
        return "The specified element could not be verified as a member of the set.";
    }

    private void andRuleError(Location loc) {
        String msg = couldNotApplyRuleMessage("And Rule");
        err.error(loc, msg);
        return;
    }

    private void orRuleError(Location loc) {
        String msg = couldNotApplyRuleMessage("Or Rule");
        err.error(loc, msg);
        return;
    }

    private String equivalentExpNotFound(String name) {
        return "The expression equivalent to " + name + " was not found.";
    }

    private String LHSandRHSDontMatch() {
        return "Unable to verify the transformation from the information provided.";
    }

    private String referenceNotFound() {
        return "A reference could not be located.";
    }

    private String conflictingVariableBindings(PosSymbol name) {
        return "Conflicting values in the expression to be checked for the local variable "
                + name.getName() + ".";
    }

    private void mismatchedDeductionError(Location loc, int i) {
        String msg = "Deduction does not match supposition (" + i + ").";
        err.error(loc, msg);
    }

    private boolean mismatchedEqualityError(Location loc) {
        String msg = LHSandRHSDontMatch();
        err.error(loc, msg);
        return false;
    }

    private void suppositionError(Location loc) {
        String msg =
                "Could not verify deduction from supposition and intervening statements.";
        err.error(loc, msg);
    }

    private void debug(String s) {
        if (debugOn) {
            System.out.println(s);
        }
    }

    private abstract class ExpPredicate {

        public abstract boolean match(Exp e);
    }

    private class IdPredicate extends ExpPredicate {

        private PosSymbol myName;

        public void setName(PosSymbol newName) {
            myName = newName;
        }

        @Override
        public boolean match(Exp e) {
            return (matchCurrentLine(myName, e) != null);
        }
    }

    public static void setUpFlags() {
        FlagDependencies.addImplies(FLAG_PROOFCHECK, Analyzer.FLAG_TYPECHECK);
    }
}