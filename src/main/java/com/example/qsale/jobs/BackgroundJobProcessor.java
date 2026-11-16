package com.example.qsale.jobs;

import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BackgroundJobProcessor {
    private final BackgroundQueue queue;
    private final BackgroundJobExecutor executor;

    @Async("taskExecutor")
    public CompletableFuture<Void> processBatch() {
        for (int count = 0; count < 5; count++) {
            String id = queue.claim();
            if (id == null) break;
            try {
                executor.execute(id);
            } catch (RuntimeException error) {
                queue.failed(id, error);
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
