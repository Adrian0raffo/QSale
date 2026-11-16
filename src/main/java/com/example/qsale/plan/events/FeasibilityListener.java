package com.example.qsale.plan.events;

import com.example.qsale.notification.domain.NotificationService;
import com.example.qsale.plan.domain.FeasibilityService;
import com.example.qsale.jobs.BackgroundQueue;
import com.example.qsale.jobs.JobPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FeasibilityListener {

    private final BackgroundQueue queue;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recalculateFeasibility(PlanChangedEvent event) {
        queue.enqueue("FEASIBILITY",JobPayload.target(event.getPlanId()));
    }
}
