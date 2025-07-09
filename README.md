# PWV with Multi-Version Objects Compiler Draft

## Overview

This project is a proof-of-concept (PoC) transpiler for　PWV with Multi-Version Objects.

## How It Works

The main application (`App.java`) orchestrates a full transpile-compile-run pipeline:

1.  **Parse:** Parses all `.java` files within a specified test case directory (e.g., `sample_1`) located under `src/test/resources/mylang_samples/` into ASTs.
2.  **Transform:** Transpiles parsed ASTs to ASTs in [JavaParser library](https://github.com/javaparser/javaparser).
    -   **Pass 1**: Builds a `SymbolTable` containing information about all classes, methods, and variables.
    -   **Pass 2**: Rewrites calls to methods whose versions can be statically resolved.
    -   **Pass 3**: Rewrites the versioned class ASTs into a new, unified class AST.
3.  **Generate Code:** Converts the new, transformed ASTs back into well-formatted Java source code and saves them to the `target/output/` directory.
4.  **Compile:** Uses the Java Compiler API (`javax.tools.JavaCompiler`) to compile all the newly generated `.java` files.
5.  **Execute:** Runs the compiled `Main` class from the test case to verify the behavior of the transpiled code.


## Requirements

* JDK 20 or higher
* Apache Maven 3.6.x or higher

## Build and Run

The entire process is automated via a single Maven command.

1.  **Prepare Input Files**
    
    Place your set of test files into a new directory under `src/test/resources/mylang_samples/`.

    * **Example:** Create a folder named `simple_cases/01_basic_dispatch`.
    * **Note:** All `.java` files within a single test case directory should belong to the same package. For instance, add `package sample;` to the top of all files.


2.  **Build the Transpiler**
    ```bash
    mvn clean compile
    ```
3.  **Run the Full Pipeline**
    
    The output from the final executed program will be printed to your console.

    ```bash
    mvn exec:java -Dexec.args="simple_cases/01_basic_dispatch"
    ```

    To see the transpiler's internal logging, add the `-Ddebug=true` property.

    ```bash
    mvn exec:java -Dexec.args="simple_cases/01_basic_dispatch" -Ddebug=true
    ```

## Automated Tests
This project features a fully automated test suite powered by [JUnit 5](https://junit.org/).

### Preparing Tests 
The test suite relies on a specific directory structure within `src/test/resources/`

- Input Files (`mylang_samples/`)

    See [Build and Run > Prepare Input Files](#build-and-run)

- Expected Output (`expected_output/`)

    This directory mirrors the structure of the input file directory, `mylang_samples/`. For each test case, include an `expected.txt` file. This file acts as a "solution", recording the console output that the compiled `Main.java` produces at runtime.

### Running Tests
- Run all test cases under `src/test/resources/mylang_samples/`:

    ```bash
    mvn test
    ```
- Run limited test cases:

    e.g. Run all test cases under `src/test/resources/mylang_samples/simple_cases/`

    ```bash
    mvn test -Dtest.target="simple_cases"
    ```