package com.compiler.dataservice.exec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Drains a process stream on its own thread so a chatty program can't
 * deadlock the parent (stdout/stderr pipes block once their OS buffer fills).
 * Output is capped at maxBytes; anything beyond that is read and discarded
 * to keep the process moving without growing the buffer unbounded.
 */
public class StreamGobbler implements Runnable {

    private final InputStream inputStream;
    private final int maxBytes;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public StreamGobbler(InputStream inputStream, int maxBytes) {
        this.inputStream = inputStream;
        this.maxBytes = maxBytes;
    }

    @Override
    public void run() {
        byte[] chunk = new byte[4096];
        int read;
        try {
            while ((read = inputStream.read(chunk)) != -1) {
                synchronized (buffer) {
                    if (buffer.size() < maxBytes) {
                        int toWrite = Math.min(read, maxBytes - buffer.size());
                        buffer.write(chunk, 0, toWrite);
                    }
                }
            }
        } catch (IOException ignored) {
            // Stream closed because the process was killed/finished; nothing to do.
        }
    }

    public String getOutput() {
        synchronized (buffer) {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
