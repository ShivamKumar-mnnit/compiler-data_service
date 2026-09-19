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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Runs one submission to completion inside a short-lived, locked-down Docker
 * container: no network, capped memory/CPU/pids, read-only root filesystem,
 * dropped capabilities. The host also enforces its own watchdog on top of the
 * in-container `timeout`, and force-kills the container if it overruns.
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
        String containerName = "exec-" + UUID.randomUUID();

        try {
            Path sourceFile = workDir.resolve(language.getFileName());
            Files.writeString(sourceFile, code == null ? "" : code);

            List<String> command = buildDockerCommand(language, containerName, workDir, cfg);
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            if (stdin != null && !stdin.isEmpty()) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();

            StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream(), cfg.getMaxOutputBytes());
            StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream(), cfg.getMaxOutputBytes());
            Thread outThread = new Thread(stdoutGobbler, "stdout-gobbler-" + containerName);
            Thread errThread = new Thread(stderrGobbler, "stderr-gobbler-" + containerName);
            outThread.start();
            errThread.start();

            long start = System.currentTimeMillis();
            long waitSeconds = cfg.getTimeoutSeconds() + cfg.getGracePeriodSeconds();
            boolean finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                killContainer(containerName);
                process.destroyForcibly();
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

    private List<String> buildDockerCommand(Language language, String containerName, Path workDir, AppProperties.Execution cfg) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-i");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--network");
        cmd.add("none");
        cmd.add("--memory");
        cmd.add(cfg.getMemoryLimit());
        cmd.add("--memory-swap");
        cmd.add(cfg.getMemoryLimit());
        cmd.add("--cpus");
        cmd.add(cfg.getCpuLimit());
        cmd.add("--pids-limit");
        cmd.add("64");
        cmd.add("--security-opt");
        cmd.add("no-new-privileges");
        cmd.add("--cap-drop");
        cmd.add("ALL");
        cmd.add("--read-only");
        cmd.add("--tmpfs");
        cmd.add("/tmp:rw,size=16m");
        cmd.add("-v");
        cmd.add(workDir.toAbsolutePath() + ":/box:rw");
        cmd.add("-w");
        cmd.add("/box");
        cmd.add(language.getImage());
        cmd.addAll(language.buildCommand(cfg.getTimeoutSeconds()));
        return cmd;
    }

    private void killContainer(String containerName) {
        try {
            new ProcessBuilder("docker", "kill", containerName).start().waitFor(5, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to force-kill container {}", containerName, e);
        }
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
