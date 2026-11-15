package com.example.qsale.reschedule.domain;

import com.example.qsale.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "reschedule_votes",
    uniqueConstraints = @UniqueConstraint(columnNames = { "proposal_id", "user_id" })
)
@Getter
@Setter
@NoArgsConstructor
public class RescheduleVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private RescheduleProposal proposal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User user;

    @Column(nullable = false)
    private boolean accepted;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
