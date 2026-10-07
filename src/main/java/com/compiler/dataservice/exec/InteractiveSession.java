package com.compiler.dataservice.exec;

import com.compiler.dataservice.config.AppProperties;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One running interactive submission: a live OS process whose stdin can be
 * written to while it runs and whose stdout/stderr are streamed out chunk by
 * chunk (not buffered to completion like the batch {@link DockerCodeRunner}),
 * so a program that prints a prompt and blocks on a read sees its prompt
 * delivered immediately instead of only after the whole run finishes.
 */
public class InteractiveSession {

    private final Process process;
    private final Path workDir;
    private final AppProperties.Interactive cfg;
    private final InteractiveExecutionListener listener;
    private final Writer stdinWriter;
    private final AtomicLong outputBytes = new AtomicLong();
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicReference<String> forcedStatus = new AtomicReference<>();
    private final long startTime = System.currentTimeMillis();
    private volatile ScheduledFuture<?> watchdogFuture;

    InteractiveSession(Process process, Path workDir, AppProperties.Interactive cfg, InteractiveExecutionListener listener) {
        this.process = process;
        this.workDir = workDir;
        this.cfg = cfg;
        this.listener = listener;
        this.stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    }

    void start(ScheduledExecutorService scheduler) {
        Thread outThread = new Thread(() -> pump(process.getInputStream(), true), "interactive-stdout-" + process.pid());
        Thread errThread = new Thread(() -> pump(process.getErrorStream(), false), "interactive-stderr-" + process.pid());
        outThread.setDaemon(true);
        errThread.setDaemon(true);
        outThread.start();
        errThread.start();

        // Backs up the in-shell `timeout` baked into the launch command, in
        // case a runaway child process it spawned escapes that. A few
        // seconds of slack lets the shell-level timeout win the race first.
        watchdogFuture = scheduler.schedule(() -> {
            if (process.isAlive()) {
                killInternal(ExecutionResult.TIME_LIMIT_EXCEEDED);
            }
        }, cfg.getSessionTimeoutSeconds() + 5L, TimeUnit.SECONDS);

        Thread waiter = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                outThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            try {
                errThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            finishOnce();
        }, "interactive-waiter-" + process.pid());
        waiter.setDaemon(true);
        waiter.start();
    }

    /** Writes to the running program's stdin; a no-op once the process has exited. */
    public synchronized void writeStdin(String data) {
        if (finished.get() || data == null || data.isEmpty()) {
            return;
        }
        try {
            stdinWriter.write(data);
            stdinWriter.flush();
        } catch (IOException ignored) {
            // Process likely already exited/closed its stdin; the exit event
            // the caller already got (or is about to get) explains why.
        }
    }

    /** Closes stdin so a program reading until EOF can proceed. */
    public synchronized void closeStdin() {
        try {
            stdinWriter.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    /** Stops the run early at the caller's request (e.g. the client disconnected or hit "stop"). */
    public void stop() {
        killInternal(ExecutionResult.KILLED);
    }

    private void killInternal(String reason) {
        forcedStatus.compareAndSet(null, reason);
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private void pump(InputStream in, boolean isStdout) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[2048];
            int n;
            while ((n = reader.read(buf)) != -1) {
                if (n == 0) {
                    continue;
                }
                String chunk = new String(buf, 0, n);
                long total = outputBytes.addAndGet(chunk.getBytes(StandardCharsets.UTF_8).length);
                if (isStdout) {
                    listener.onStdout(chunk);
                } else {
                    listener.onStderr(chunk);
                }
                if (total > cfg.getMaxOutputBytes()) {
                    killInternal(ExecutionResult.OUTPUT_LIMIT_EXCEEDED);
                    break;
                }
            }
        } catch (IOException ignored) {
            // Stream closed because the process exited or was killed.
        }
    }

    private void finishOnce() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> future = watchdogFuture;
        if (future != null) {
            future.cancel(false);
        }
        long elapsed = System.currentTimeMillis() - startTime;
        int exitCode = process.exitValue();

        String status = forcedStatus.get();
        if (status == null) {
            if (exitCode == 0) {
                status = ExecutionResult.SUCCESS;
            } else if (exitCode == 124) {
                // `timeout` in the shell script kills with this exit code once the
                // per-run wall-clock limit is reached.
                status = ExecutionResult.TIME_LIMIT_EXCEEDED;
            } else if (ExitDiagnostics.looksLikeMemoryLimitExceeded(exitCode, null)) {
                status = ExecutionResult.MEMORY_LIMIT_EXCEEDED;
            } else {
                status = ExecutionResult.ERROR;
            }
        }

        TempDirs.deleteRecursively(workDir);
        listener.onExit(new ExecutionResult(status, null, null, exitCode, elapsed));
    }
}
