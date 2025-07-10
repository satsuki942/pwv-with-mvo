package io.github.satsuki942.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.ast.CompilationUnit;

public class AstUtil {

    /**
     * The regex pattern to identify and capture the base name and version number
     * from a versioned class name (e.g., "MyClass__1__").<p>
     * 
     * - Group 1: (.+) captures the base name (e.g., "MyClass").<p>
     * - Group 2: (\\d+) captures the version number (e.g., "1").
     */
    private static final Pattern VERSIONED_CLASS_PATTERN = Pattern.compile("(.+)__(\\d+)__$");

    /**
     * Private constructor to prevent instantiation.
     */
    private AstUtil() {}

    /**
     * Checks if the primary type in a given CompilationUnit is a versioned class.
     * A class is considered versioned if its name matches the VERSIONED_CLASS_PATTERN.
     *
     * @param cu The CompilationUnit to check.
     * @return {@code true} if the primary type is versioned, {@code false} otherwise.
     */
    public static boolean isVersioned(CompilationUnit cu) {
        return cu.getPrimaryType()
                 .map(t -> t.getNameAsString())
                 .map(name -> VERSIONED_CLASS_PATTERN.matcher(name).matches())
                 .orElse(false);
    }

    /**
     * Extracts the version suffix (e.g., "v1", "v2") from a versioned class name.
     *
     * @param cu The CompilationUnit of the versioned class.
     * @return An {@link Optional} containing the version suffix (like "v1") if found, otherwise an empty Optional.
     */
    public static Optional<String> getVersionSuffix(CompilationUnit cu) {
        return getGroupName(cu, 2).map(numStr -> "v" + numStr);
    }

    /**
     * Returns the compiled regex pattern used for identifying versioned classes.
     *
     * @return The static {@link Pattern} instance.
     */
    public static Pattern getVersionedClassPattern() {
        return VERSIONED_CLASS_PATTERN;
    }

    /**
     * A private helper to extract a specific capture group from the versioned class name.
     *
     * @param cu    The CompilationUnit to inspect.
     * @param group The capture group number to extract (1 for base name, 2 for version number).
     * @return An {@link Optional} containing the captured string group if the pattern matches, otherwise an empty Optional.
     */
    private static Optional<String> getGroupName(CompilationUnit cu, int group) {
        return cu.getPrimaryType().map(t -> t.getNameAsString()).flatMap(name -> {
            Matcher matcher = VERSIONED_CLASS_PATTERN.matcher(name);
            return Optional.ofNullable(matcher.matches() ? matcher.group(group) : null);
        });
    }
}
