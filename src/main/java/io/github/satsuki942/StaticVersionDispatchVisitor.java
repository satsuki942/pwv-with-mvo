package io.github.satsuki942;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.NodeList;

import io.github.satsuki942.symboltable.ClassInfo;
import io.github.satsuki942.symboltable.MethodInfo;
import io.github.satsuki942.symboltable.SymbolTable;

public class StaticVersionDispatchVisitor extends ModifierVisitor<SymbolTable> {

    private static final Pattern VERSIONED_CLASS_PATTERN = Pattern.compile("(.+)__(\\d+)__$");
    private ClassInfo currentClassInfo;
    private MethodInfo currentMethodInfo;

    @Override
    public Node visit(ClassOrInterfaceDeclaration ClassInterfaceDecl, SymbolTable symbolTable) {
        String className = ClassInterfaceDecl.getNameAsString();
        this.currentClassInfo = symbolTable.lookupClass(className);
        Node result = (Node) super.visit(ClassInterfaceDecl, symbolTable);
        this.currentClassInfo = null;
        return result;
    }

    @Override
    public Node visit(MethodDeclaration MethodDecl, SymbolTable symbolTable) {
        if (this.currentClassInfo != null) {
            this.currentMethodInfo = findMethodInfoFor(MethodDecl, this.currentClassInfo);
        }
        Node result = (Node) super.visit(MethodDecl, symbolTable);
        this.currentMethodInfo = null;
        return result;
    }

    @Override
    public Node visit(com.github.javaparser.ast.stmt.ExpressionStmt exprStmt, SymbolTable symbolTable){
        if (!exprStmt.getExpression().isMethodCallExpr()) {
            // If the expression is not a MethodCallExpr, we cannot resolve it
            return (Node) super.visit(exprStmt, symbolTable);
        }

        MethodCallExpr MethodCallExpr = exprStmt.getExpression().asMethodCallExpr();

        if (this.currentMethodInfo == null) {
            return (Node) super.visit(exprStmt, symbolTable);
        }

        com.github.javaparser.ast.expr.Expression scopeExpr = MethodCallExpr.getScope().orElse(null);

        if (scopeExpr == null || !scopeExpr.isNameExpr()) {
            // If there's no scope or it's not a NameExpr, we can't resolve it
            return (Node) super.visit(exprStmt, symbolTable);
        }

        if (this.currentClassInfo == null) {
            // If we are not in a class context, we cannot resolve the method call to a versioned method
            return (Node) super.visit(exprStmt, symbolTable); 
        }
        
        String varName = scopeExpr.asNameExpr().getNameAsString();
        String typeName = currentMethodInfo.getVariables().get(varName);

        if (typeName == null) {
            // If the type name is not found in the current method's variables, we cannot resolve it
            return (Node) super.visit(exprStmt, symbolTable);
        }

        ClassInfo classInfo = symbolTable.lookupClass(typeName);

        if(classInfo == null || !classInfo.isVersioned()) {
            // If the class is not found or is not versioned, we cannot resolve the method call to a versioned method
            return (Node) super.visit(exprStmt, symbolTable);
        }

        List<MethodInfo> candidates = classInfo.getMethods().get(MethodCallExpr.getNameAsString());

        if (candidates == null || candidates.isEmpty()) {
            // If no methods are found with the given name, we cannot resolve it
            return (Node) super.visit(exprStmt, symbolTable);
        }

        List<String> argumentTypes = resolveArgumentTypes(MethodCallExpr.getArguments(), symbolTable);

        List<MethodInfo> matchingMethods = candidates.stream()
            .filter(m -> m.getParameterTypes().equals(argumentTypes))
            .collect(Collectors.toList());

        if (matchingMethods.size() == 1) {
            MethodInfo targetMethod = matchingMethods.get(0);
            int versionNumber = Integer.parseInt(targetMethod.getVersion());

            // Create a new block to switch to the correct version including the original method call expression
            BlockStmt newBlock = new BlockStmt();
            MethodCallExpr switchCall = new MethodCallExpr(
                MethodCallExpr.getScope().get(), // obj
                "__switchToVersion",
                new NodeList<>(new IntegerLiteralExpr(String.valueOf(versionNumber)))
            );
            newBlock.addStatement(new ExpressionStmt(switchCall));
            newBlock.addStatement(exprStmt.clone());

            return newBlock;
        }

        return (Node) super.visit(exprStmt, symbolTable);
    }

    @Override
    public Node visit(ClassOrInterfaceType classInterfaceType, SymbolTable symbolTable) {
        String typeName = classInterfaceType.getNameAsString();
        Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(typeName);
        if (matcher.matches()) {
            String baseName = matcher.group(1);
            classInterfaceType.setName(baseName);
        }
        return (Node) super.visit(classInterfaceType, symbolTable);
    }

    @Override
    public Node visit(ObjectCreationExpr objCreationExpr, SymbolTable symbolTable) {
        objCreationExpr.setType((ClassOrInterfaceType) visit(objCreationExpr.getType(), symbolTable));
        return (Node) super.visit(objCreationExpr, symbolTable);
    }

    // HELPERS
    private List<String> resolveArgumentTypes(NodeList<com.github.javaparser.ast.expr.Expression> arguments, SymbolTable symbolTable) {
        if (arguments.isEmpty()) return new ArrayList<>();
        List<String> argTypes = new ArrayList<>();
        for (com.github.javaparser.ast.expr.Expression argExpr : arguments) {
            if (argExpr.isStringLiteralExpr()) {
                argTypes.add("String");
            } else if (argExpr.isIntegerLiteralExpr()) {
                argTypes.add("int");
            }
            // ... (other literal types) ...
            else if (argExpr.isNameExpr()) {
                String varName = argExpr.asNameExpr().getNameAsString();
                String type = currentMethodInfo.getVariables().get(varName);
                argTypes.add(type != null ? type : "UNKNOWN_TYPE");
            } else {
                argTypes.add("UNKNOWN_TYPE");
            }
        }
        return argTypes;
    }

    // Helper to find the correct MethodInfo from a MethodDeclaration node
    private MethodInfo findMethodInfoFor(MethodDeclaration n, ClassInfo classInfo) {
        List<String> paramTypes = n.getParameters().stream()
                                    .map(p -> p.getType().asString())
                                    .collect(Collectors.toList());
                                    
        List<MethodInfo> candidates = classInfo.getMethods().get(n.getNameAsString());
        if (candidates == null) return null;

        return candidates.stream()
                         .filter(m -> m.getParameterTypes().equals(paramTypes))
                         .findFirst()
                         .orElse(null);
    }
}