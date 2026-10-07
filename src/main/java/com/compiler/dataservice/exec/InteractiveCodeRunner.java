package com.compiler.dataservice.exec;

import com.compiler.dataservice.config.AppProperties;
import com.compiler.dataservice.domain.Language;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Starts one interactive submission as a live OS process (same compile+run
 * shell command as the batch {@link DockerCodeRunner}, just launched to run
 * for the duration of a WebSocket session instead of to completion in one
 * call) and hands back a handle the caller streams stdin into / stdout out of.
 */
@Component
public class InteractiveCodeRunner {

    private final AppProperties props;
    private final ScheduledExecutorService interactiveWatchdogScheduler;

    public InteractiveCodeRunner(AppProperties props, ScheduledExecutorService interactiveWatchdogScheduler) {
        this.props = props;
        this.interactiveWatchdogScheduler = interactiveWatchdogScheduler;
    }

    public InteractiveSession start(Language language, String code, InteractiveExecutionListener listener) throws IOException {
        AppProperties.Interactive interactiveCfg = props.getInteractive();
        Path workDir = Files.createTempDirectory("isession-");
        try {
            Path sourceFile = workDir.resolve(language.getFileName());
            Files.writeString(sourceFile, code == null ? "" : code);

            List<String> command = ShellCommandBuilder.build(
                    language, interactiveCfg.getSessionTimeoutSeconds(), props.getExecution().getMemoryLimit());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            Process process = pb.start();

            InteractiveSession session = new InteractiveSession(process, workDir, interactiveCfg, listener);
            session.start(interactiveWatchdogScheduler);
            return session;
        } catch (IOException e) {
            TempDirs.deleteRecursively(workDir);
            throw e;
        }
    }
}
