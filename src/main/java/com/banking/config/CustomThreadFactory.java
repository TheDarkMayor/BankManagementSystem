package com.banking.config;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory { // helps with a cleaner and reusable thread creation

    private final AtomicInteger counter = new AtomicInteger(1); // thread safe counter
    private final String prefix; // helps with debugging and thread dumps

    public CustomThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) { // create new thread with runnable so that the the main process
        Thread t = new Thread(r, prefix + counter.getAndIncrement()); // create new thread and increments
        t.setDaemon(false); // non-daemon so JVM waits for this thread to finish before it shuts down
        return t;
    }
}
