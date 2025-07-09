package io.github.satsuki942;

public class Logger {
    public static boolean DEBUG_MODE = false;

    /**
     * Outputs a debug message to the console if DEBUG_MODE is enabled.
     * @param message The message to output
     */
    public static void debugLog(String message) {
        if (DEBUG_MODE) {
            System.out.println("[LOG]     " + message);
        }
    }

    /**
     * Outputs a success message to the console.
     * @param message The success message to output
     */
    public static void successLog(String message) {
        if (DEBUG_MODE) {
            System.out.println("[SUCCESS] " + message);
        }
    }

    /**
     * Outputs an error message to the console.
     * @param message The error message to output
     */
    public static void errorLog(String message) {
        if (DEBUG_MODE) {
            System.err.println("[ERROR]   " + message);
        }
    }

    /**
     * Outputs a general log message to the console.
     * @param message The message to output
     */
    public static void Log(String message) {
        System.out.println(message);
    }
}
