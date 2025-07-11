package io.github.satsuki942.unifiedclassbuilder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.Modifier;

import io.github.satsuki942.symboltable.FieldInfo;
import io.github.satsuki942.symboltable.SymbolTable;

public class GetterSetterGenerator {
    private final ClassOrInterfaceDeclaration targetClass;
    private final SymbolTable symbolTable;
    private final String baseName;

    public GetterSetterGenerator(ClassOrInterfaceDeclaration targetClass, SymbolTable symbolTable, String baseName) {
        this.targetClass = targetClass;
        this.symbolTable = symbolTable;
        this.baseName = baseName;
    }

    public void generate() {
        var classInfo = symbolTable.lookupClass(baseName);
        if (classInfo == null || !classInfo.isVersioned()) {
            return;
        }

        // Collect all public fields
        Map<String, FieldInfo> publicFields = new HashMap<>();
        classInfo.getFields().values().stream()
            .flatMap(list -> list.stream())
            .forEach(fieldInfo -> publicFields.putIfAbsent(fieldInfo.getName(), fieldInfo));
        
        for (FieldInfo field : publicFields.values()) {
            createGetterFor(field);
            createSetterFor(field);
        }
    }

    private void createGetterFor(FieldInfo field) {
        String methodName = "__get_" + field.getName();
        String instanceName = "v" + field.getVersion().toLowerCase() + "_instance";

        MethodDeclaration getter = targetClass.addMethod(methodName, Modifier.Keyword.PUBLIC)
            .setType(field.getType());

        FieldAccessExpr fieldAccess = new FieldAccessExpr(
            new FieldAccessExpr(new ThisExpr(), instanceName), 
            field.getName()
        );
        
        BlockStmt body = new BlockStmt();
        int versionNumber = Integer.parseInt(field.getVersion().replace("v", ""));
        body.addStatement(new MethodCallExpr(new NameExpr("this"), "__switchToVersion", new NodeList<>(new IntegerLiteralExpr(String.valueOf(versionNumber)))));
        body.addStatement(new ReturnStmt(fieldAccess));
        getter.setBody(body);
    }

    private void createSetterFor(FieldInfo field) {
        String methodName = "__set_" + field.getName();
        String instanceName = "v" + field.getVersion().toLowerCase() + "_instance";
        
        MethodDeclaration setter = targetClass.addMethod(methodName, Modifier.Keyword.PUBLIC)
            .setType("void")
            .addParameter(field.getType(), "value");

        AssignExpr assignment = new AssignExpr(
            new FieldAccessExpr(new FieldAccessExpr(new ThisExpr(), instanceName), field.getName()),
            new NameExpr("value"),
            AssignExpr.Operator.ASSIGN
        );
        
        // メソッド本体: this.__switchToVersion(1); this.v1_instance.x = value;
        BlockStmt body = new BlockStmt();
        int versionNumber = Integer.parseInt(field.getVersion().replace("v", ""));
        body.addStatement(new MethodCallExpr(new NameExpr("this"), "__switchToVersion", new NodeList<>(new IntegerLiteralExpr(String.valueOf(versionNumber)))));
        body.addStatement(assignment);
        setter.setBody(body);
    }
}
