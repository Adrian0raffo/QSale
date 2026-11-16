package com.example.qsale.option.events;

import com.example.qsale.option.domain.PlanOptionService;
import com.example.qsale.jobs.BackgroundQueue;
import com.example.qsale.jobs.JobPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PlaceOptionListener {

    private final PlanOptionService planOptionService;
    private final BackgroundQueue queue;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void calculateTravelEstimate(PlaceOptionCreatedEvent event) {
        queue.enqueue("TRAVEL_OPTION",JobPayload.target(event.getOptionId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void refreshPlanTravelEstimates(TravelEstimateRefreshRequestedEvent event) {
        queue.enqueue("TRAVEL_PLAN",JobPayload.target(event.getPlanId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void markTravelEstimatesPending(TravelEstimateRefreshRequestedEvent event) {
        planOptionService.markTravelEstimatesPending(event.getPlanId());
    }
}
