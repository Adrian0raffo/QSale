package com.example.qsale.reschedule.infrastructure;

import com.example.qsale.reschedule.domain.RescheduleVote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RescheduleVoteRepository extends JpaRepository<RescheduleVote, Long> {
    List<RescheduleVote> findByProposalId(Long proposalId);
    Optional<RescheduleVote> findByProposalIdAndUserId(Long proposalId, Long userId);
}
