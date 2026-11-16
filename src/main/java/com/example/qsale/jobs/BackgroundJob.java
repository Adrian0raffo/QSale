package com.example.qsale.jobs;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name="background_jobs")
@Getter @Setter @NoArgsConstructor
public class BackgroundJob {
    @Id @Column(length=36) private String id;
    @Column(nullable=false,length=64) private String scope;
    @Column(nullable=false,length=32) private String type;
    @Column(nullable=false,columnDefinition="text") private String payload;
    @Column(nullable=false,length=16) private String status="PENDING";
    @Column(nullable=false) private int attempts;
    @Column(nullable=false) private Instant availableAt=Instant.now();
    @Column(nullable=false) private Instant createdAt=Instant.now();
    private Instant leaseUntil;
    private Instant completedAt;
    @Column(length=120) private String lastError;
}
