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
    
    private void generateStateInfrastructure() {
        // Generating IVersionBehavior Interface
        // 1. ClassOrInterfaceDeclarationとしてまず生成
        ClassOrInterfaceDeclaration behaviorInterface = new ClassOrInterfaceDeclaration();
        // 2. setInterface(true)で、これをインターフェースであると設定
        behaviorInterface.setInterface(true);
        behaviorInterface.setName("IVersionBehavior");
        behaviorInterface.setPrivate(true);
        // 3. addMember()で内部インターフェースとして追加
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

        // 状態を保持するフィールド
        for (CompilationUnit versionCu : versionAsts) {
            String versionSuffix = getVersionSuffix(versionCu).orElse("").toUpperCase();
            this.newCIDecl.addField(versionSuffix + "_Impl", versionSuffix.toLowerCase() + "_instance")
                    .setPrivate(true).setFinal(true);
        }
        this.newCIDecl.addField("IVersionBehavior", "currentState").setPrivate(true);
        
        this.newCIDecl.addMember(createSwitchToVersionMethod());

        // コンストラクタでフィールドを初期化
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

    /**
     * ステップ2：バージョンごとの実装を、対応する内部クラスにコピーする
     */
    private void mergeMembersIntoImplClasses() {
        for (CompilationUnit versionCu : versionAsts) {
            String versionSuffix = getVersionSuffix(versionCu).orElse("").toUpperCase();
            
            if (versionSuffix.isEmpty()) continue;
        
            String implClassName = versionSuffix + "_Impl";

            // this.newClassのメンバーリストから、対応する内部実装クラス (V1_Implなど) を見つける
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

            // ラムダ式で使うために、finalな変数に値をコピーする
            final ClassOrInterfaceDeclaration implClass = foundImplClass;

            // 元のバージョンクラスからメンバーをコピー
            versionCu.getPrimaryType().ifPresent(type -> {
                type.getMembers().forEach(member -> implClass.addMember(member.clone()));
            });
        }
    }

    /**
     * ステップ3：公開されるスタブメソッドを生成する
     */
    private void generatePublicStubs() {
        ClassInfo classInfo = symbolTable.lookupClass(baseName);
        if (classInfo == null) return;
        
        // getMembers()で全メンバーをループし、目的のインターフェースを探す
        ClassOrInterfaceDeclaration behaviorInterface = null;
        for (BodyDeclaration<?> member : this.newCIDecl.getMembers()) {
            if (member.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cid = member.asClassOrInterfaceDeclaration();
                // .isInterface() でインターフェースかどうかを判定
                if (cid.isInterface() && "IVersionBehavior".equals(cid.getNameAsString())) {
                    behaviorInterface = cid;
                    break; // 見つかったのでループを抜ける
                }
            }
        }

        if (behaviorInterface == null) {
            // インターフェースが見つからなければ、何もせず終了
            System.err.println("Error: Could not find IVersionBehavior interface in the generated class.");
            return;
        }

        // 1. 全メソッドを一つのリストにし、シグネチャでグループ化
        Map<String, List<MethodInfo>> methodsBySignature = classInfo.getMethods().values().stream()
                .flatMap(List::stream) // Stream<List<MethodInfo>> を Stream<MethodInfo> に平坦化
                .collect(Collectors.groupingBy(
                        // "methodName[paramType1, paramType2]" のような文字列をキーにする
                        method -> method.getName() + method.getParameterTypes().toString()
                ));

        // 2. グループ化されたメソッドごとにスタブを生成
        for (List<MethodInfo> overloads : methodsBySignature.values()) {
            MethodInfo firstOverload = overloads.get(0);
            MethodDeclaration stub = createMethodStubSignature(firstOverload);

            if (overloads.size() > 1) { // 複数のバージョンに存在する -> 曖昧な呼び出し
                // a. IVersionBehaviorインターフェースに、この曖昧なメソッドのシグネチャを追加
                behaviorInterface.addMember(createMethodStubSignature(firstOverload).setBody(null));

                // b. 統合クラスのスタブには、現在の状態に処理を委譲するコードを生成
                MethodCallExpr delegateCall = new MethodCallExpr(
                        new FieldAccessExpr(new ThisExpr(), "currentState"),
                        stub.getNameAsString()
                );
                // 呼び出しの引数をそのまま渡す
                stub.getParameters().forEach(p -> delegateCall.addArgument(new NameExpr(p.getNameAsString())));
                stub.setBody(new BlockStmt().addStatement(delegateCall));

            } else { // 一つのバージョンにしか存在しない -> 静的な呼び出し
                String versionSuffix = firstOverload.getVersion().toLowerCase();
                MethodCallExpr directCall = new MethodCallExpr(
                        new FieldAccessExpr(new ThisExpr(), "v" + versionSuffix + "_instance"),
                        stub.getNameAsString()
                );
                // 呼び出しの引数をそのまま渡す
                stub.getParameters().forEach(p -> directCall.addArgument(new NameExpr(p.getNameAsString())));
                stub.setBody(new BlockStmt().addStatement(directCall));
            }
            this.newCIDecl.addMember(stub);
        }
    }

    // --- ヘルパーメソッド ---
    private static final Pattern VERSIONED_CLASS_PATTERN = Pattern.compile("(.+)__(\\d+)__$");
    // (isVersioned, getBaseName, getVersionSuffix などのヘルパーは別途定義)

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
             // 簡易的な実装。実際は型と名前を正しく設定する必要がある
            method.addParameter(param, "arg" + method.getParameters().size());
        });
        return method;
    }

    /**
     * __switchToVersion(int)メソッドのASTを生成する
     */
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
            // "v1" などのバージョン接尾辞を取得
            String versionSuffix = getVersionSuffix(versionAsts.get(i)).orElse("v" + (i + 1)).toLowerCase();
            // 1, 2, ... といったバージョン番号を生成
            int versionNumber = Integer.parseInt(versionSuffix.replace("v", ""));

            // this.currentState = this.vX_instance; という代入式を生成
            AssignExpr assignExpr = new AssignExpr(
                    new FieldAccessExpr(new ThisExpr(), "currentState"),
                    new FieldAccessExpr(new ThisExpr(), versionSuffix + "_instance"),
                    AssignExpr.Operator.ASSIGN
            );

            IfStmt newIf = new IfStmt(
                    new BinaryExpr(new NameExpr("version"), new IntegerLiteralExpr(String.valueOf(versionNumber)), BinaryExpr.Operator.EQUALS),
                    new BlockStmt().addStatement(new ExpressionStmt(assignExpr)),
                    null // else節は後で連結する
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