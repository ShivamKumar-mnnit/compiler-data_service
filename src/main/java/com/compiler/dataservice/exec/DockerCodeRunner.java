package com.compiler.dataservice.exec;

import com.compiler.dataservice.config.AppProperties;
import com.compiler.dataservice.domain.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs one submission to completion as a plain OS process in a fresh temp
 * directory (no Docker). Isolation is best-effort since there is no
 * container/namespace boundary here: a shell-level ulimit caps address
 * space and process count, and both an in-shell `timeout` and this class's
 * own watchdog cap wall-clock time. This trades some isolation strength for
 * running on hosts (e.g. Render) that don't expose a Docker daemon inside
 * the app's own container.
 */
@Component
public class DockerCodeRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerCodeRunner.class);

    private final AppProperties props;

    public DockerCodeRunner(AppProperties props) {
        this.props = props;
    }

    public ExecutionResult run(Language language, String code, String stdin) throws IOException, InterruptedException {
        AppProperties.Execution cfg = props.getExecution();
        Path workDir = Files.createTempDirectory("job-");

        try {
            Path sourceFile = workDir.resolve(language.getFileName());
            Files.writeString(sourceFile, code == null ? "" : code);

            List<String> command = buildShellCommand(language, cfg);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            Process process = pb.start();

            if (stdin != null && !stdin.isEmpty()) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();

            StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream(), cfg.getMaxOutputBytes());
            StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream(), cfg.getMaxOutputBytes());
            Thread outThread = new Thread(stdoutGobbler, "stdout-gobbler-" + process.pid());
            Thread errThread = new Thread(stderrGobbler, "stderr-gobbler-" + process.pid());
            outThread.start();
            errThread.start();

            long start = System.currentTimeMillis();
            long waitSeconds = cfg.getTimeoutSeconds() + cfg.getGracePeriodSeconds();
            boolean finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                killProcessTree(process);
                outThread.join(2000);
                errThread.join(2000);
                return new ExecutionResult(ExecutionResult.TIMEOUT, stdoutGobbler.getOutput(), stderrGobbler.getOutput(), null, elapsed);
            }

            outThread.join(2000);
            errThread.join(2000);
            int exitCode = process.exitValue();
            String status = exitCode == 0 ? ExecutionResult.SUCCESS
                    : (exitCode == 124 ? ExecutionResult.TIMEOUT : ExecutionResult.ERROR);
            return new ExecutionResult(status, stdoutGobbler.getOutput(), stderrGobbler.getOutput(), exitCode, elapsed);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private List<String> buildShellCommand(Language language, AppProperties.Execution cfg) {
        long memoryMb = parseMemoryLimitMb(cfg.getMemoryLimit());
        String ulimits = language.isMemoryUlimitSafe()
                ? "ulimit -v " + (memoryMb * 1024L) + " -u 64 2>/dev/null; "
                : "ulimit -u 64 2>/dev/null; ";
        String script = ulimits + language.buildCommand(cfg.getTimeoutSeconds(), memoryMb);
        return List.of("sh", "-c", script);
    }

    /** Parses limits like "256m" / "1g" / "512k" into megabytes. */
    private long parseMemoryLimitMb(String memoryLimit) {
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

    private void killProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private void deleteRecursively(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean up work dir {}", path, e);
        }
    }
}
