package com.compiler.dataservice.exec;

/**
 * Infers whether a process died from hitting its memory limit, shared by the
 * batch and interactive runners. See {@link DockerCodeRunner} for the
 * reasoning: there's no cgroup here to report "OOM" directly, so this is
 * inferred from how the process died.
 */
public final class ExitDiagnostics {

    private ExitDiagnostics() {
    }

    public static boolean looksLikeMemoryLimitExceeded(int exitCode, String stderr) {
        if (exitCode == 134 || exitCode == 137 || exitCode == 139) {
            return true;
        }
        String s = stderr == null ? "" : stderr.toLowerCase();
        return s.contains("outofmemoryerror")
                || s.contains("cannot allocate memory")
                || s.contains("bad_alloc")
                || s.contains("memoryerror")
                || s.contains("javascript heap out of memory");
    }
}
