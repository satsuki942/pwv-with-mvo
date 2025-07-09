package io.github.satsuki942;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MyLangTransformerTest {

    private final MyLangTransformer transformer = new MyLangTransformer();
    private static final Path SAMPLES_ROOT = Paths.get("src/test/resources/mylang_samples");
    private static final Path EXPECTED_ROOT = Paths.get("src/test/resources/expected_output");
    private static final Path TEMP_BUILD_ROOT = Paths.get("target/test-builds");

    @TestFactory
    Stream<DynamicTest> runAllTestCases() throws IOException {
        // 1. Get the root directory for test cases
        String targetPath = System.getProperty("test.target", "");
        Path searchRoot = SAMPLES_ROOT.resolve(targetPath);

        // 2. Recursively search for test case directories
        //    - Test Case Directory: Contains at least one .java file
        List<Path> testCaseDirs = Files.walk(searchRoot)
                .filter(Files::isDirectory)
                .filter(dir -> {
                    try (Stream<Path> entries = Files.list(dir)) {
                        return entries.anyMatch(p -> p.toString().endsWith(".java"));
                    } catch (IOException e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        // 3. Generating tests for each test case directory
        return testCaseDirs.stream()
                .map(inputDir -> {
                    String testName = SAMPLES_ROOT.relativize(inputDir).toString();
                    return DynamicTest.dynamicTest("TestCase: " + testName, () -> {
                        executeAndVerify(inputDir);
                    });
                });
    }

    // Condoucts the transpilation, compilation, execution, and output verification for a single test case.
    private void executeAndVerify(Path inputDir) throws IOException, InterruptedException {
        // --- 1. Transpilation ---
        List<CompilationUnit> sourceAsts = parseDirectory(inputDir);
        List<CompilationUnit> transpiledAsts = transformer.transform(sourceAsts);

        // --- 2. Write transpiled Java programs to temporary directory ---
        Path tempBuildDir = TEMP_BUILD_ROOT.resolve(SAMPLES_ROOT.relativize(inputDir));
        Files.createDirectories(tempBuildDir);
        List<String> javaFilePaths = new ArrayList<>();

        for (CompilationUnit cu : transpiledAsts) {
            String packageName = cu.getPackageDeclaration().map(pd -> pd.getName().asString()).orElse("");
            String className = cu.getTypes().stream()
                        .filter(type -> type.isPublic() && type.isClassOrInterfaceDeclaration())
                        .findFirst()
                        .map(type -> type.getNameAsString())
                        .orElse("UnknownClass");
            
            Path packageDir = tempBuildDir.resolve(packageName.replace('.', File.separatorChar));
            Files.createDirectories(packageDir);
            Path outputFile = packageDir.resolve(className + ".java");
            
            Files.writeString(outputFile, cu.toString());
            javaFilePaths.add(outputFile.toString());
        }

        // --- 3. Compilation ---
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int compilationResult = compiler.run(null, null, null, javaFilePaths.toArray(new String[0]));
        Assertions.assertEquals(0, compilationResult, "Compilation failed for test case: " + inputDir);

        // --- 4. Execution & Output Capture ---
        String mainClassName = "sample.Main";
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", tempBuildDir.toString(), mainClassName);
        Process process = processBuilder.start();
        
        StringBuilder processOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processOutput.append(line).append(System.lineSeparator());
            }
        }
        int exitCode = process.waitFor();
        Assertions.assertEquals(0, exitCode, "Runtime execution failed.");

        // --- 5. Verification ---
        Path expectedOutputFile = EXPECTED_ROOT.resolve(SAMPLES_ROOT.relativize(inputDir)).resolve("expected.txt");
        Assertions.assertTrue(Files.exists(expectedOutputFile), "Expected output file not found: " + expectedOutputFile);

        String expectedOutput = Files.readString(expectedOutputFile);
        Assertions.assertEquals(
            expectedOutput.trim(),
            processOutput.toString().trim(),
            "Runtime output does not match expected output."
        );
        System.out.println("Test case passed: " + inputDir);
    }
    
    private List<CompilationUnit> parseDirectory(Path dir) throws IOException {
        List<CompilationUnit> asts = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(javaFile -> {
                    try {
                        asts.add(StaticJavaParser.parse(javaFile));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                 });
        }
        return asts;
    }
}