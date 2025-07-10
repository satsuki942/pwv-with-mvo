package io.github.satsuki942.unifiedclassbuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;

import io.github.satsuki942.symboltable.ClassInfo;
import io.github.satsuki942.symboltable.MethodInfo;
import io.github.satsuki942.symboltable.SymbolTable;

/**
 * Generates the public-facing "stub" methods on the unified class.
 * <p>
 * It analyzes the symbol table to determine whether a method is ambiguous (shared across versions)
 * or unambiguous (exists in only one version) and creates the appropriate dispatch logic.
 */
public class StubMethodGenerator {
    private final ClassOrInterfaceDeclaration targetClass;
    private final SymbolTable symbolTable;
    private final String baseName;

    public StubMethodGenerator(ClassOrInterfaceDeclaration targetClass, SymbolTable symbolTable, String baseName) {
        this.targetClass = targetClass;
        this.symbolTable = symbolTable;
        this.baseName = baseName;
    }

    /**
     * Executes the generation of all public stub methods.
     */
    public void generate() {
        ClassInfo classInfo = symbolTable.lookupClass(baseName);
        if (classInfo == null) return;

        ClassOrInterfaceDeclaration behaviorInterface = findBehaviorInterface();
        if (behaviorInterface == null) return;

        // 1. Group all methods by their signature
        Map<String, List<MethodInfo>> methodsBySignature = classInfo.getMethods().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                        method -> method.getName() + method.getParameterTypes().toString()
                ));

        // 2. Generate stubs for each group
        for (List<MethodInfo> overloads : methodsBySignature.values()) {
            generateStubFor(overloads, behaviorInterface);
        }
    }

    private void generateStubFor(List<MethodInfo> overloads, ClassOrInterfaceDeclaration behaviorInterface) {
        MethodInfo firstOverload = overloads.get(0);
        MethodDeclaration stub = createMethodStubSignature(firstOverload);
        MethodCallExpr callExpr;

        if (overloads.size() > 1) { // Ambiguous method defined in along multiple versions
            behaviorInterface.addMember(createMethodStubSignature(firstOverload).setBody(null));
            callExpr = new MethodCallExpr(
                    new FieldAccessExpr(new ThisExpr(), "currentState"),
                    stub.getNameAsString()
            );
        } else { // Unambiguous method defined in a single version
            String versionSuffix = firstOverload.getVersion().toLowerCase();
            callExpr = new MethodCallExpr(
                    new FieldAccessExpr(new ThisExpr(), "v" +versionSuffix + "_instance"),
                    stub.getNameAsString()
            );
        }

        stub.getParameters().forEach(p -> callExpr.addArgument(p.getNameAsExpression()));

        if (firstOverload.getReturnType().equals("void")) {
            stub.setBody(new BlockStmt().addStatement(callExpr));
        } else {
            stub.setBody(new BlockStmt().addStatement(new ReturnStmt(callExpr)));
        }
        
        this.targetClass.addMember(stub);
    }

    // -- HELPER METHODS --
    private ClassOrInterfaceDeclaration findBehaviorInterface() {
        for (BodyDeclaration<?> member : this.targetClass.getMembers()) {
            if (member.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cid = member.asClassOrInterfaceDeclaration();
                if (cid.isInterface() && "IVersionBehavior".equals(cid.getNameAsString())) {
                    return cid;
                }
            }
        }
        System.err.println("Error: Could not find IVersionBehavior interface.");
        return null;
    }

    private MethodDeclaration createMethodStubSignature(MethodInfo methodInfo) {
        MethodDeclaration method = new MethodDeclaration();
        method.setName(methodInfo.getName());
        method.setType(methodInfo.getReturnType());
        method.setModifiers(new NodeList<>(new Modifier(Modifier.Keyword.PUBLIC)));
        for (int i = 0; i < methodInfo.getParameterTypes().size(); i++) {
            method.addParameter(methodInfo.getParameterTypes().get(i), "arg" + i);
        }
        return method;
    }
}
