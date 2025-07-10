package io.github.satsuki942;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import io.github.satsuki942.symboltable.SymbolTable;
import io.github.satsuki942.unifiedclassbuilder.ConstructorGenerator;
import io.github.satsuki942.unifiedclassbuilder.MemberMerger;
import io.github.satsuki942.unifiedclassbuilder.StateInfrastructureGenerator;
import io.github.satsuki942.unifiedclassbuilder.StubMethodGenerator;

import java.util.List;

/**
 * Orchestrates the transformation of versioned classes into a single, unified class.
 * <p>
 * This class acts as a "director" that coordinates various builder classes,
 * each responsible for a specific part of the final AST generation.
 */
public class UnifiedClassBuilder {
    private final String baseName;
    private final List<CompilationUnit> versionAsts;
    private final SymbolTable symbolTable;
    private final ClassOrInterfaceDeclaration newCIDecl;
    private final CompilationUnit newCu;

    /**
     * Constructs a new UnifiedClassBuilder.
     *
     * @param baseName    The base name of the class to be unified (e.g., "Test").
     * @param versionAsts A list of CompilationUnits for each versioned class (e.g., ASTs for "Test__1__", "Test__2__").
     * @param symbolTable The symbol table containing information about all classes in the project.
     */
    public UnifiedClassBuilder(String baseName, List<CompilationUnit> versionAsts, SymbolTable symbolTable) {
        this.baseName = baseName;
        this.versionAsts = versionAsts;
        this.symbolTable = symbolTable;

        this.newCu = new CompilationUnit();
        this.versionAsts.stream().findFirst()
            .flatMap(CompilationUnit::getPackageDeclaration)
            .ifPresent(pd -> this.newCu.setPackageDeclaration(pd.clone()));
        this.newCIDecl = this.newCu.addClass(baseName).setPublic(true);
    }

    /**
     * Executes the full build process to generate the unified class AST.
     * <p>
     * This method orchestrates a multi-step process by invoking specialized builders in a specific order:
     * <ol>
     * <li>Generates the basic infrastructure for the State Pattern.</li>
     * <li>Merges members from versioned classes into their respective implementation inner classes.</li>
     * <li>Generates public constructors for the unified class.</li>
     * <li>Generates public stub methods that handle the dispatch logic.</li>
     * </ol>
     *
     * @return The {@link CompilationUnit} containing the newly generated, unified class.
     */
    public CompilationUnit build() {
        // 1. Generate the unified class structure
        new StateInfrastructureGenerator(this.newCIDecl, this.versionAsts).generate();

        // 2. Merge members from all versioned classes into the unified class
        new MemberMerger(this.newCIDecl, this.versionAsts).merge();

        // 3. Generate public stubs for methods
        new StubMethodGenerator(newCIDecl, symbolTable, baseName).generate();

        // 4. Generate public constructors for the unified class
        new ConstructorGenerator(this.newCIDecl, this.versionAsts).generate();

        return newCu;
    }
}