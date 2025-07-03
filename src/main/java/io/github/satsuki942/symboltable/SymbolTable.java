package io.github.satsuki942.symboltable;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    private final Map<String, ClassInfo> classTable = new HashMap<>();

    // Class information
    public void addClass(ClassInfo classInfo) {
        classTable.put(classInfo.getBaseName(), classInfo);
    }

    public ClassInfo lookupClass(String baseName) {
        return classTable.get(baseName);
    }

    // For Debugging: Print the symbol table contents
    public void print() {
        System.out.println("Symbol Table:");

        classTable.forEach((name, info) -> {
            System.out.println("    Class: " + name + " (Versioned: " + info.isVersioned() + ")");
            info.getMethods().forEach((methodName, overloads) -> {
                overloads.forEach(methodInfo -> {
                    System.out.println(String.format("      - Method: %s, Version: %s, Params: %s",
                            methodInfo.getName(), methodInfo.getVersion(), methodInfo.getParameterTypes()));
                    // メソッド内の変数情報を出力
                    if (!methodInfo.getVariables().isEmpty()) {
                        methodInfo.getVariables().forEach((varName, typeName) -> {
                            System.out.println("        - Var: " + varName + ", Type: " + typeName);
                        });
                    }
                });
            });
        });
        System.out.println();
    }
}
