package io.github.satsuki942;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;

import io.github.satsuki942.symboltable.ClassInfo;
import io.github.satsuki942.symboltable.MethodInfo;
import io.github.satsuki942.symboltable.SymbolTable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class UnifiedClassBuilder {
    private final String baseName;
    private final List<CompilationUnit> versionAsts;
    private final SymbolTable symbolTable;
    private final ClassOrInterfaceDeclaration newCIDecl;
    private final CompilationUnit newCu;

    public UnifiedClassBuilder(String baseName, List<CompilationUnit> versionAsts, SymbolTable symbolTable) {
        this.baseName = baseName;
        this.versionAsts = versionAsts;
        this.symbolTable = symbolTable;

        this.newCu = new CompilationUnit();
        this.versionAsts.stream().findFirst()
            .flatMap(CompilationUnit::getPackageDeclaration)
            .ifPresent(pd -> this.newCu.setPackageDeclaration(pd.clone()));
        this.newCIDecl = this.newCu.addClass(baseName).setPublic(true);
    }

    public CompilationUnit build() {
        // 2. Stateパターンに必要なインフラ（内部クラス、フィールド等）を生成
        generateStateInfrastructure();

        // 3. 全てのバージョンからメソッド等をマージし、名前修飾する
        mergeMembersIntoImplClasses();

        // 4. 公開スタブメソッドを生成する
        generatePublicStubs();

        return newCu;
    }
    
    // 
    private void generateStateInfrastructure() {
        // Generating IVersionBehavior Interface

        ClassOrInterfaceDeclaration behaviorInterface = new ClassOrInterfaceDeclaration();
        behaviorInterface.setInterface(true);
        behaviorInterface.setName("IVersionBehavior");
        behaviorInterface.setPrivate(true);
        this.newCIDecl.addMember(behaviorInterface);

        // Generating Versioned Internal Clsses
        // e.g. V1_Impl, V2_Impl
        versionAsts.forEach(cu -> {
            String versionSuffix = getVersionSuffix(cu).orElse("").toUpperCase();
            ClassOrInterfaceDeclaration implClass = new ClassOrInterfaceDeclaration();
            
            implClass.setName(versionSuffix + "_Impl");
            implClass.setPrivate(true);
            implClass.setStatic(true);
            
            implClass.addImplementedType(behaviorInterface.getNameAsString());
            this.newCIDecl.addMember(implClass);
        });

        // Generating Fields holding current version of instance
        for (CompilationUnit versionCu : versionAsts) {
            String versionSuffix = getVersionSuffix(versionCu).orElse("").toUpperCase();
            this.newCIDecl.addField(versionSuffix + "_Impl", versionSuffix.toLowerCase() + "_instance")
                    .setPrivate(true).setFinal(true);
        }
        this.newCIDecl.addField("IVersionBehavior", "currentState").setPrivate(true);
        this.newCIDecl.addMember(createSwitchToVersionMethod());

        // Modifying the Constructor to initialize fields)
        ConstructorDeclaration constructor = this.newCIDecl.addConstructor().setPublic(true);
        BlockStmt constructorBody = new BlockStmt();
        for (CompilationUnit versionCu : versionAsts) {
            String versionSuffix = getVersionSuffix(versionCu).orElse("").toUpperCase();
            constructorBody.addStatement(String.format("this.%s_instance = new %s_Impl();", 
                versionSuffix.toLowerCase(), versionSuffix));
        }
        String defaultVersionSuffix = getVersionSuffix(versionAsts.get(0)).orElse("v1").toLowerCase();
        constructorBody.addStatement(String.format("this.currentState = this.%s_instance;", defaultVersionSuffix));
        constructor.setBody(constructorBody);
    }

    private void mergeMembersIntoImplClasses() {
        for (CompilationUnit versionCu : versionAsts) {
            String versionSuffix = getVersionSuffix(versionCu).orElse("").toUpperCase();
            if (versionSuffix.isEmpty()) continue;
            String implClassName = versionSuffix + "_Impl";

            // Found the internal class definition (e.g., V1_Impl, V2_Impl)
            ClassOrInterfaceDeclaration foundImplClass = null;
            for (BodyDeclaration<?> member : this.newCIDecl.getMembers()) {
                if (member.isClassOrInterfaceDeclaration()) {
                    ClassOrInterfaceDeclaration cid = member.asClassOrInterfaceDeclaration();
                    if (!cid.isInterface() && implClassName.equals(cid.getNameAsString())) {
                        foundImplClass = cid;
                        break;
                    }
                }
            }
            if (foundImplClass == null) continue;

            // Merge the members from the versioned CompilationUnit into the found implementation class
            final ClassOrInterfaceDeclaration implClass = foundImplClass;
            versionCu.getPrimaryType().ifPresent(type -> {
                type.getMembers().forEach(member -> implClass.addMember(member.clone()));
            });
        }
    }


    private void generatePublicStubs() {
        ClassInfo classInfo = symbolTable.lookupClass(baseName);
        if (classInfo == null) return;
        
        // Search for the IVersionBehavior interface in the generated class
        ClassOrInterfaceDeclaration behaviorInterface = null;
        for (BodyDeclaration<?> member : this.newCIDecl.getMembers()) {
            if (member.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cid = member.asClassOrInterfaceDeclaration();
                if (cid.isInterface() && "IVersionBehavior".equals(cid.getNameAsString())) {
                    behaviorInterface = cid;
                    break;
                }
            }
        }

        if (behaviorInterface == null) {
            Logger.errorLog("Could not find IVersionBehavior interface in the generated class.");
            return;
        }

        // Listing all methods and grouping by signature
        Map<String, List<MethodInfo>> methodsBySignature = classInfo.getMethods().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                        method -> method.getName() + method.getParameterTypes().toString()
                ));

        // Generating public stubs for each grouped method
        for (List<MethodInfo> overloads : methodsBySignature.values()) {
            MethodInfo firstOverload = overloads.get(0);
            MethodDeclaration stub = createMethodStubSignature(firstOverload);

            if (overloads.size() > 1) { // Ambiguous method defined in along multiple versions
                behaviorInterface.addMember(createMethodStubSignature(firstOverload).setBody(null));
                MethodCallExpr delegateCall = new MethodCallExpr(
                        new FieldAccessExpr(new ThisExpr(), "currentState"),
                        stub.getNameAsString()
                );
                stub.getParameters().forEach(p -> delegateCall.addArgument(new NameExpr(p.getNameAsString())));
                stub.setBody(new BlockStmt().addStatement(delegateCall));

            } else { // Unambiguous method defined in a single version
                String versionSuffix = firstOverload.getVersion().toLowerCase();
                MethodCallExpr directCall = new MethodCallExpr(
                        new FieldAccessExpr(new ThisExpr(), "v" + versionSuffix + "_instance"),
                        stub.getNameAsString()
                );
                stub.getParameters().forEach(p -> directCall.addArgument(new NameExpr(p.getNameAsString())));
                stub.setBody(new BlockStmt().addStatement(directCall));
            }
            this.newCIDecl.addMember(stub);
        }
    }

    // HELPERS
    private static final Pattern VERSIONED_CLASS_PATTERN = Pattern.compile("(.+)__(\\d+)__$");

    private Optional<String> getVersionSuffix(CompilationUnit cu) {
        return getGroupName(cu, 2).map(numStr -> "v" + numStr);
    }

    private Optional<String> getGroupName(CompilationUnit cu, int group) {
        return cu.getPrimaryTypeName()
                 .map(name -> {
                     Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(name);
                     return matcher.matches() ? matcher.group(group) : null;
                 });
    }

    private MethodDeclaration createMethodStubSignature(MethodInfo methodInfo) {
        MethodDeclaration method = new MethodDeclaration();
        method.setName(methodInfo.getName());
        method.setType(methodInfo.getReturnType());
        method.setModifiers(new NodeList<>(new Modifier(Modifier.Keyword.PUBLIC)));
        methodInfo.getParameterTypes().forEach(param -> {
            method.addParameter(param, "arg" + method.getParameters().size());
        });
        return method;
    }

    private MethodDeclaration createSwitchToVersionMethod() {
        MethodDeclaration switchMethod = new MethodDeclaration()
                .setPublic(true)
                .setName("__switchToVersion")
                .setType("void");

        switchMethod.addParameter("int", "version");

        BlockStmt switchBody = new BlockStmt();
        IfStmt topIfStmt = null;
        IfStmt currentIf = null;

        for (int i = 0; i < versionAsts.size(); i++) {
            String versionSuffix = getVersionSuffix(versionAsts.get(i)).orElse("v" + (i + 1)).toLowerCase();
            int versionNumber = Integer.parseInt(versionSuffix.replace("v", ""));

            AssignExpr assignExpr = new AssignExpr(
                    new FieldAccessExpr(new ThisExpr(), "currentState"),
                    new FieldAccessExpr(new ThisExpr(), versionSuffix + "_instance"),
                    AssignExpr.Operator.ASSIGN
            );

            IfStmt newIf = new IfStmt(
                    new BinaryExpr(new NameExpr("version"), new IntegerLiteralExpr(String.valueOf(versionNumber)), BinaryExpr.Operator.EQUALS),
                    new BlockStmt().addStatement(new ExpressionStmt(assignExpr)),
                    null
            );

            if (topIfStmt == null) {
                topIfStmt = newIf;
                currentIf = topIfStmt;
            } else {
                currentIf.setElseStmt(newIf);
                currentIf = newIf;
            }
        }

        if (topIfStmt != null) {
            switchBody.addStatement(topIfStmt);
        }
        switchMethod.setBody(switchBody);
        return switchMethod;
    }
}