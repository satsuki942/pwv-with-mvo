package io.github.satsuki942;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import io.github.satsuki942.symboltable.SymbolTable;
import io.github.satsuki942.util.Logger;
import io.github.satsuki942.util.AstUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MyLangTransformer {

    private static final Pattern VERSIONED_CLASS_PATTERN = AstUtil.getVersionedClassPattern();

    public List<CompilationUnit> transform(List<CompilationUnit> MyLangASTs) {
        Logger.debugLog("Starting transformation...");


        // STEP1: Generate a symbol table
        SymbolTable symbolTable = new SymbolTable();
        SymbolTableBuilderVisitor analysisVisitor = new SymbolTableBuilderVisitor();
        for (CompilationUnit cu : MyLangASTs) {
            analysisVisitor.visit(cu, symbolTable);
        }

        Logger.successLog("Generated a symbol table");


        // STEP2: Dispatch versions of method calls & Rewrite field accesses
        List<CompilationUnit> tempAsts = new ArrayList<>();
        StaticVersionDispatchVisitor transformVisitor = new StaticVersionDispatchVisitor();
        for (CompilationUnit cu : MyLangASTs) {
            Node transformedNode = (Node) transformVisitor.visit(cu, symbolTable);
            if (transformedNode instanceof CompilationUnit) {
                tempAsts.add((CompilationUnit) transformedNode);
            }
        }

        FieldAccessRewriteVisitor fieldVisitor = new FieldAccessRewriteVisitor();
        List<CompilationUnit> transformedAsts = new ArrayList<>(); // 最終的な変換結果を格納するリスト
        for (CompilationUnit cu : tempAsts) { // ← tempAsts (変更後のリスト) を走査
            Node finalNode = (Node) fieldVisitor.visit(cu, symbolTable);
            if (finalNode != null) { // フィールド変換ビジターはクラスを削除する場合があるのでnullチェック
                transformedAsts.add((CompilationUnit) finalNode);
            }
        }

        Logger.successLog("Dispatched versions of method calls & Rewrote field accesses");
        // Print the transformed ASTs for debugging
        // transformedAsts.forEach(cu -> {
        //     Logger.debugLog("Transformed AST: " + cu.getPrimaryTypeName().orElse("Unnamed"));
        //     Logger.debugLog(cu.toString());
        // });


        // STEP3: Merge versioned classes
        // Separate normal classes and versioned classes
        List<CompilationUnit> normalClassesASTs = MyLangASTs.stream()
                .filter(cu -> !AstUtil.isVersioned(cu))
                .collect(Collectors.toList());
        Map<String, List<CompilationUnit>> versionedClassMap = MyLangASTs.stream()
                .filter(AstUtil::isVersioned)
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

    // -- HELPER METHODS --
    private String getBaseName(CompilationUnit cu) {
        return cu.getPrimaryTypeName()
                 .map(name -> {
                     Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(name);
                     return matcher.matches() ? matcher.group(1) : name;
                 })
                 .orElse("");
    }
}