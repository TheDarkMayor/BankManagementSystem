package com.banking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// for managing thread lifecycle, naming, queuing and backpressure

@Configuration // reads from application.yml
public class ThreadPoolConfig {

    @Bean(name = "transactionExecutor")
    public ExecutorService transactionExecutor(
            @Value("${banking.thread-pool.core-size:10}") int coreSize, // min threads kept alive
            @Value("${banking.thread-pool.max-size:10}") int maxSize, // max threads allowed
            @Value("${banking.thread-pool.queue-capacity:5000}") int queueCapacity) { // max queue capacity

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new CustomThreadFactory("txn-worker-"), // give thread name
                new ThreadPoolExecutor.CallerRunsPolicy() // backpressure: caller runs when queue full
        );

        executor.allowCoreThreadTimeOut(true); // terminate threads after timeout
        return executor;
    }
}
