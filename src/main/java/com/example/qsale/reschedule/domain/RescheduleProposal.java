package com.example.qsale.reschedule.domain;

import com.example.qsale.plan.domain.Plan;
import com.example.qsale.user.domain.User;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reschedule_proposals")
@Getter
@Setter
@NoArgsConstructor
public class RescheduleProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private int baseAgreementVersion;

    @Column(nullable = false)
    private LocalDate proposedDate;

    private LocalTime proposedTime;
    private LocalDate proposedEndDate;
    private LocalTime proposedEndTime;
    private Long placeOptionId;

    @Column(nullable = false, length = 120)
    private String placeLabel;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 255)
    private String address;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RescheduleStatus status = RescheduleStatus.VOTING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;
}
