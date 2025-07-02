package io.github.satsuki942;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

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

        // STEP1: extract normal classes (not versioned)
        List<CompilationUnit> normalClassesASTs = MyLangASTs.stream()
                .filter(cu -> !isVersioned(cu))
                .collect(Collectors.toList());

        // STEP2: group versioned classes by their base name
        Map<String, List<CompilationUnit>> versionedClassMap = MyLangASTs.stream()
                .filter(this::isVersioned)
                .collect(Collectors.groupingBy(this::getBaseName));

        System.out.println("Found " + versionedClassMap.size() + " versioned class group(s).");

        // STEP3: merge ASTs within each group into a single unified class
        List<CompilationUnit> transformedASTs = versionedClassMap.entrySet().stream()
                .map(entry -> mergeClasses(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // STEP4: combine unified classes with normal classes to form the final list
        transformedASTs.addAll(normalClassesASTs);

        System.out.println("Transformation complete.");
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
        System.out.println("Merging class: " + baseName);

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