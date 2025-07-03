package io.github.satsuki942;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.YamlPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import java.util.stream.Collectors;

public class App {

    static private String INPUTPATH = "src/test/resources/mylang_samples/";
    static private String OUTPUTPATH = "target/output/";
    static private String OUTPUTPACKAGE = "sample";
    public static void main(String[] args) {

        Path inputDir = Paths.get(INPUTPATH + args[0]);
        Path outputDir = Paths.get(OUTPUTPATH + OUTPUTPACKAGE);

        // Ensure the output directory exists
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("[SUCCESS] Created output directory: " + outputDir);

        // 1. parse Java files in the input directory to create MyLang-ASTs
        List<CompilationUnit> MyLangASTs = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(inputDir)) {
            List<Path> javaFiles = paths.filter(Files::isRegularFile)
                                        .filter(path -> path.toString().endsWith(".java"))
                                        .collect(Collectors.toList());
            for (Path javaFile : javaFiles) {
                MyLangASTs.add(StaticJavaParser.parse(javaFile));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("[SUCCESS] Parsed " + MyLangASTs.size() + " MyLang-ASTs from " + inputDir);

        // 2. call the transformer to transform the MyLang-ASTs
        MyLangTransformer transformer = new MyLangTransformer();
        List<CompilationUnit> transpiledAsts = transformer.transform(MyLangASTs);

        // 3. output the transformed ASTs to files
        List<String> generatedFilePaths = new ArrayList<>();
        for (CompilationUnit cu : transpiledAsts) {
            // For Debuging: Print the transpiled AST in YAML format
            // YamlPrinter printer = new YamlPrinter(true);
            // System.out.println(printer.output(cu));
            
            String className = cu.getTypes().stream()
                        .filter(type -> type.isPublic() && type.isClassOrInterfaceDeclaration())
                        .findFirst()
                        .map(type -> type.getNameAsString())
                        .orElse("UnknownClass");
            Path outputFile = outputDir.resolve(className + ".java");
            try {
                Files.write(outputFile, cu.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                generatedFilePaths.add(outputFile.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 4. compile the transpiled sources
        System.out.println("Compiling transpiled sources...");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int compilationResult = compiler.run(null, null, null, 
            generatedFilePaths.toArray(new String[0]));

        if (compilationResult == 0) {
            System.out.println("[SUCCESS] Compilation Succeeded.");
        } else {
            System.err.println("[ERROR] Compilation Failed.");
            return;
        }

        // 5. run the compiled code
        System.out.println("Running compiled code...");
        try {
            System.out.println("\nRunning Result: ----------------------");
            Path outputPath = Paths.get(OUTPUTPATH);
            runProcess(outputPath, OUTPUTPACKAGE + ".Main");
            System.out.println("--------------------------------------\n");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("[SUCCESS] Execution completed.");
    }

    private static void runProcess(Path classpath, String mainClass) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "java",
            "-cp", classpath.toAbsolutePath().toString(),
            mainClass
        );
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        process.waitFor();
    }
}