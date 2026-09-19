package com.compiler.dataservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded worker pool for running compile/execute jobs. Once the queue fills
 * up, submissions are rejected immediately (AbortPolicy) instead of piling up
 * unbounded, so the service can return "please wait" rather than falling over.
 */
@Configuration
public class ExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor compilationExecutor(AppProperties props) {
        AppProperties.Queue q = props.getQueue();
        return new ThreadPoolExecutor(
                q.getCorePoolSize(),
                q.getMaxPoolSize(),
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(q.getCapacity()),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
