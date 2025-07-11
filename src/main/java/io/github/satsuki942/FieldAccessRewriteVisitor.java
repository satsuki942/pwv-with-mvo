package io.github.satsuki942;

import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import io.github.satsuki942.symboltable.ClassInfo;
import io.github.satsuki942.symboltable.MethodInfo;
import io.github.satsuki942.symboltable.SymbolTable;
import io.github.satsuki942.util.AstUtil;

public class FieldAccessRewriteVisitor extends ModifierVisitor<SymbolTable> {

    private static final Pattern VERSIONED_CLASS_PATTERN = AstUtil.getVersionedClassPattern();
    private ClassInfo currentClassInfo;
    private MethodInfo currentMethodInfo;

    @Override
    public Node visit(ClassOrInterfaceDeclaration n, SymbolTable symbolTable) {
        this.currentClassInfo = symbolTable.lookupClass(n.getNameAsString());
        Node result = (Node) super.visit(n, symbolTable);
        this.currentClassInfo = null;
        return result;
    }

    @Override
    public Node visit(MethodDeclaration n, SymbolTable symbolTable) {
        if (this.currentClassInfo != null) {
            this.currentMethodInfo = findMethodInfoFor(n, this.currentClassInfo);
        }
        Node result = (Node) super.visit(n, symbolTable);
        this.currentMethodInfo = null;
        return result;
    }

    @Override
    public Node visit(AssignExpr n, SymbolTable symbolTable) {
        com.github.javaparser.ast.expr.Expression target = n.getTarget();

        if (target.isFieldAccessExpr()) {
            FieldAccessExpr fieldAccess = target.asFieldAccessExpr();
            String typeName = resolveCallerType(fieldAccess, symbolTable);

            if (typeName != null) {
                Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(typeName);
                String baseTypeName = matcher.matches() ? matcher.group(1) : typeName;

                ClassInfo classInfo = symbolTable.lookupClass(baseTypeName);

                if (classInfo != null && classInfo.isVersioned() && classInfo.getFields().containsKey(fieldAccess.getNameAsString())) {
                    String setterName = "__set_" + fieldAccess.getNameAsString();
                    MethodCallExpr setterCall = new MethodCallExpr(
                        fieldAccess.getScope(),
                        setterName,
                        new NodeList<>(n.getValue())
                    );
                    
                    return setterCall;
                }
            }
        }
        return (Node) super.visit(n, symbolTable);
    }

    @Override
    public Node visit(FieldAccessExpr n, SymbolTable symbolTable) {
        if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof AssignExpr &&
            ((AssignExpr) n.getParentNode().get()).getTarget() == n) {
            return (Node) super.visit(n, symbolTable);
        }

        String typeName = resolveCallerType(n, symbolTable);

        if (typeName != null) {
            Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(typeName);
            String baseTypeName = matcher.matches() ? matcher.group(1) : typeName;

            ClassInfo classInfo = symbolTable.lookupClass(baseTypeName);

            if (classInfo != null && classInfo.isVersioned() && classInfo.getFields().containsKey(n.getNameAsString())) {
                String getterName = "__get_" + n.getNameAsString();
                MethodCallExpr getterCall = new MethodCallExpr(
                    n.getScope(),
                    getterName
                );
                
                return getterCall;
            }
        }
        return (Node) super.visit(n, symbolTable);
    }

    // -- HELPER METHODS --
    private String resolveCallerType(FieldAccessExpr n, SymbolTable symbolTable) {
        com.github.javaparser.ast.expr.Expression scopeExpr = n.getScope();
        if (scopeExpr.isNameExpr()) {
            String varName = scopeExpr.asNameExpr().getNameAsString();
            if (this.currentMethodInfo != null) {
                return this.currentMethodInfo.getVariables().get(varName);
            }
        }
        return null;
    }

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
