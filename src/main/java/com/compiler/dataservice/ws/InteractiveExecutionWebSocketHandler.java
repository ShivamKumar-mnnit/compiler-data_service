package com.compiler.dataservice.ws;

import com.compiler.dataservice.config.AppProperties;
import com.compiler.dataservice.domain.Language;
import com.compiler.dataservice.exception.UnsupportedLanguageException;
import com.compiler.dataservice.exec.ExecutionResult;
import com.compiler.dataservice.exec.InteractiveCodeRunner;
import com.compiler.dataservice.exec.InteractiveExecutionListener;
import com.compiler.dataservice.exec.InteractiveSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * One WebSocket connection = one interactive run-at-a-time "terminal".
 * Protocol (JSON text frames):
 *
 * <pre>
 * client -> server
 *   {"type":"start","language":"cpp","code":"..."}   start a run (fails if one is already active)
 *   {"type":"stdin","data":"5\n"}                     send input to the running program
 *   {"type":"eof"}                                    close stdin (for programs that read until EOF)
 *   {"type":"stop"}                                   kill the running program early
 *
 * server -> client
 *   {"type":"stdout","data":"..."}                    a chunk of program output, as produced
 *   {"type":"stderr","data":"..."}
 *   {"type":"exit","status":"SUCCESS","exitCode":0,"executionTimeMs":123}
 *   {"type":"error","message":"..."}                  protocol/validation/capacity error
 * </pre>
 *
 * Unlike the batch /api/v1/compile endpoint, stdout/stderr are streamed as
 * the program produces them (not buffered to completion), so a prompt
 * printed without a trailing newline still reaches the client immediately,
 * and stdin written mid-run reaches the program's next blocking read.
 */
@Component
public class InteractiveExecutionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(InteractiveExecutionWebSocketHandler.class);

    private final InteractiveCodeRunner runner;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Semaphore concurrencyLimiter;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public InteractiveExecutionWebSocketHandler(InteractiveCodeRunner runner, AppProperties props) {
        this.runner = runner;
        this.concurrencyLimiter = new Semaphore(props.getInteractive().getMaxConcurrentSessions());
    }

    private static final class SessionState {
        final Object sendLock = new Object();
        volatile InteractiveSession process;
        volatile boolean slotHeld;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        sessions.put(wsSession.getId(), new SessionState());
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        SessionState state = sessions.get(wsSession.getId());
        if (state == null) {
            return;
        }

        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (IOException e) {
            sendError(wsSession, state, "Invalid JSON message");
            return;
        }

        String type = node.path("type").asText("");
        switch (type) {
            case "start" -> handleStart(wsSession, state, node);
            case "stdin" -> {
                if (state.process != null) {
                    state.process.writeStdin(node.path("data").asText(""));
                }
            }
            case "eof" -> {
                if (state.process != null) {
                    state.process.closeStdin();
                }
            }
            case "stop" -> {
                if (state.process != null) {
                    state.process.stop();
                }
            }
            default -> sendError(wsSession, state, "Unknown message type: " + type);
        }
    }

    private void handleStart(WebSocketSession wsSession, SessionState state, JsonNode node) {
        if (state.process != null) {
            sendError(wsSession, state, "A run is already in progress on this connection");
            return;
        }

        String languageCode = node.path("language").asText(null);
        String code = node.path("code").asText(null);
        if (code == null) {
            sendError(wsSession, state, "code is required");
            return;
        }

        Language language;
        try {
            language = Language.fromCode(languageCode);
        } catch (UnsupportedLanguageException e) {
            sendError(wsSession, state, e.getMessage());
            return;
        }

        if (!concurrencyLimiter.tryAcquire()) {
            sendError(wsSession, state, "Server is busy running other interactive sessions, please try again shortly");
            return;
        }
        state.slotHeld = true;

        InteractiveExecutionListener listener = new InteractiveExecutionListener() {
            @Override
            public void onStdout(String chunk) {
                send(wsSession, state, "stdout", chunk);
            }

            @Override
            public void onStderr(String chunk) {
                send(wsSession, state, "stderr", chunk);
            }

            @Override
            public void onExit(ExecutionResult result) {
                releaseSlot(state);
                state.process = null;
                sendExit(wsSession, state, result);
            }
        };

        try {
            InteractiveSession session = runner.start(language, code, listener);
            state.process = session;
            String initialStdin = node.path("stdin").asText(null);
            if (initialStdin != null && !initialStdin.isEmpty()) {
                session.writeStdin(initialStdin);
            }
        } catch (IOException e) {
            log.warn("Failed to start interactive session", e);
            releaseSlot(state);
            sendError(wsSession, state, "Failed to start process");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        SessionState state = sessions.remove(wsSession.getId());
        if (state != null) {
            if (state.process != null) {
                state.process.stop();
            }
            releaseSlot(state);
        }
    }

    private void releaseSlot(SessionState state) {
        if (state.slotHeld) {
            state.slotHeld = false;
            concurrencyLimiter.release();
        }
    }

    private void send(WebSocketSession wsSession, SessionState state, String type, String data) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("type", type);
        payload.put("data", data);
        sendPayload(wsSession, state, payload);
    }

    private void sendExit(WebSocketSession wsSession, SessionState state, ExecutionResult result) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("type", "exit");
        payload.put("status", result.status());
        payload.put("exitCode", result.exitCode());
        payload.put("executionTimeMs", result.executionTimeMs());
        sendPayload(wsSession, state, payload);
    }

    private void sendError(WebSocketSession wsSession, SessionState state, String message) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("type", "error");
        payload.put("message", message);
        sendPayload(wsSession, state, payload);
    }

    private void sendPayload(WebSocketSession wsSession, SessionState state, ObjectNode payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            synchronized (state.sendLock) {
                if (wsSession.isOpen()) {
                    wsSession.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send message to WebSocket session {}", wsSession.getId(), e);
        }
    }
}
