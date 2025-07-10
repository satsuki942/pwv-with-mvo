package io.github.satsuki942.unifiedclassbuilder;
import io.github.satsuki942.util.AstUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

/**
 * Generates the public constructors on the unified class.
 * <p>
 * This class is responsible for replicating all unique constructors from the original
 * versioned classes, ensuring that the state-holding fields are initialized correctly
 * based on which constructor is called. It also handles the generation of a default
 * constructor if none are defined.
 */
public class ConstructorGenerator {
    private final ClassOrInterfaceDeclaration targetClass;
    private final List<CompilationUnit> versionAsts;

    public ConstructorGenerator(ClassOrInterfaceDeclaration targetClass, List<CompilationUnit> versionAsts) {
        this.targetClass = targetClass;
        this.versionAsts = versionAsts;
    }

    /**
     * Executes the generation of all public constructors.
     */
    public void generate() {
        // 1. Collect all constructors from all versioned classes
        Map<String, ConstructorDeclaration> constructorsBySignature = new HashMap<>();
        for (CompilationUnit cu : versionAsts) {
            cu.findAll(ConstructorDeclaration.class).forEach(ctor -> {
                constructorsBySignature.putIfAbsent(ctor.getSignature().asString(), ctor);
            });
        }

        // 2. When no constructors are found, create a default constructor
        if (constructorsBySignature.isEmpty()) {
            ConstructorDeclaration defaultCtor = new ConstructorDeclaration(
                new NodeList<>(new Modifier(Modifier.Keyword.PUBLIC)), 
                this.targetClass.getNameAsString()
            );
            constructorsBySignature.put(defaultCtor.getSignature().asString(), defaultCtor);
        }

        // 3. Generate public constructors corresponding to the collected constructors
        for (ConstructorDeclaration originalCtor : constructorsBySignature.values()) {
            generateVersionedConstructor(originalCtor);
        }
    }

    private void generateVersionedConstructor(ConstructorDeclaration originalCtor) {
        ConstructorDeclaration publicCtor = this.targetClass.addConstructor(Modifier.Keyword.PUBLIC);
        originalCtor.getParameters().forEach(p -> publicCtor.addParameter(p.clone()));
        
        BlockStmt body = new BlockStmt();
        String ctorOwnerVersion = getVersionForConstructor(originalCtor).toLowerCase();

        for (CompilationUnit cu : versionAsts) {
            String currentVersionSuffix = AstUtil.getVersionSuffix(cu).orElse("").toLowerCase();
            String implClassName = AstUtil.getVersionSuffix(cu).orElse("").toUpperCase() + "_Impl";
            String instanceName = currentVersionSuffix + "_instance";

            ObjectCreationExpr newExpr = new ObjectCreationExpr(null, new ClassOrInterfaceType(null,implClassName), new NodeList<>());

            if (currentVersionSuffix.equals(ctorOwnerVersion)) {
                originalCtor.getParameters().forEach(p -> newExpr.addArgument(p.getNameAsExpression()));
            }

            AssignExpr assignExpr = new AssignExpr(
                new FieldAccessExpr(new ThisExpr(), instanceName),
                newExpr,
                AssignExpr.Operator.ASSIGN
            );
            body.addStatement(new ExpressionStmt(assignExpr));
        }

        body.addStatement(String.format("this.currentState = this.%s_instance;", ctorOwnerVersion));
        publicCtor.setBody(body);
    }

    // -- HELPER METHODS --
    private String getVersionForConstructor(ConstructorDeclaration ctor) {
        for (CompilationUnit cu : versionAsts) {
            if (cu.findAll(ConstructorDeclaration.class).stream().anyMatch(c -> c.getSignature().equals(ctor.getSignature()))) {
                return AstUtil.getVersionSuffix(cu).orElse("v1");
            }
        }
        return "v1";
    }
}
