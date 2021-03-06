/**
 * MathSymbolTable.java
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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.clemson.cs.r2jt.absyn.ModuleDec;
import edu.clemson.cs.r2jt.absyn.ResolveConceptualElement;
import edu.clemson.cs.r2jt.typereasoning.TypeGraph;

/**
 * <p>A <code>MathSymbolTable</code> represents an immutable mapping from
 * those nodes in the AST that define a scope to {@link FinalizedScope Scope} objects
 * representing those scopes and containing the symbols defined therein.</p>
 * 
 * <p><code>Scope</code>s that were introduced at the module-level (e.g.,
 * the scope defined by a 
 * {@link edu.clemson.cs.r2jt.absyn.MathModuleDec MathModuleDec}) will have an
 * associated <code>Scope</code> that is further refined into an instance of
 * {@link FinalizedModuleScope ModuleScope}.  As a convenience, such module scopes may
 * be retrieved in a type-safe way with a call to 
 * {@link #getModuleScope(ModuleIdentifier) getModuleScope()}.</p>
 * 
 * <p>Note that there are no public constructors for 
 * <code>MathSymbolTable</code>.  New instances should be acquired from a call 
 * to {@link MathSymbolTableBuilder#seal() MathSymbolTableBuilder.seal()}.</p>
 */
public class MathSymbolTable extends ScopeRepository {

    /**
     * <p>When starting a search from a particular scope, specifies how any
     * available facilities should be searched.</p>
     * 
     * <p>Available facilities are those facilities defined in a module searched
     * by the search's <code>ImportStrategy</code> (which necessarily always
     * includes the source module).</p>
     * 
     * <p>Note that facilities cannot be recursively searched.  Imports and
     * facilities appearing in available facilities will not be searched.</p>
     */
    public static enum FacilityStrategy {

        /**
         * <p>Indicates that available facilities should not be searched.  The
         * default strategy.</p>
         */
        FACILITY_IGNORE,

        /**
         * <p>Indicates that available facilities should be searched with 
         * generic types instantiated.  That is, any types used by symbols 
         * inside the facility should be updated to reflect the particular
         * instantiation of the generic types.</p>
         */
        FACILITY_INSTANTIATE,

        /**
         * <p>Indicates that available facilities should be searched with 
         * generic types intact.  That is, any types used by symbols inside the
         * facility will appear exactly as listed in the source file--including
         * references to generics--even if we could use information from the
         * facility to "fill them in."</p>
         */
        FACILITY_GENERIC
    }

    /**
     * <p>When starting a search from a particular scope, specifies which 
     * additional modules should be searched, based on any imported modules.</p>
     * 
     * <p>Imported modules are those listed in the <em>uses</em> clause of the 
     * source module scope in which the scope is introduced.  For searches 
     * originating directly in a module scope, the source module scope is the 
     * scope itself.  In addition to those scopes directly imported in the 
     * <em>uses</em> clause, any modules implicitly imported will also be 
     * searched.  Implicitly imported modules include the standard modules 
     * (<code>Std_Boolean_Fac</code>, etc.), and any modules named in the header
     * of the source module (e.g., an enhancement realization implicitly imports
     * it's associate enhancement and concept.)</p>
     */
    public static enum ImportStrategy {

        /**
         * <p>Indicates that imported modules should not be searched. The
         * default strategy.</p>
         */
        IMPORT_NONE {

            public ImportStrategy cascadingStrategy() {
                return IMPORT_NONE;
            }

            public boolean considerImports() {
                return false;
            }
        },

        /**
         * <p>Indicates that only those modules imported directly from the 
         * source module should be searched.</p>
         */
        IMPORT_NAMED {

            public ImportStrategy cascadingStrategy() {
                return IMPORT_NONE;
            }

            public boolean considerImports() {
                return true;
            }
        },

        /**
         * <p>Indicates that the search should recursively search the closure
         * of all imports and their own imports.</p>
         */
        IMPORT_RECURSIVE {

            public ImportStrategy cascadingStrategy() {
                return IMPORT_RECURSIVE;
            }

            public boolean considerImports() {
                return true;
            }
        };

        /**
         * <p>Returns the strategy that should be used to recursively search
         * any imported modules.</p>
         * 
         * @return The strategy that should be used to recursively search any
         *         imported modules.
         */
        public abstract ImportStrategy cascadingStrategy();

