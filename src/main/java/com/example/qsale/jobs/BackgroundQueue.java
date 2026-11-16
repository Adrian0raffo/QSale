package com.example.qsale.jobs;

import com.example.qsale.exceptions.ResourceNotFoundException;
import com.example.qsale.notification.domain.NotificationStatus;
import com.example.qsale.notification.infrastructure.NotificationRepository;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
public class BackgroundQueue {
    private final BackgroundJobRepository jobs;
    private final NotificationRepository notifications;
    private final ObjectMapper json;
    @Value("${jobs.scope:default}") private String scope;

    @Transactional(propagation=Propagation.MANDATORY)
    public String enqueue(String type,JobPayload payload) {
        BackgroundJob job=new BackgroundJob();
        job.setId(UUID.randomUUID().toString());job.setScope(scope);
        job.setType(type);job.setPayload(json.writeValueAsString(payload));
        jobs.save(job);return job.getId();
    }

    @Transactional
    public String claim() {
        return jobs.claimNext(scope).map(job -> {
            job.setStatus("RUNNING");job.setAttempts(job.getAttempts()+1);
            job.setLeaseUntil(Instant.now().plusSeconds(900));return job.getId();
        }).orElse(null);
    }

    @Transactional
    public void failed(String id,RuntimeException error) {
        jobs.findForUpdate(id).filter(job -> !job.getStatus().equals("DONE")).ifPresent(job -> {
            job.setStatus(job.getAttempts()>=8 ? "FAILED" : "PENDING");
            job.setAvailableAt(Instant.now().plusSeconds(Math.min(3600,5L << Math.min(job.getAttempts(),9))));
            job.setLeaseUntil(null);job.setLastError(error.getClass().getSimpleName());
            JobPayload payload=json.readValue(job.getPayload(),JobPayload.class);
            if(payload.notificationId()!=null) notifications.findById(payload.notificationId())
                    .ifPresent(notification -> notification.setStatus(NotificationStatus.FAILED));
        });
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional(readOnly=true)
    public List<Summary> failedJobs() {
        return jobs.findTop100ByScopeAndStatusOrderByCreatedAtAsc(scope,"FAILED").stream()
                .map(job -> new Summary(job.getId(),job.getType(),job.getAttempts(),job.getLastError())).toList();
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Transactional
    public void retry(String id) {
        BackgroundJob job=jobs.findForUpdate(id).filter(row -> row.getScope().equals(scope))
                .orElseThrow(() -> new ResourceNotFoundException("Background job not found"));
        if(!job.getStatus().equals("FAILED")) return;
        job.setStatus("PENDING");job.setAttempts(0);job.setAvailableAt(Instant.now());job.setLastError(null);
    }

    @Transactional
    public void removeCompleted() { jobs.deleteByStatusAndCompletedAtBefore("DONE",Instant.now().minusSeconds(604800)); }
    public record Summary(String id,String type,int attempts,String error) {}
}
