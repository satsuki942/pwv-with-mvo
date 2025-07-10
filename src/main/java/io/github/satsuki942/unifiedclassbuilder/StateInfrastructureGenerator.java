package io.github.satsuki942.unifiedclassbuilder;
import io.github.satsuki942.util.AstUtil;

import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;

/**
 * Generates the basic infrastructure for the State Pattern within the unified class.
 * <p>
 * This includes creating the behavior interface, the empty implementation inner classes for each version,
 * the fields to hold the state, and the internal state-switching method.
 */
public class StateInfrastructureGenerator {

    private final ClassOrInterfaceDeclaration targetClass;
    private final List<CompilationUnit> versionAsts;

    public StateInfrastructureGenerator(ClassOrInterfaceDeclaration targetClass, List<CompilationUnit> versionAsts) {
        this.targetClass = targetClass;
        this.versionAsts = versionAsts;
    }

    /**
     * Executes the generation of all state-related infrastructure components.
     */
    public void generate() {
        ClassOrInterfaceDeclaration behaviorInterface = createIVersionBehaviorInterface();
        createImplClasses(behaviorInterface);
        createStateFields();
        createSwitchToVersionMethod();
    }

    private ClassOrInterfaceDeclaration createIVersionBehaviorInterface() {
        ClassOrInterfaceDeclaration behaviorInterface = new ClassOrInterfaceDeclaration();
        behaviorInterface.setInterface(true);
        behaviorInterface.setName("IVersionBehavior");
        behaviorInterface.setPrivate(true);
        this.targetClass.addMember(behaviorInterface);
        return behaviorInterface;
    }

    private void createImplClasses(ClassOrInterfaceDeclaration behaviorInterface) {
        versionAsts.forEach(cu -> {
            String versionSuffix = AstUtil.getVersionSuffix(cu).orElse("").toUpperCase();
            ClassOrInterfaceDeclaration implClass = new ClassOrInterfaceDeclaration();
            implClass.setName(versionSuffix + "_Impl");
            implClass.setPrivate(true);
            implClass.setStatic(true);
            implClass.addImplementedType(behaviorInterface.getNameAsString());
            this.targetClass.addMember(implClass);
        });
    }

    private void createStateFields() {
        for (CompilationUnit versionCu : versionAsts) {
            String versionSuffix = AstUtil.getVersionSuffix(versionCu).orElse("").toUpperCase();
            this.targetClass.addField(versionSuffix + "_Impl", versionSuffix.toLowerCase() + "_instance")
                    .setPrivate(true).setFinal(true);
        }
        this.targetClass.addField("IVersionBehavior", "currentState").setPrivate(true);
    }

    private void createSwitchToVersionMethod() {
        MethodDeclaration switchMethod = new MethodDeclaration()
                .setPublic(true)
                .setName("__switchToVersion")
                .setType("void")
                .addParameter("int", "version");

        SwitchStmt switchStmt = new SwitchStmt();
        switchStmt.setSelector(new NameExpr("version"));

        for (int i = 0; i < versionAsts.size(); i++) {
            String versionSuffix = AstUtil.getVersionSuffix(versionAsts.get(i)).orElse("v" + (i + 1)).toLowerCase();
            int versionNumber = Integer.parseInt(versionSuffix.replace("v", ""));

            // this.currentState = this.vX_instance; という代入式
            AssignExpr assignExpr = new AssignExpr(
                    new FieldAccessExpr(new ThisExpr(), "currentState"),
                    new FieldAccessExpr(new ThisExpr(), versionSuffix + "_instance"),
                    AssignExpr.Operator.ASSIGN
            );

            // case X: ... break;
            SwitchEntry switchEntry = new SwitchEntry();
            switchEntry.getLabels().add(new IntegerLiteralExpr(String.valueOf(versionNumber)));
            switchEntry.getStatements().add(new ExpressionStmt(assignExpr));
            switchEntry.getStatements().add(new BreakStmt());

            switchStmt.getEntries().add(switchEntry);
        }

        switchMethod.setBody(new BlockStmt().addStatement(switchStmt));
    
        this.targetClass.addMember(switchMethod);
    }
}
