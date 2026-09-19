package com.compiler.dataservice.service;

import com.compiler.dataservice.api.dto.CompileRequest;
import com.compiler.dataservice.api.dto.CompileResponse;
import com.compiler.dataservice.config.AppProperties;
import com.compiler.dataservice.domain.Language;
import com.compiler.dataservice.exception.ExecutionTimedOutException;
import com.compiler.dataservice.exception.QueueFullException;
import com.compiler.dataservice.exec.DockerCodeRunner;
import com.compiler.dataservice.exec.ExecutionResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class CompilationService {

    private final ThreadPoolExecutor compilationExecutor;
    private final DockerCodeRunner runner;
    private final AppProperties props;

    public CompilationService(ThreadPoolExecutor compilationExecutor, DockerCodeRunner runner, AppProperties props) {
        this.compilationExecutor = compilationExecutor;
        this.runner = runner;
        this.props = props;
    }

    public CompileResponse compile(CompileRequest request) {
        Language language = Language.fromCode(request.language());

        Future<ExecutionResult> future;
        try {
            future = compilationExecutor.submit(() -> runner.run(language, request.code(), request.stdin()));
        } catch (RejectedExecutionException e) {
            throw new QueueFullException("Server is busy processing other requests, please try again shortly");
        }

        try {
            ExecutionResult result = future.get(props.getExecution().getTotalTimeoutSeconds(), TimeUnit.SECONDS);
            return new CompileResponse(result.status(), result.stdout(), result.stderr(), result.exitCode(), result.executionTimeMs());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ExecutionTimedOutException("Execution did not complete in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionTimedOutException("Execution was interrupted");
        } catch (ExecutionException e) {
            throw new RuntimeException("Execution failed", e.getCause());
        }
    }
}
