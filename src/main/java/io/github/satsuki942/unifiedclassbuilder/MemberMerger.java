package io.github.satsuki942.unifiedclassbuilder;
import io.github.satsuki942.util.AstUtil;

import java.util.List;
import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;

/**
 * Merges members (fields, methods, constructors) from the original versioned classes
 * into their corresponding inner implementation classes (e.g., V1_Impl, V2_Impl).
 */
public class MemberMerger {
    private final ClassOrInterfaceDeclaration targetClass;
    private final List<CompilationUnit> versionAsts;

    
    public MemberMerger(ClassOrInterfaceDeclaration targetClass, List<CompilationUnit> versionAsts) {
        this.targetClass = targetClass;
        this.versionAsts = versionAsts;
    }

    /**
     * Executes the merging process.
     */
    public void merge() {
        for (CompilationUnit versionCu : versionAsts) {
            String versionSuffix = AstUtil.getVersionSuffix(versionCu).orElse("").toUpperCase();
            if (versionSuffix.isEmpty()) continue;
            
            String implClassName = versionSuffix + "_Impl";

            // Find the corresponding inner implementation class (e.g., V1_Impl)
            Optional<ClassOrInterfaceDeclaration> implClassOpt = findInnerClass(implClassName);
            if (implClassOpt.isEmpty()) {
                System.err.println("Error: Could not find implementation class: " + implClassName);
                continue;
            }
            ClassOrInterfaceDeclaration implClass = implClassOpt.get();

            // Copy members from the original version class
            versionCu.getPrimaryType().ifPresent(type -> {
                type.getMembers().forEach(member -> {
                    if (member.isConstructorDeclaration()) {
                        // Constructors: rename to match the implementation class
                        ConstructorDeclaration constructor = member.asConstructorDeclaration().clone();
                        constructor.setName(implClassName);
                        implClass.addMember(constructor);
                    } else {
                        // Others: copied as is
                        implClass.addMember(member.clone());
                    }
                });
            });

            // Injecting the default constructor, if it doesn't exist
            boolean hasDefaultConstructor = implClass.getConstructors().stream()
                .anyMatch(ctor -> ctor.isPublic() && ctor.getParameters().isEmpty());
            if (!hasDefaultConstructor) {
                implClass.addConstructor(com.github.javaparser.ast.Modifier.Keyword.PUBLIC);
            }
        }
    }

    // -- HELPER METHODS --
    private Optional<ClassOrInterfaceDeclaration> findInnerClass(String name) {
        for (BodyDeclaration<?> member : this.targetClass.getMembers()) {
            if (member.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cid = member.asClassOrInterfaceDeclaration();
                if (!cid.isInterface() && name.equals(cid.getNameAsString())) {
                    return Optional.of(cid);
                }
            }
        }
        return Optional.empty();
    }
}
