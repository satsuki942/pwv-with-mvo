package io.github.satsuki942;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.satsuki942.symboltable.ClassInfo;
import io.github.satsuki942.symboltable.MethodInfo;
import io.github.satsuki942.symboltable.SymbolTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.github.javaparser.ast.body.Parameter;

public class SymbolTableBuilderVisitor extends VoidVisitorAdapter<SymbolTable> {

    private static final Pattern VERSIONED_CLASS_PATTERN = Pattern.compile("(.+)__(\\d+)__$");

    @Override
    public void visit(ClassOrInterfaceDeclaration ClassInterfaceDecl, SymbolTable symbolTable) {
        String className = ClassInterfaceDecl.getNameAsString();
        Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(className);

        String baseName;
        String version;
        boolean isVersioned;

        if (matcher.matches()) {
            baseName = matcher.group(1); // e.g., "Test" from "Test__1__"
            isVersioned = true;
            version = matcher.group(2); // e.g., "1" from "Test__1__"
        } else {
            baseName = className;
            isVersioned = false;
            version = "normal"; // Default version for non-versioned classes
        }

        // Prepare a method map depending on the class name
        ClassInfo existingClassInfo = symbolTable.lookupClass(baseName);
        Map<String, List<MethodInfo>> methodsMap = (existingClassInfo != null) ? existingClassInfo.getMethods() : new HashMap<>();

        // --- Collect all method information in the class ---
        for (MethodDeclaration method : ClassInterfaceDecl.getMethods()) {

            // Collect parameter types as a list of strings
            String methodName = method.getNameAsString();
            String returnType = method.getType().asString();
            List<String> paramTypes = method.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.toList());
            Map<String, String> variablesInMethod = new HashMap<>();
            for (Parameter parameter : method.getParameters()) {
                variablesInMethod.put(parameter.getNameAsString(), parameter.getTypeAsString());
            } 
            for (VariableDeclarator varDeclarator : method.findAll(VariableDeclarator.class)) {
                variablesInMethod.put(varDeclarator.getNameAsString(), varDeclarator.getTypeAsString());
            }

            MethodInfo methodInfo = new MethodInfo(
                methodName,
                returnType,
                paramTypes,
                version,
                variablesInMethod
            );
            
            // Considering method overloading, store methods in a list by name
            methodsMap.computeIfAbsent(method.getNameAsString(), k -> new ArrayList<>()).add(methodInfo);
        }

        // --- Generating ClassInfo, Put it into SymbolTable ---
        ClassInfo classInfo = new ClassInfo(baseName, isVersioned, methodsMap);
        symbolTable.addClass(classInfo);

        // Call the parent class's visit method to allow exploration of nested classes, etc.
        super.visit(ClassInterfaceDecl, symbolTable);
    }
}