        /**
         * <p>Returns <code>true</code> <strong>iff</strong> this strategy
         * requires searching directly imported modules.</p>
         * 
         * @return <code>true</code> <strong>iff</strong> this strategy
         *         requires searching directly imported modules.
         */
        public abstract boolean considerImports();
    }

    private final Map<ResolveConceptualElement, FinalizedScope> myScopes =
            new HashMap<ResolveConceptualElement, FinalizedScope>();

    private final Map<ModuleIdentifier, FinalizedModuleScope> myModuleScopes =
            new HashMap<ModuleIdentifier, FinalizedModuleScope>();

    private final TypeGraph myTypeGraph;

    /*package private*/MathSymbolTable(TypeGraph typeGraph,
            Map<ResolveConceptualElement, ScopeBuilder> scopes,
            ScopeBuilder root) throws NoSuchModuleException {

        myTypeGraph = typeGraph;

        List<ImportRequest> importedModules = new LinkedList<ImportRequest>();

        seal(root, importedModules);

        for (ImportRequest request : importedModules) {
            if (!myModuleScopes.containsKey(request.importedModule)) {
                throw new NoSuchModuleException(request.sourceModule,
                        request.importedModule);
            }
        }
    }

    private void seal(ScopeBuilder b, List<ImportRequest> importedModules) {

        FinalizedScope result = b.seal(this);
        FinalizedModuleScope resultAsModuleScope;
        ModuleIdentifier resultIdentifier;

        ResolveConceptualElement definingElement = b.getDefiningElement();
        if (definingElement != null) {
            myScopes.put(definingElement, result);

            if (result instanceof FinalizedModuleScope) {
                resultAsModuleScope = (FinalizedModuleScope) result;
                resultIdentifier =
                        new ModuleIdentifier((ModuleDec) b.getDefiningElement());

                myModuleScopes.put(resultIdentifier, resultAsModuleScope);

                importedModules.addAll(buildImportRequests(resultIdentifier,
                        resultAsModuleScope.getImports()));
            }
        }

        for (ScopeBuilder curChild : b.children()) {
            curChild.setParent(result);
            seal(curChild, importedModules);
        }
    }

    private static List<ImportRequest> buildImportRequests(
            ModuleIdentifier source, List<ModuleIdentifier> imports) {

        List<ImportRequest> result = new LinkedList<ImportRequest>();

        for (ModuleIdentifier imported : imports) {
            result.add(new ImportRequest(source, imported));
        }

        return result;
    }

    @Override
    public TypeGraph getTypeGraph() {
        return myTypeGraph;
    }

    /**
     * <p>Returns the <code>Scope</code> associated with <code>e</code>.  If
     * there is not associated scope, throws a 
     * {@link NoSuchScopeException NoSuchScopeException}.</p>
     * 
     * @param e The node in the AST for which to retrieve an associated 
     *          <code>Scope</code>.
     *          
     * @return The associated scope.
     * 
     * @throws NoSuchScopeException If there is no <code>Scope</code> associated
     *                              with the given AST node.
     */
    public FinalizedScope getScope(ResolveConceptualElement e) {
        if (!myScopes.containsKey(e)) {
            throw new NoSuchScopeException(e);
        }

        return myScopes.get(e);
    }

    /**
     * <p>Returns the <code>ModuleScope</code> associated with 
     * <code>module</code>.  If there is no module by that name, throws a 
     * {@link NoSuchSymbolException NoSuchSymbolException}.</p>
     * 
     * <p>Barring the type of the <code>Exception</code> thrown, for all
     * <code>ModuleDec</code>s, <em>d</em>, if 
     * <code>module.equals(new ModuleIdentifier(d))<code>, then 
     * <code>getModuleScope(module)</code> is equivalent to
     * <code>(ModuleScope) getScope(d)</code>.</p>
     * 
     * @param e The node in the AST for which to retrieve an associated 
     *          <code>Scope</code>.
     *          
     * @return The associated scope.
     * 
     * @throws NoSuchScopeException If there is no <code>Scope</code> associated
     *                              with the given AST node.
     */
    public FinalizedModuleScope getModuleScope(ModuleIdentifier module)
            throws NoSuchSymbolException {

        if (!myModuleScopes.containsKey(module)) {
            throw new NoSuchSymbolException("" + module);
        }

        return myModuleScopes.get(module);
    }

    private static class ImportRequest {

        public final ModuleIdentifier sourceModule;
        public final ModuleIdentifier importedModule;

        public ImportRequest(ModuleIdentifier source, ModuleIdentifier imported) {

            sourceModule = source;
            importedModule = imported;
        }
    }
}
