package io.github.satsuki942;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import io.github.satsuki942.symboltable.SymbolTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MyLangTransformer {

    // regex pattern to find versioned classes: e.g., "Test__1__", "Test__2__"
    private static final Pattern VERSIONED_CLASS_PATTERN = Pattern.compile("(.+)__(\\d+)__$");

    public List<CompilationUnit> transform(List<CompilationUnit> MyLangASTs) {
        Logger.debugLog("Starting transformation...");


        // STEP1: Generate a symbol table
        SymbolTable symbolTable = new SymbolTable();
        SymbolTableBuilderVisitor analysisVisitor = new SymbolTableBuilderVisitor();
        for (CompilationUnit cu : MyLangASTs) {
            analysisVisitor.visit(cu, symbolTable);
        }

        Logger.successLog("Generated a symbol table");


        // STEP2: Dispatch versions of method calls
        StaticVersionDispatchVisitor transformVisitor = new StaticVersionDispatchVisitor();
        List<CompilationUnit> transformedAsts = new ArrayList<>();
        for (CompilationUnit cu : MyLangASTs) {
            Node transformedNode = (Node) transformVisitor.visit(cu, symbolTable);
            if (transformedNode instanceof CompilationUnit) {
                transformedAsts.add((CompilationUnit) transformedNode);
            }
        }

        Logger.successLog("Dispatched versions of method calls");
        // Print the transformed ASTs for debugging
        // transformedAsts.forEach(cu -> {
        //     Logger.debugLog("Transformed AST: " + cu.getPrimaryTypeName().orElse("Unnamed"));
        //     Logger.debugLog(cu.toString());
        // });


        // STEP3: Merge versioned classes
        // Separate normal classes and versioned classes
        List<CompilationUnit> normalClassesASTs = MyLangASTs.stream()
                .filter(cu -> !isVersioned(cu))
                .collect(Collectors.toList());
        Map<String, List<CompilationUnit>> versionedClassMap = MyLangASTs.stream()
                .filter(this::isVersioned)
                .collect(Collectors.groupingBy(this::getBaseName));

        // Create versioned class definitions (= transformed ASTs)
        List<CompilationUnit> transformedASTs = versionedClassMap.entrySet().stream()
                .map(entry -> {
                    UnifiedClassBuilder builder = new UnifiedClassBuilder(entry.getKey(), entry.getValue(), symbolTable);
                    return builder.build();
                })
                .collect(Collectors.toList());
        
        transformedASTs.addAll(normalClassesASTs);

        Logger.successLog("Merged versioned classes");

        Logger.successLog("Whole transformation completed");
        return transformedASTs;
    }

    // check if the class is versioned
    private boolean isVersioned(CompilationUnit cu) {
        return cu.getPrimaryTypeName()
                 .map(name -> VERSIONED_CLASS_PATTERN.matcher(name).matches())
                 .orElse(false);
    }

    // extract base name from versioned class name
    private String getBaseName(CompilationUnit cu) {
        return cu.getPrimaryTypeName()
                 .map(name -> {
                     Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(name);
                     return matcher.matches() ? matcher.group(1) : name;
                 })
                 .orElse("");
    }
}