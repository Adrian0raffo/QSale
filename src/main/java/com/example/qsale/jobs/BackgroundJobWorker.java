package com.example.qsale.jobs;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @EnableScheduling
@ConditionalOnProperty(name="jobs.enabled",havingValue="true",matchIfMissing=true)
public class BackgroundJobWorker {
    private final BackgroundQueue queue;
    private final BackgroundJobExecutor executor;

    @Async
    @Scheduled(fixedDelayString="${jobs.poll-delay:500}")
    public void processPending() {
        for(int count=0;count<5;count++) {
            String id=queue.claim();
            if(id==null)return;
            try { executor.execute(id); }
            catch(RuntimeException error) { queue.failed(id,error); }
        }
    }

    @Scheduled(fixedDelay=86400000)
    public void cleanCompletedJobs() { queue.removeCompleted(); }
}
