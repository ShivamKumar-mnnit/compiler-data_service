package com.compiler.dataservice.exec;

import com.compiler.dataservice.config.AppProperties;
import com.compiler.dataservice.domain.Language;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

            List<String> command = ShellCommandBuilder.build(language, cfg.getTimeoutSeconds(), cfg.getMemoryLimit());
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
                // Our own watchdog fired: the in-shell `timeout` didn't kill it in time
                // (e.g. a runaway child process it spawned), so force-kill the whole tree.
                killProcessTree(process);
                outThread.join(2000);
                errThread.join(2000);
                return new ExecutionResult(ExecutionResult.TIME_LIMIT_EXCEEDED, stdoutGobbler.getOutput(), stderrGobbler.getOutput(), null, elapsed);
            }

            outThread.join(2000);
            errThread.join(2000);
            int exitCode = process.exitValue();
            String stderr = stderrGobbler.getOutput();
            String status;
            if (exitCode == 0) {
                status = ExecutionResult.SUCCESS;
            } else if (exitCode == 124) {
                // `timeout` in the shell script kills with this exit code once the
                // per-run wall-clock limit is reached - this is the infinite-loop case.
                status = ExecutionResult.TIME_LIMIT_EXCEEDED;
            } else if (ExitDiagnostics.looksLikeMemoryLimitExceeded(exitCode, stderr)) {
                status = ExecutionResult.MEMORY_LIMIT_EXCEEDED;
            } else {
                status = ExecutionResult.ERROR;
            }
            return new ExecutionResult(status, stdoutGobbler.getOutput(), stderr, exitCode, elapsed);
        } finally {
            TempDirs.deleteRecursively(workDir);
        }
    }

    private void killProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
