package com.compiler.dataservice.domain;

import com.compiler.dataservice.exception.UnsupportedLanguageException;

import java.util.List;
import java.util.function.BiFunction;

/**
 * One entry per supported language: what the source file must be named, the
 * binary required on PATH, and the compile+run shell snippet executed
 * directly on the host (no container). The run step is wrapped in `timeout`
 * so compile errors (non-zero exit, stderr populated) short-circuit before
 * it, and a runaway program is still killed.
 */
public enum Language {

    C("c", "Main.c", List.of("gcc"), true,
            (timeout, memoryMb) -> "gcc -O2 -o Main Main.c && timeout " + timeout + " ./Main"),

    CPP("cpp", "Main.cpp", List.of("g++"), true,
            (timeout, memoryMb) -> "g++ -O2 -o Main Main.cpp && timeout " + timeout + " ./Main"),

    // No shell-level memory ulimit: the JVM reserves large virtual address
    // space for heap/metaspace on startup regardless of actual usage, so
    // `ulimit -v` would make it fail to launch. Cap the heap directly instead.
    JAVA("java", "Main.java", List.of("javac", "java"), false,
            (timeout, memoryMb) -> "javac Main.java && timeout " + timeout
                    + " java -Xmx" + memoryMb + "m -XX:+ExitOnOutOfMemoryError Main"),

    PYTHON("python", "Main.py", List.of("python3"), true,
            (timeout, memoryMb) -> "timeout " + timeout + " python3 Main.py"),

    JAVASCRIPT("javascript", "Main.js", List.of("node"), true,
            (timeout, memoryMb) -> "timeout " + timeout + " node Main.js");

    private final String code;
    private final String fileName;
    private final List<String> requiredBinaries;
    private final boolean applyMemoryUlimit;
    private final BiFunction<Integer, Long, String> commandBuilder;

    Language(String code, String fileName, List<String> requiredBinaries, boolean applyMemoryUlimit,
             BiFunction<Integer, Long, String> commandBuilder) {
        this.code = code;
        this.fileName = fileName;
        this.requiredBinaries = requiredBinaries;
        this.applyMemoryUlimit = applyMemoryUlimit;
        this.commandBuilder = commandBuilder;
    }

    public static Language fromCode(String code) {
        if (code != null) {
            for (Language language : values()) {
                if (language.code.equalsIgnoreCase(code)) {
                    return language;
                }
            }
        }
        throw new UnsupportedLanguageException("Unsupported language: " + code);
    }

    public String getFileName() {
        return fileName;
    }

    /** Binaries this language needs on PATH, checked at startup. */
    public List<String> getRequiredBinaries() {
        return requiredBinaries;
    }

    /** Whether the shell-level `ulimit -v` memory cap is safe to apply for this language. */
    public boolean isMemoryUlimitSafe() {
        return applyMemoryUlimit;
    }

    public String buildCommand(int timeoutSeconds, long memoryLimitMb) {
        return commandBuilder.apply(timeoutSeconds, memoryLimitMb);
    }
}
