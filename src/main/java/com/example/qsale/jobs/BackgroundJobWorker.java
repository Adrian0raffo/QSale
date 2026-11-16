package com.example.qsale.jobs;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @EnableScheduling
@ConditionalOnProperty(name="jobs.enabled",havingValue="true",matchIfMissing=true)
public class BackgroundJobWorker {
    private final BackgroundQueue queue;
    private final BackgroundJobProcessor processor;

    @Scheduled(fixedDelayString="${jobs.poll-delay:500}")
    public void processPending() {
        processor.processBatch().join();
    }

    @Scheduled(fixedDelay=86400000)
    public void cleanCompletedJobs() { queue.removeCompleted(); }
}
