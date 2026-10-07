package com.compiler.dataservice.exec;

import com.compiler.dataservice.domain.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Builds the `sh -c "..."` command used to compile+run a submission, shared
 * by the batch and interactive runners so the ulimit/memory-parsing logic
 * only lives in one place.
 */
public final class ShellCommandBuilder {

    private static final Logger log = LoggerFactory.getLogger(ShellCommandBuilder.class);

    private ShellCommandBuilder() {
    }

    public static List<String> build(Language language, int timeoutSeconds, String memoryLimit) {
        long memoryMb = parseMemoryLimitMb(memoryLimit);
        String ulimits = language.isMemoryUlimitSafe()
                ? "ulimit -v " + (memoryMb * 1024L) + " -u 64 2>/dev/null; "
                : "ulimit -u 64 2>/dev/null; ";
        String script = ulimits + language.buildCommand(timeoutSeconds, memoryMb);
        return List.of("sh", "-c", script);
    }

    /** Parses limits like "256m" / "1g" / "512k" into megabytes. */
    private static long parseMemoryLimitMb(String memoryLimit) {
        String value = memoryLimit.trim().toLowerCase();
        try {
            if (value.endsWith("g")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 1024L;
            } else if (value.endsWith("m")) {
                return Long.parseLong(value.substring(0, value.length() - 1));
            } else if (value.endsWith("k")) {
                return Math.max(1, Long.parseLong(value.substring(0, value.length() - 1)) / 1024L);
            }
            return Long.parseLong(value) / (1024L * 1024L);
        } catch (NumberFormatException e) {
            log.warn("Could not parse memory limit '{}', defaulting to 256m", memoryLimit);
            return 256L;
        }
    }
}
