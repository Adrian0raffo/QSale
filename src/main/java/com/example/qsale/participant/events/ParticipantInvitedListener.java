package com.example.qsale.participant.events;

import com.example.qsale.notification.domain.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ParticipantInvitedListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void notifyInvitation(ParticipantInvitedEvent event) {
        notificationService.notifyInvitation(event.getPlanId(), event.getUserId());
    }
}
