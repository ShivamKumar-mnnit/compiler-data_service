package com.compiler.dataservice.exec;

/** Callbacks an {@link InteractiveSession} uses to report what's happening, as it happens. */
public interface InteractiveExecutionListener {

    void onStdout(String chunk);

    void onStderr(String chunk);

    void onExit(ExecutionResult result);
}
