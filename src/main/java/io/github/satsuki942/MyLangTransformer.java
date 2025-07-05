package io.github.satsuki942;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.Node;

import io.github.satsuki942.symboltable.SymbolTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MyLangTransformer {

    // regex patter to find versioned classes: e.g., "Test__1__", "Test__2__"
    private static final Pattern VERSIONED_CLASS_PATTERN = Pattern.compile("(.+)__(\\d+)__$");

    public List<CompilationUnit> transform(List<CompilationUnit> MyLangASTs) {
        System.out.println("Starting transformation...");


        // STEP1: Generate a symbol table
        SymbolTable symbolTable = new SymbolTable();
        SymbolTableBuilderVisitor analysisVisitor = new SymbolTableBuilderVisitor();
        for (CompilationUnit cu : MyLangASTs) {
            analysisVisitor.visit(cu, symbolTable);
        }

        System.out.println("[SUCCESS] Generated a symbol table");


        // STEP2: Dispatch versions of method calls
        StaticVersionDispatchVisitor transformVisitor = new StaticVersionDispatchVisitor();
        List<CompilationUnit> transformedAsts = new ArrayList<>();
        for (CompilationUnit cu : MyLangASTs) {
            Node transformedNode = (Node) transformVisitor.visit(cu, symbolTable);
            if (transformedNode instanceof CompilationUnit) {
                transformedAsts.add((CompilationUnit) transformedNode);
            }
        }

        System.out.println("[SUCCESS] Dispatched versions of method calls");
        System.out.println("    AST display is omitted");
        // Print the transformed ASTs for debugging
        // transformedAsts.forEach(cu -> {
        //     System.out.println("Transformed AST: " + cu.getPrimaryTypeName().orElse("Unnamed"));
        //     System.out.println(cu.toString());
        // });


        // STEP3: Merge versioned classes
        List<CompilationUnit> normalClassesASTs = MyLangASTs.stream()
                .filter(cu -> !isVersioned(cu))
                .collect(Collectors.toList());
        Map<String, List<CompilationUnit>> versionedClassMap = MyLangASTs.stream()
                .filter(this::isVersioned)
                .collect(Collectors.groupingBy(this::getBaseName));

        // List<CompilationUnit> transformedASTs = versionedClassMap.entrySet().stream()
        //         .map(entry -> mergeClasses(entry.getKey(), entry.getValue()))
        //         .collect(Collectors.toList());

        // ToDo: リファクタリング後に使う。上のmergeClassesを使った実装は消す。
        List<CompilationUnit> transformedASTs = versionedClassMap.entrySet().stream()
                .map(entry -> {
                    UnifiedClassBuilder builder = new UnifiedClassBuilder(entry.getKey(), entry.getValue(), symbolTable);
                    return builder.build();
                })
                .collect(Collectors.toList());
        
        transformedASTs.addAll(normalClassesASTs);

        System.out.println("[SUCCESS] Merged versioned classes");

        System.out.println("[SUCCESS] Whole transformation completed");
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

    // merge multiple versioned ASTs into a single unified class AST
    private CompilationUnit mergeClasses(String baseName, List<CompilationUnit> VersionedClassASTs) {

        // generate a new CompilationUnit for the unified class
        CompilationUnit mergedClassUnit = new CompilationUnit();
        VersionedClassASTs.get(0).getPackageDeclaration().ifPresent(pd -> {
            mergedClassUnit.setPackageDeclaration(pd.clone());
        });
        ClassOrInterfaceDeclaration newClass = mergedClassUnit.addClass(baseName).setPublic(true);

        for (CompilationUnit versionedClassAST : VersionedClassASTs) {
            // get the version suffix from the class name
            String versionSuffix = getVersionSuffix(versionedClassAST).orElse(null);
            if (versionSuffix == null) continue;

            // get the declaration node of the versioned class
            versionedClassAST.getPrimaryType().ifPresent(typeDecl -> {
                if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                    ClassOrInterfaceDeclaration versionedClass = (ClassOrInterfaceDeclaration) typeDecl;

                    for (BodyDeclaration<?> member : versionedClass.getMembers()) {
                        BodyDeclaration<?> clonedMember = member.clone();

                        //methods
                        if (clonedMember instanceof MethodDeclaration) {
                            MethodDeclaration method = (MethodDeclaration) clonedMember;
                            method.setName(method.getNameAsString() + "__" + versionSuffix + "__");
                            newClass.addMember(method);

                        // fields
                        } else if (clonedMember instanceof FieldDeclaration) {
                            FieldDeclaration field = (FieldDeclaration) clonedMember;
                            for (VariableDeclarator var : field.getVariables()) {
                                var.setName(var.getNameAsString() + "__" + versionSuffix + "__");
                            }
                            newClass.addMember(field);
                        
                        // internal classes or interfaces
                        } else if (clonedMember instanceof ClassOrInterfaceDeclaration) {
                            ClassOrInterfaceDeclaration innerClass = (ClassOrInterfaceDeclaration) clonedMember;
                            innerClass.setName(innerClass.getNameAsString() + "__" + versionSuffix + "__");
                            newClass.addMember(innerClass);
                        }
                    }
                }
            });
        }
        return mergedClassUnit;
    }

    private Optional<String> getVersionSuffix(CompilationUnit cu) {
        return getGroupName(cu, 2);
    }

    private Optional<String> getGroupName(CompilationUnit cu, int group) {
        return cu.getPrimaryTypeName()
                 .map(name -> {
                     Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(name);
                     return matcher.matches() ? matcher.group(group) : null;
                 });
    }
}