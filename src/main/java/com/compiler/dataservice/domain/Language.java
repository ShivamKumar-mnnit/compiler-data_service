package com.compiler.dataservice.domain;

import com.compiler.dataservice.exception.UnsupportedLanguageException;

import java.util.List;
import java.util.function.IntFunction;

/**
 * One entry per supported language: which Docker image runs it, what the
 * source file must be named, and the compile+run command executed inside the
 * container. Everything is combined into a single `sh -c "..."` so compile
 * errors (non-zero exit, stderr populated) short-circuit the run step.
 */
public enum Language {

    C("c", "gcc:13", "Main.c",
            timeout -> List.of("sh", "-c",
                    "gcc -O2 -o Main Main.c && timeout " + timeout + " ./Main")),

    CPP("cpp", "gcc:13", "Main.cpp",
            timeout -> List.of("sh", "-c",
                    "g++ -O2 -o Main Main.cpp && timeout " + timeout + " ./Main")),

    JAVA("java", "eclipse-temurin:21-jdk", "Main.java",
            timeout -> List.of("sh", "-c",
                    "javac Main.java && timeout " + timeout + " java Main")),

    PYTHON("python", "python:3.11-slim", "Main.py",
            timeout -> List.of("sh", "-c",
                    "timeout " + timeout + " python3 Main.py")),

    JAVASCRIPT("javascript", "node:20-slim", "Main.js",
            timeout -> List.of("sh", "-c",
                    "timeout " + timeout + " node Main.js"));

    private final String code;
    private final String image;
    private final String fileName;
    private final IntFunction<List<String>> commandBuilder;

    Language(String code, String image, String fileName, IntFunction<List<String>> commandBuilder) {
        this.code = code;
        this.image = image;
        this.fileName = fileName;
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

    public String getImage() {
        return image;
    }

    public String getFileName() {
        return fileName;
    }

    public List<String> buildCommand(int timeoutSeconds) {
        return commandBuilder.apply(timeoutSeconds);
    }
}
