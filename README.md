# PWV with Multi-Version Objects Compiler PoC

## Overview

This project is a proof-of-concept (PoC) transpiler for　PWV with Multi-Version Objects.

## How It Works

The main application (`App.java`) orchestrates a full transpile-compile-run pipeline:

1.  **Parse:** Parses all `.java` files within a specified test case directory (e.g., `sample_1`) located under `src/test/resources/mylang_samples/` into JavaParser ASTs.
2.  **Transform:** (Work In Progress)
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

    * **Example:** Create a folder named `sample_1`.
    * **Note:** All `.java` files within a single test case directory should belong to the same package. For instance, add `package sample;` to the top of all files.


2.  **Build the Transpiler**
    Compile the transpiler itself:
    ```bash
    mvn clean compile
    ```

3.  **Run the Full Pipeline**
    ```bash
    mvn exec:java -Dexec.args="sample_1"
    ```
    The output from the final executed program will be printed to your console.